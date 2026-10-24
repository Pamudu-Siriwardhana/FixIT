(async function () {
  const d = await api('GET', '/api/technician/dashboard');
  const stat = (label, value) => '<div class="stat"><span class="muted">' + label + '</span><b>' + value + '</b></div>';
  $('#stats').innerHTML =
    stat('Earned (paid jobs)', 'LKR ' + money(d.totalEarned)) +
    stat('Waiting for payment', 'LKR ' + money(d.pendingPayout)) +
    stat('Service charge paid', 'LKR ' + money(d.serviceChargePaid)) +
    stat('Rating', d.ratingCount ? '&#9733; ' + d.ratingAverage + ' (' + d.ratingCount + ')' : 'No ratings yet');

  $('#statusBody').innerHTML = d.bookingsByStatus.map(s =>
    '<tr><td>' + badge(s.status) + '</td><td>' + s.count + '</td></tr>').join('');

  $('#customers').innerHTML = d.customers.length
    ? '<table>' + d.customers.map(c => '<tr><td>' + esc(c.fullName) + '</td><td>' + esc(c.contactNumber) + '</td></tr>').join('') + '</table>'
    : 'None yet. Customers appear once a price is agreed.';

  const payouts = await api('GET', '/api/technician/payouts');
  $('#payoutBody').innerHTML = payouts.length ? payouts.map(p =>
    '<tr><td><a href="/bookings/' + p.bookingId + '">#' + p.bookingId + '</a></td><td>' + money(p.amount) + '</td><td>' + esc(p.reference) +
    (p.note ? ' <span class="muted">(' + esc(p.note) + ')</span>' : '') + '</td><td>' + when(p.paidAt) + '</td></tr>').join('')
    : '<tr><td colspan="4" class="muted">No payouts yet.</td></tr>';

  if (!d.upcomingJobs.length) { $('#noUpcoming').hidden = false; return; }
  $('#upcoming').innerHTML = d.upcomingJobs.map(b =>
    '<tr><td>' + b.id + '</td><td>' + esc(b.customer.fullName) + '</td><td>' + esc(b.profession.name) + '</td><td>' + when(b.scheduledAt) +
    '</td><td>' + money(b.agreedPrice) + '</td><td>' + badge(b.status) + '</td><td><a href="/bookings/' + b.id + '">Open</a></td></tr>').join('');
})().catch(e => flash($('#msg'), e.message, false));
