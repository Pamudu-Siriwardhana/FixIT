(async function () {
  const msg = $('#msg');
  const role = document.body.dataset.role;
  const list = await api('GET', '/api/bookings');
  $('#whoHead').textContent = role === 'CUSTOMER' ? 'Technician' : 'Customer';
  if (!list.length) { $('#empty').hidden = false; return; }
  $('#body').innerHTML = list.map(b => {
    const other = role === 'CUSTOMER' ? b.technician : b.customer;
    return '<tr><td>' + b.id + '</td><td>' + esc(other.fullName) + '</td><td>' + esc(b.profession.name) + '</td><td>' + when(b.scheduledAt) +
      '</td><td>' + money(b.agreedPrice != null ? b.agreedPrice : b.currentOffer) + '</td><td>' + badge(b.status) +
      '</td><td><a href="/bookings/' + b.id + '">Open</a></td></tr>';
  }).join('');
})().catch(e => flash($('#msg'), e.message, false));
