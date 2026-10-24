(async function () {
  const msg = $('#msg');
  const me = await api('GET', '/api/me');
  const professions = await api('GET', '/api/professions');
  let found = [];
  let origin = me.latitude != null ? L.latLng(me.latitude, me.longitude) : null;

  if (me.status !== 'VERIFIED') {
    flash(msg, 'Your profile is ' + me.status + '. An admin must verify it before you can search or book.', false);
  }
  if (!origin) {
    flash(msg, 'Pin your location on your Profile first, or click the map to choose where to search.', false);
  }

  $('#fProfession').innerHTML += professions.map(p => '<option value="' + p.id + '">' + esc(p.name) + '</option>').join('');

  // ----- search map -----
  const map = createMap('map', origin ? [origin.lat, origin.lng] : null, origin ? 13 : 11);
  const layer = L.layerGroup().addTo(map);
  let originMarker = null;
  function setOrigin(ll) {
    origin = L.latLng(ll);
    if (originMarker) originMarker.setLatLng(origin);
    else originMarker = L.circleMarker(origin, { radius: 9, color: '#d11b52', fillColor: '#d11b52', fillOpacity: .5 }).addTo(map).bindTooltip('Search centre');
    $('#originText').textContent = 'Searching around ' + origin.lat.toFixed(4) + ', ' + origin.lng.toFixed(4) + '. Click the map to change it.';
  }
  if (origin) setOrigin(origin);
  map.on('click', e => setOrigin(e.latlng));
  addAddressSearch('map', (lat, lng) => { setOrigin([lat, lng]); map.setView([lat, lng], 14); });

  // ----- results -----
  function render(list) {
    found = list;
    layer.clearLayers();
    $('#count').textContent = '(' + list.length + ')';
    if (!list.length) {
      $('#results').innerHTML = '<div class="empty">No technicians match. Try a bigger radius, another profession, or untick "Apply my saved preferences".</div>';
      return;
    }
    $('#results').innerHTML = list.map((t, i) =>
      '<div class="result"><div class="row"><div><strong><a href="/technicians/' + t.id + '">' + esc(t.fullName) + '</a></strong> &middot; ' + t.distanceKm + ' km away' +
      '<br><span class="muted">' + (t.rated ? '&#9733; ' + t.ratingAverage + ' (' + t.ratingCount + ' ratings)' : 'No ratings yet') +
      ' &middot; ' + esc(t.gender) + ' &middot; speaks ' + (t.languages.map(l => esc(l.name)).join(', ') || '-') + '</span>' +
      '<br>' + t.skills.map(s => esc(s.profession) + ' (' + esc(s.skillLevel) + ', LKR ' + money(s.minPrice) + ' - ' + money(s.maxPrice) + ')').join('; ') +
      (t.qualification ? '<br><span class="muted">' + esc(t.qualification) + (t.experienceYears != null ? ' &middot; ' + t.experienceYears + ' yrs' : '') + '</span>' : '') +
      '</div><div style="flex:0 0 auto"><button type="button" data-i="' + i + '">Book</button></div></div></div>').join('');
    $('#results').querySelectorAll('button').forEach(b => b.onclick = () => openBooking(Number(b.dataset.i)));
    const bounds = [];
    list.forEach((t, i) => {
      const m = L.marker([t.latitude, t.longitude]).addTo(layer).bindPopup('<strong>' + esc(t.fullName) + '</strong><br>' + t.distanceKm + ' km');
      bounds.push([t.latitude, t.longitude]);
    });
    if (origin) bounds.push([origin.lat, origin.lng]);
    map.fitBounds(bounds, { padding: [30, 30], maxZoom: 15 });
  }

  async function search() {
    if (!origin) throw new Error('Click the map to choose where to search');
    const p = new URLSearchParams({ lat: origin.lat, lng: origin.lng, usePreferences: $('#fPrefs').checked });
    if ($('#fRadius').value) p.set('radiusKm', $('#fRadius').value);
    if ($('#fProfession').value) p.set('professionId', $('#fProfession').value);
    flash(msg, '');
    $('#results').innerHTML = '<div class="skeleton"></div><div class="skeleton"></div>';
    try {
      render(await api('GET', '/api/discovery/technicians?' + p));
    } catch (e) { $('#results').innerHTML = ''; throw e; }
  }
  $('#search').onclick = guarded(msg, search);

  $('#suggest').onclick = guarded(msg, async () => {
    const text = $('#problem').value.trim();
    if (!text) throw new Error('Describe the problem first');
    if (!origin) throw new Error('Click the map to choose where to search');
    flash(msg, '');
    const body = { description: text, latitude: origin.lat, longitude: origin.lng };
    if ($('#fRadius').value) body.radiusKm = Number($('#fRadius').value);
    const r = await api('POST', '/api/discovery/match', body);
    if (!r.best) {
      $('#suggestion').textContent = 'We could not tell what kind of technician this needs. Pick a profession below and search.';
      render([]);
      return;
    }
    const others = r.matches.slice(1, 3).map(m => m.profession).join(', ');
    $('#suggestion').innerHTML = 'Best fit: <strong>' + esc(r.best.profession) + '</strong>' + (others ? ' (also possible: ' + esc(others) + ')' : '');
    $('#fProfession').value = r.best.professionId;
    render(r.technicians);
  });

  // ----- booking dialog -----
  const dlg = $('#bookDialog');
  let bPicker = null;
  let current = null;

  function showRange() {
    const s = current.skills.find(x => x.professionId === Number($('#bProf').value));
    if (!s) return;
    $('#bRange').textContent = 'Allowed price for ' + s.profession + ': LKR ' + money(s.minPrice) + ' - ' + money(s.maxPrice);
    $('#bPrice').min = s.minPrice;
    $('#bPrice').max = s.maxPrice;
  }

  function openBooking(i) {
    current = found[i];
    $('#bTech').textContent = current.fullName;
    $('#bProf').innerHTML = current.skills.map(s => '<option value="' + s.professionId + '">' + esc(s.profession) + '</option>').join('');
    const chosen = Number($('#fProfession').value);
    if (chosen && current.skills.some(s => s.professionId === chosen)) $('#bProf').value = chosen;
    $('#bDesc').value = $('#problem').value;
    $('#bPrice').value = '';
    $('#bMsg').textContent = '';
    const soon = new Date(Date.now() + 2 * 3600 * 1000);
    soon.setMinutes(soon.getMinutes() - soon.getTimezoneOffset());
    $('#bWhen').value = soon.toISOString().slice(0, 16);
    showRange();
    dlg.showModal();
    setTimeout(() => {
      const start = origin ? [origin.lat, origin.lng] : null;
      if (!bPicker) {
        bPicker = pinPicker('bMap', start, null);
        addAddressSearch('bMap', (lat, lng, label) => { bPicker.set([lat, lng], true); $('#bAddress').value = label; });
      }
      bPicker.map.invalidateSize();
      if (start && !bPicker.get()) bPicker.set(start, true);
    }, 150);
  }
  $('#bProf').onchange = showRange;
  $('#bCancel').onclick = () => dlg.close();
  $('#bookForm').onsubmit = guarded($('#bMsg'), async () => {
    const pin = bPicker && bPicker.get();
    if (!pin) throw new Error('Click the map to mark where the job is');
    const b = await api('POST', '/api/bookings', {
      technicianId: current.id,
      professionId: Number($('#bProf').value),
      description: $('#bDesc').value,
      jobLatitude: pin.lat,
      jobLongitude: pin.lng,
      jobAddress: $('#bAddress').value,
      scheduledAt: $('#bWhen').value.length === 16 ? $('#bWhen').value + ':00' : $('#bWhen').value,
      proposedPrice: Number($('#bPrice').value)
    });
    location.href = '/bookings/' + b.id;
  });

  if (origin && me.status === 'VERIFIED') await search();
})().catch(e => flash($('#msg'), e.message, false));
