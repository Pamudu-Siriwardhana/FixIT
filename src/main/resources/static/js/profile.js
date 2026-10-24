(async function () {
  const role = document.body.dataset.role;
  const msg = $('#msg');
  const levelsNote = { PENDING: 'An admin must verify your profile.', REJECTED: '', VERIFIED: '' };
  let me = await api('GET', '/api/me');
  const languages = await api('GET', '/api/languages');

  function renderStatus() {
    const s = $('#status');
    s.textContent = me.status;
    s.className = 'badge ' + me.status;
    $('#statusNote').textContent = me.status === 'REJECTED'
      ? 'Rejected: ' + (me.rejectionReason || '') + '. Update your profile to resubmit.'
      : levelsNote[me.status] || '';
  }
  function setMe(updated) { me = updated; renderStatus(); }
  renderStatus();

  // ----- basic details -----
  $('#idText').textContent = 'Email: ' + me.email + ' | NIC: ' + me.nic;
  $('#dName').value = me.fullName;
  $('#dContact').value = me.contactNumber;
  $('#dGender').value = me.gender;
  $('#detailsForm').onsubmit = guarded(msg, async () => {
    setMe(await api('PUT', '/api/me', { fullName: $('#dName').value, contactNumber: $('#dContact').value, gender: $('#dGender').value }));
    flash(msg, 'Details saved', true);
  });

  // ----- map location -----
  $('#address').value = me.address || '';
  const showLoc = ll => { $('#locText').textContent = ll ? ll.lat.toFixed(5) + ', ' + ll.lng.toFixed(5) : 'No pin yet'; };
  const picker = pinPicker('map', me.latitude != null ? [me.latitude, me.longitude] : null, showLoc);
  if (me.latitude == null) showLoc(null);
  addAddressSearch('map', (lat, lng, label) => { picker.set([lat, lng], true); $('#address').value = label; });
  $('#useMyLocation').onclick = () => picker.locate(err => err && flash(msg, err.message, false));
  $('#saveLocation').onclick = guarded(msg, async () => {
    const p = picker.get();
    if (!p) throw new Error('Click the map to place your pin first');
    setMe(await api('PUT', '/api/me/location', { latitude: p.lat, longitude: p.lng, address: $('#address').value }));
    flash(msg, 'Location saved', true);
  });

  // ----- verification documents -----
  async function loadDocs() {
    const docs = await api('GET', '/api/me/documents');
    $('#docBody').innerHTML = docs.length ? docs.map(d =>
      '<tr><td>' + esc(d.label) + '</td><td><a href="/api/documents/' + d.id + '/file" target="_blank">' + esc(d.fileName) + '</a></td>' +
      '<td class="muted">' + Math.max(1, Math.round(d.size / 1024)) + ' KB &middot; ' + when(d.uploadedAt) + '</td>' +
      '<td><button type="button" class="danger" data-id="' + d.id + '">Delete</button></td></tr>').join('')
      : '<tr><td class="muted">No documents yet.</td></tr>';
    $('#docBody').querySelectorAll('button').forEach(b => b.onclick = guarded(msg, async () => {
      await api('DELETE', '/api/me/documents/' + b.dataset.id);
      await loadDocs();
    }));
  }
  $('#docForm').onsubmit = guarded(msg, async () => {
    const f = new FormData();
    f.append('file', $('#docFile').files[0]);
    f.append('label', $('#docLabel').value);
    await api('POST', '/api/me/documents', f, true);
    $('#docForm').reset();
    me = await api('GET', '/api/me');
    renderStatus();
    await loadDocs();
    flash(msg, 'Document uploaded', true);
  });
  await loadDocs();

  // ----- languages -----
  checkboxes($('#langBox'), languages, me.languages.map(l => l.id), 'lang');
  $('#saveLangs').onclick = guarded(msg, async () => {
    setMe(await api('PUT', '/api/me/languages', { languageIds: checkedIds($('#langBox')) }));
    flash(msg, 'Languages saved', true);
  });

  // ----- customer preferences -----
  if (role === 'CUSTOMER') {
    const p = await api('GET', '/api/customer/preferences');
    $('#prefGender').value = p.preferredGender || '';
    $('#prefDistance').value = p.maxDistanceKm == null ? '' : p.maxDistanceKm;
    $('#prefRating').value = p.minRating == null ? '' : p.minRating;
    $('#prefMin').value = p.budgetMin == null ? '' : p.budgetMin;
    $('#prefMax').value = p.budgetMax == null ? '' : p.budgetMax;
    checkboxes($('#prefLangBox'), languages, (p.preferredLanguages || []).map(l => l.id), 'prefLang');
    $('#prefForm').onsubmit = guarded(msg, async () => {
      await api('PUT', '/api/customer/preferences', {
        preferredGender: $('#prefGender').value || null,
        maxDistanceKm: numOrNull($('#prefDistance').value),
        minRating: numOrNull($('#prefRating').value),
        budgetMin: numOrNull($('#prefMin').value),
        budgetMax: numOrNull($('#prefMax').value),
        preferredLanguageIds: checkedIds($('#prefLangBox'))
      });
      flash(msg, 'Preferences saved', true);
    });
  }

  // ----- technician profile and professions -----
  if (role === 'TECHNICIAN') {
    $('#qualification').value = me.qualification || '';
    $('#experience').value = me.experienceYears == null ? '' : me.experienceYears;
    $('#techForm').onsubmit = guarded(msg, async () => {
      setMe(await api('PUT', '/api/technician/profile', {
        qualification: $('#qualification').value,
        experienceYears: numOrNull($('#experience').value)
      }));
      flash(msg, 'Saved', true);
    });

    const professions = await api('GET', '/api/professions');
    $('#skillProf').innerHTML = professions.map(p => '<option value="' + p.id + '">' + esc(p.name) + '</option>').join('');

    const renderSkills = () => {
      const rows = (me.skills || []).map(s =>
        '<tr><td>' + esc(s.profession) + '</td><td>' + esc(s.skillLevel) + '</td><td>' + esc(s.notes) + '</td>' +
        '<td>' + money(s.minPrice) + ' - ' + money(s.maxPrice) + '</td>' +
        '<td><button type="button" class="danger" data-id="' + s.id + '">Remove</button></td></tr>');
      $('#skillBody').innerHTML = rows.length ? rows.join('') : '<tr><td colspan="5" class="muted">No professions yet. Add at least one to be listed.</td></tr>';
      $('#skillBody').querySelectorAll('button').forEach(b => b.onclick = guarded(msg, async () => {
        setMe(await api('DELETE', '/api/technician/skills/' + b.dataset.id));
        renderSkills();
      }));
    };
    renderSkills();
    $('#skillForm').onsubmit = guarded(msg, async () => {
      setMe(await api('POST', '/api/technician/skills', {
        professionId: Number($('#skillProf').value),
        skillLevel: $('#skillLevel').value,
        notes: $('#skillNotes').value
      }));
      $('#skillNotes').value = '';
      renderSkills();
      flash(msg, 'Profession saved', true);
    });
  }
})().catch(e => flash($('#msg'), e.message, false));
