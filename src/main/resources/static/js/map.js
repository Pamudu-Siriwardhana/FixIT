// Leaflet helpers (OpenStreetMap tiles). Default centre: Colombo.
const COLOMBO = [6.9271, 79.8612];

function createMap(elId, center, zoom) {
  const map = L.map(elId).setView(center || COLOMBO, zoom || 13);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  }).addTo(map);
  return map;
}

/**
 * A map with one draggable pin. Click the map or drag the pin to move it.
 * onMove(latlng) is called whenever the pin changes.
 */
function pinPicker(elId, start, onMove) {
  const map = createMap(elId, start, start ? 15 : 12);
  let marker = null;

  function place(latlng, pan) {
    latlng = L.latLng(latlng);
    if (!marker) {
      marker = L.marker(latlng, { draggable: true }).addTo(map);
      marker.on('dragend', () => onMove && onMove(marker.getLatLng()));
    } else {
      marker.setLatLng(latlng);
    }
    if (pan) map.setView(latlng, Math.max(map.getZoom(), 15));
    if (onMove) onMove(latlng);
  }

  map.on('click', e => place(e.latlng, false));
  if (start) place(start, false);

  return {
    map,
    set: (latlng, pan) => place(latlng, pan !== false),
    get: () => (marker ? marker.getLatLng() : null),
    /** Uses the device GPS; calls done(error) afterwards. */
    locate(done) {
      if (!navigator.geolocation) { done && done(new Error('This browser cannot give your location')); return; }
      navigator.geolocation.getCurrentPosition(
        pos => { place([pos.coords.latitude, pos.coords.longitude], true); done && done(); },
        () => done && done(new Error('Could not get your location. Allow location access or click the map.')),
        { timeout: 8000 });
    }
  };
}

/**
 * Adds an address search box above a map. onPick(lat, lng, label) is called when the user chooses a result.
 * Uses GET /api/geocode; if search is not configured the message says so and the user can still click the map.
 */
function addAddressSearch(mapElId, onPick) {
  const mapEl = document.getElementById(mapElId);
  const box = document.createElement('div');
  box.className = 'addr-search';
  box.innerHTML = '<div class="row"><input type="text" placeholder="Search an address or place" maxlength="150">' +
    '<div><button type="button" class="secondary">Search</button></div></div><ul class="addr-results"></ul>';
  mapEl.parentNode.insertBefore(box, mapEl);
  const input = box.querySelector('input');
  const list = box.querySelector('ul');

  async function run() {
    list.innerHTML = '<li class="muted">Searching...</li>';
    try {
      const places = await api('GET', '/api/geocode?q=' + encodeURIComponent(input.value));
      if (!places.length) { list.innerHTML = '<li class="muted">Nothing found. Try another spelling or click the map.</li>'; return; }
      list.innerHTML = places.map((p, i) => '<li><button type="button" class="link" data-i="' + i + '">' + esc(p.label) + '</button></li>').join('');
      list.querySelectorAll('button').forEach(b => b.onclick = () => {
        const p = places[Number(b.dataset.i)];
        onPick(p.latitude, p.longitude, p.label);
        list.innerHTML = '';
      });
    } catch (e) {
      list.innerHTML = '<li class="muted">' + esc(e.message) + '</li>';
    }
  }
  box.querySelector('button').onclick = run;
  input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); run(); } });
}
