(async function () {
  const msg = $('#catalogMsg');

  async function loadLanguages() {
    const l = await api('GET', '/api/languages');
    $('#langList').textContent = l.map(x => x.name).join(', ');
  }

  async function loadProfessions() {
    const ps = await api('GET', '/api/professions');
    $('#profBody').innerHTML = ps.map(p =>
      '<tr data-id="' + p.id + '"><td><input class="n" value="' + esc(p.name) + '"></td>' +
      '<td><input class="lo" type="number" min="0" step="0.01" value="' + p.minPrice + '"></td>' +
      '<td><input class="hi" type="number" min="0" step="0.01" value="' + p.maxPrice + '"></td>' +
      '<td><input class="k" value="' + esc(p.keywords || '') + '"></td>' +
      '<td><button type="button" class="secondary">Save</button></td></tr>').join('');
    $('#profBody').querySelectorAll('tr').forEach(tr => tr.querySelector('button').onclick = guarded(msg, async () => {
      await api('PUT', '/api/admin/professions/' + tr.dataset.id, {
        name: tr.querySelector('.n').value,
        minPrice: Number(tr.querySelector('.lo').value),
        maxPrice: Number(tr.querySelector('.hi').value),
        keywords: tr.querySelector('.k').value
      });
      flash(msg, 'Profession saved', true);
    }));
  }

  $('#langForm').onsubmit = guarded(msg, async () => {
    await api('POST', '/api/admin/languages', { name: $('#langName').value });
    $('#langName').value = '';
    flash(msg, 'Language added', true);
    await loadLanguages();
  });

  $('#profForm').onsubmit = guarded(msg, async () => {
    await api('POST', '/api/admin/professions', {
      name: $('#pName').value,
      minPrice: Number($('#pMin').value),
      maxPrice: Number($('#pMax').value),
      keywords: $('#pKeys').value
    });
    $('#profForm').reset();
    flash(msg, 'Profession added', true);
    await loadProfessions();
  });

  // ----- verification documents -----
  for (const cell of document.querySelectorAll('td.docs')) {
    try {
      const docs = await api('GET', '/api/admin/users/' + cell.dataset.user + '/documents');
      cell.innerHTML = docs.length ? docs.map(d => '<a href="/api/documents/' + d.id + '/file" target="_blank">' + esc(d.label) + '</a>').join('<br>')
        : '<span class="muted">None uploaded</span>';
    } catch (e) { cell.textContent = e.message; }
  }

  // ----- payouts -----
  const pmsg = $('#payoutMsg');
  async function loadPayouts() {
    const pending = await api('GET', '/api/admin/payouts/pending');
    $('#payoutPending').innerHTML = pending.length ? pending.map(p =>
      '<tr data-b="' + p.bookingId + '"><td><a href="/bookings/' + p.bookingId + '">#' + p.bookingId + '</a></td><td>' + esc(p.technicianName) +
      '</td><td>' + money(p.amount) + '</td><td><input class="ref" placeholder="Transfer reference" maxlength="200"></td>' +
      '<td><button type="button">Record payout</button></td></tr>').join('')
      : '<tr><td colspan="5" class="muted">Nothing to pay out.</td></tr>';
    $('#payoutPending').querySelectorAll('tr[data-b]').forEach(tr => tr.querySelector('button').onclick = guarded(pmsg, async () => {
      await api('POST', '/api/admin/bookings/' + tr.dataset.b + '/payout', { reference: tr.querySelector('.ref').value });
      flash(pmsg, 'Payout recorded', true);
      await loadPayouts();
    }));
    const done = await api('GET', '/api/admin/payouts');
    $('#payoutDone').innerHTML = done.length ? done.map(p =>
      '<tr><td><a href="/bookings/' + p.bookingId + '">#' + p.bookingId + '</a></td><td>' + esc(p.technicianName) + '</td><td>' + money(p.amount) +
      '</td><td>' + esc(p.reference) + '</td><td>' + when(p.paidAt) + '</td></tr>').join('')
      : '<tr><td colspan="5" class="muted">None yet.</td></tr>';
  }
  await loadPayouts();

  await loadLanguages();
  await loadProfessions();
})().catch(e => flash($('#catalogMsg'), e.message, false));
