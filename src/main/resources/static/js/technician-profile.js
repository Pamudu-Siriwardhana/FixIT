(async function () {
  const t = await api('GET', '/api/technicians/' + document.body.dataset.id);
  const stars = n => '<span class="stars">' + '&#9733;'.repeat(n) + '&#9734;'.repeat(5 - n) + '</span>';
  $('#name').textContent = t.fullName;
  $('#rating').innerHTML = t.ratingCount
    ? stars(Math.round(t.ratingAverage)) + ' <strong>' + t.ratingAverage + '</strong> from ' + t.ratingCount + ' rating' + (t.ratingCount === 1 ? '' : 's')
    : '<span class="muted">No ratings yet</span>';
  $('#about').innerHTML = esc(t.gender) + ' &middot; speaks ' + (t.languages.map(l => esc(l.name)).join(', ') || '-') +
    (t.experienceYears != null ? ' &middot; ' + t.experienceYears + ' years of experience' : '') +
    (t.qualification ? '<br>' + esc(t.qualification) : '');
  $('#skills').innerHTML = t.skills.length ? t.skills.map(s =>
    '<tr><td>' + esc(s.profession) + '</td><td>' + esc(s.skillLevel) + '</td><td>' + esc(s.notes) + '</td><td>' +
    money(s.minPrice) + ' - ' + money(s.maxPrice) + '</td></tr>').join('')
    : '<tr><td colspan="4" class="muted">No professions listed.</td></tr>';
  $('#reviews').innerHTML = t.reviews.length ? t.reviews.map(r =>
    '<div class="review">' + stars(r.score) + ' <strong>' + esc(r.customerName) + '</strong> <span class="muted">' + when(r.createdAt) + '</span>' +
    (r.comment ? '<br>' + esc(r.comment) : '') + '</div>').join('')
    : '<p class="muted">No reviews yet.</p>';
  $('#content').hidden = false;
})().catch(e => flash($('#msg'), e.message, false));
