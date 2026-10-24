(async function () {
  const id = document.body.dataset.id;
  const role = document.body.dataset.role;
  const msg = $('#msg');
  const base = '/api/bookings/' + id;
  let b = null;
  let payments = [];

  const map = createMap('map', null, 13);
  const routeLayer = L.layerGroup().addTo(map);
  let jobMarker = null;

  const dur = min => min >= 60 ? Math.floor(min / 60) + ' h ' + Math.round(min % 60) + ' min' : Math.round(min) + ' min';

  // ---------- loading ----------
  async function load() {
    b = await api('GET', base);
    if (['COMPLETED', 'PAID'].includes(b.status)) payments = await api('GET', base + '/payments');
    renderAll();
  }

  function renderAll() {
    const sb = $('#statusBadge');
    sb.textContent = b.status.replace('_', ' ');
    sb.className = 'badge ' + b.status;

    const flow = ['REQUESTED', 'NEGOTIATING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'PAID'];
    const names = { REQUESTED: 'Requested', NEGOTIATING: 'Negotiating', CONFIRMED: 'Confirmed', IN_PROGRESS: 'In progress', COMPLETED: 'Completed', PAID: 'Paid' };
    const at = flow.indexOf(b.status);
    $('#stepper').innerHTML = b.status === 'CANCELLED'
      ? '<li class="stopped">Cancelled</li>'
      : flow.map((s, i) => '<li class="' + (i < at ? 'done' : i === at ? 'now' : '') + '"' + (i === at ? ' aria-current="step"' : '') + '>' + names[s] + '</li>').join('');

    const contact = p => p.contactNumber ? ' &middot; ' + esc(p.contactNumber) : ' <span class="muted">(contact shown once the price is agreed)</span>';
    $('#summary').innerHTML =
      '<table><tr><th>Customer</th><td>' + esc(b.customer.fullName) + contact(b.customer) + '</td></tr>' +
      '<tr><th>Technician</th><td>' + esc(b.technician.fullName) + contact(b.technician) + '</td></tr>' +
      '<tr><th>Profession</th><td>' + esc(b.profession.name) + '</td></tr>' +
      '<tr><th>Problem</th><td>' + esc(b.description) + '</td></tr>' +
      '<tr><th>When</th><td>' + when(b.scheduledAt) + '</td></tr>' +
      (b.agreedPrice != null
        ? '<tr><th>Agreed price</th><td><strong>LKR ' + money(b.agreedPrice) + '</strong></td></tr>' +
          '<tr><th>Service charge</th><td>LKR ' + money(b.serviceCharge) + ' (paid through the platform)</td></tr>' +
          '<tr><th>Technician receives</th><td>LKR ' + money(b.technicianPayout) + '</td></tr>'
        : '<tr><th>Latest offer</th><td>LKR ' + money(b.currentOffer) + ' from the ' + esc(String(b.currentOfferBy).toLowerCase()) + '</td></tr>') +
      '</table>';

    const pos = [b.jobLatitude, b.jobLongitude];
    if (!jobMarker) {
      jobMarker = L.marker(pos).addTo(map).bindPopup('Job location');
      map.setView(pos, 15);
    }
    $('#address').textContent = b.jobAddress || '';

    renderOffers();
    renderNegotiation();
    renderActions();
    $('#routesCard').hidden = !(role === 'TECHNICIAN' && b.status !== 'CANCELLED');
    renderPayments();
    renderRating();
  }

  // ---------- negotiation ----------
  function renderOffers() {
    $('#offers').innerHTML = '<table><thead><tr><th>From</th><th>Offer (LKR)</th><th>When</th></tr></thead><tbody>' +
      b.offers.map(o => '<tr><td>' + esc(o.byName) + ' <span class="muted">(' + esc(o.by.toLowerCase()) + ')</span></td><td>' + money(o.amount) + '</td><td>' + when(o.at) + '</td></tr>').join('') +
      '</tbody></table>';
  }

  function renderNegotiation() {
    const box = $('#negotiation');
    if (!['REQUESTED', 'NEGOTIATING'].includes(b.status) || role === 'ADMIN') {
      box.innerHTML = b.agreedPrice != null ? '<p><strong>Price agreed: LKR ' + money(b.agreedPrice) + '</strong></p>' : '';
      return;
    }
    if (b.currentOfferBy === role) {
      box.innerHTML = '<p class="muted">Waiting for the other side to answer your offer of LKR ' + money(b.currentOffer) + '.</p>';
      return;
    }
    const p = b.profession;
    box.innerHTML =
      '<p>Latest offer: <strong>LKR ' + money(b.currentOffer) + '</strong></p>' +
      '<button type="button" id="acceptBtn">Accept this price</button>' +
      '<form id="counterForm"><label>Or counter with another price (LKR ' + money(p.minPrice) + ' - ' + money(p.maxPrice) + ')' +
      '<input type="number" id="counterAmt" min="' + p.minPrice + '" max="' + p.maxPrice + '" step="0.01" required></label>' +
      '<button type="submit" class="secondary">Send counter offer</button></form>';
    $('#acceptBtn').onclick = guarded(msg, async () => { await api('POST', base + '/accept'); flash(msg, 'Price accepted', true); await load(); });
    $('#counterForm').onsubmit = guarded(msg, async () => {
      await api('POST', base + '/offers', { amount: Number($('#counterAmt').value) });
      flash(msg, 'Counter offer sent', true);
      await load();
    });
  }

  // ---------- job status ----------
  function renderActions() {
    const btns = [];
    if (role === 'TECHNICIAN' && b.status === 'CONFIRMED') btns.push(['start', 'Start job', '']);
    if (role === 'TECHNICIAN' && b.status === 'IN_PROGRESS') btns.push(['complete', 'Mark job completed', '']);
    if (role !== 'ADMIN' && ['REQUESTED', 'NEGOTIATING', 'CONFIRMED'].includes(b.status)) btns.push(['cancel', 'Cancel booking', 'danger']);
    $('#actionsCard').hidden = btns.length === 0;
    $('#actions').innerHTML = btns.map(x => '<button type="button" class="' + x[2] + '" data-a="' + x[0] + '">' + x[1] + '</button>').join(' ');
    $('#actions').querySelectorAll('button').forEach(btn => btn.onclick = guarded(msg, async () => {
      if (btn.dataset.a === 'cancel' && !confirm('Cancel this booking?')) return;
      await api('POST', base + '/' + btn.dataset.a);
      flash(msg, 'Updated', true);
      await load();
    }));
  }

  // ---------- routes (technician) ----------
  const gps = () => new Promise(resolve => {
    if (!navigator.geolocation) return resolve(null);
    navigator.geolocation.getCurrentPosition(p => resolve(p.coords), () => resolve(null), { timeout: 6000 });
  });

  function showRoute(r, i) {
    const x = r.routes[i];
    routeLayer.clearLayers();
    $('#routesOut').querySelectorAll('.tabs button').forEach((t, k) => t.className = k === i ? '' : 'secondary');
    let html = '';
    if (x.error) {
      html = '<p class="error">' + esc(x.error) + '</p>';
    } else {
      html = '<p><strong>' + x.distanceKm + ' km</strong>, about <strong>' + dur(x.durationMinutes) + '</strong></p>' +
        (x.note ? '<p class="muted">' + esc(x.note) + '</p>' : '') +
        '<ol class="steps">' + x.steps.map(s => '<li>' + esc(s.instruction) + ' <span class="muted">(' + Math.round(s.distanceMeters) + ' m)</span></li>').join('') + '</ol>';
      if (x.geometry && x.geometry.length) {
        const line = L.polyline(x.geometry, { color: '#d11b52', weight: 5 }).addTo(routeLayer);
        L.circleMarker([r.fromLatitude, r.fromLongitude], { radius: 8, color: '#131721' }).addTo(routeLayer).bindTooltip('Start');
        map.fitBounds(line.getBounds(), { padding: [20, 20] });
      }
    }
    $('#routeDetail').innerHTML = html;
  }

  $('#routesBtn').onclick = guarded(msg, async () => {
    $('#routesOut').innerHTML = '<p class="muted">Finding routes...</p>';
    const c = await gps();
    let r;
    try {
      r = await api('GET', '/api/technician/bookings/' + id + '/routes' + (c ? '?fromLat=' + c.latitude + '&fromLng=' + c.longitude : ''));
    } catch (e) {
      $('#routesOut').innerHTML = '';
      throw e;
    }
    $('#routesOut').innerHTML =
      '<p class="muted">From ' + (c ? 'your current position' : 'your saved location') + '.</p>' +
      '<div class="tabs">' + r.routes.map((x, i) => '<button type="button" data-i="' + i + '">' + esc(x.label) + '</button>').join('') + '</div>' +
      '<div id="routeDetail"></div><p class="muted">' + esc(r.transitNote) + '</p>';
    $('#routesOut').querySelectorAll('.tabs button').forEach(t => t.onclick = () => showRoute(r, Number(t.dataset.i)));
    showRoute(r, 0);
  });

  // ---------- payments ----------
  function renderPayments() {
    const show = ['COMPLETED', 'PAID'].includes(b.status);
    $('#paymentsCard').hidden = !show;
    if (!show) return;
    $('#paymentInfo').innerHTML = 'Service charge (' + 'paid through the platform): <strong>LKR ' + money(b.serviceCharge) +
      '</strong> &middot; Balance for the technician: <strong>LKR ' + money(b.technicianPayout) + '</strong> (cash, or through the platform).';
    $('#payBody').innerHTML = payments.length ? payments.map(p =>
      '<tr><td>' + esc(p.type.replace('_', ' ')) + '</td><td>' + esc(p.method) + '</td><td>' + money(p.amount) + '</td><td>' + badge(p.status) +
      (p.status === 'REJECTED' && p.note ? '<br><span class="error">' + esc(p.note) + '</span>' : '') + '</td><td>' +
      (p.hasProof ? '<a href="/api/payments/' + p.id + '/proof" target="_blank">' + esc(p.proofFileName) + '</a>' : '-') + '</td><td>' +
      (role === 'TECHNICIAN' && p.method === 'CASH' && p.status === 'PENDING'
        ? '<button type="button" data-id="' + p.id + '">Cash received</button>' : '') + '</td></tr>').join('')
      : '<tr><td colspan="6" class="muted">No payments recorded yet.</td></tr>';
    $('#payBody').querySelectorAll('button').forEach(btn => btn.onclick = guarded(msg, async () => {
      await api('POST', '/api/payments/' + btn.dataset.id + '/confirm');
      flash(msg, 'Cash payment confirmed', true);
      await load();
    }));

    const forms = $('#payForms');
    forms.innerHTML = '';
    if (role !== 'CUSTOMER' || b.status !== 'COMPLETED') return;
    const have = t => payments.some(p => p.type === t && p.status !== 'REJECTED');
    if (!have('SERVICE_CHARGE')) {
      forms.innerHTML += payForm('SERVICE_CHARGE', 'Pay the service charge: LKR ' + money(b.serviceCharge),
        'Pay it through the platform, then upload your payment slip (PNG, JPEG or PDF, up to 5 MB). An admin confirms it.', false);
    }
    if (!have('BALANCE')) {
      forms.innerHTML += payForm('BALANCE', 'Pay the technician: LKR ' + money(b.technicianPayout),
        'Pay in cash (the technician confirms) or through the platform with a payment slip.', true);
    }
    forms.querySelectorAll('form').forEach(f => f.onsubmit = guarded(msg, async () => {
      const fd = new FormData();
      fd.append('type', f.dataset.type);
      fd.append('method', f.method_.value);
      if (f.note.value) fd.append('note', f.note.value);
      if (f.file.files.length) fd.append('file', f.file.files[0]);
      await api('POST', base + '/payments', fd, true);
      flash(msg, 'Payment recorded', true);
      await load();
    }));
  }

  function payForm(type, title, help, choose) {
    return '<form data-type="' + type + '"><h3>' + esc(title) + '</h3><p class="muted">' + esc(help) + '</p>' +
      (choose ? '<label>Method<select name="method_"><option value="CASH">Cash</option><option value="PLATFORM">Through the platform</option></select></label>'
              : '<input type="hidden" name="method_" value="PLATFORM">') +
      '<label>Payment slip' + (choose ? ' (needed for platform payments)' : '') + '<input type="file" name="file" accept="image/png,image/jpeg,application/pdf"></label>' +
      '<label>Note (optional)<input type="text" name="note" maxlength="500"></label>' +
      '<button type="submit">Record payment</button></form>';
  }

  // ---------- rating ----------
  function renderRating() {
    const done = ['COMPLETED', 'PAID'].includes(b.status);
    $('#ratingCard').hidden = !done;
    if (!done) return;
    const box = $('#ratingBody');
    if (b.ratingScore != null) {
      box.innerHTML = '<p>' + (role === 'CUSTOMER' ? 'You rated this technician ' : 'The customer rated you ') + '<strong>' + '&#9733;'.repeat(b.ratingScore) + ' ' + b.ratingScore + '/5</strong>.</p>';
      return;
    }
    if (role !== 'CUSTOMER') { box.innerHTML = '<p class="muted">The customer has not rated this job yet.</p>'; return; }
    box.innerHTML = '<form id="rateForm"><label>Score<select id="score"><option value="5">5 - Excellent</option><option value="4">4 - Good</option>' +
      '<option value="3">3 - OK</option><option value="2">2 - Poor</option><option value="1">1 - Bad</option></select></label>' +
      '<label>Comment (optional)<input type="text" id="comment" maxlength="1000"></label><button type="submit">Submit rating</button></form>';
    $('#rateForm').onsubmit = guarded(msg, async () => {
      await api('POST', base + '/rating', { score: Number($('#score').value), comment: $('#comment').value });
      flash(msg, 'Thank you for rating', true);
      await load();
    });
  }

  await load();
})().catch(e => flash($('#msg'), e.message, false));
