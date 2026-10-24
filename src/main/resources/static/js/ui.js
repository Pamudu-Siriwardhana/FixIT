// Small UX helpers loaded with the navigation bar on every signed-in page.
document.addEventListener('DOMContentLoaded', function () {
  // highlight the current page in the navigation
  var path = location.pathname.replace(/\/$/, '') || '/';
  var best = null;
  document.querySelectorAll('.topnav a[href]').forEach(function (a) {
    var href = a.getAttribute('href').replace(/\/$/, '');
    if (a.classList.contains('brand')) return;
    if (path === href || (href.length > 1 && path.indexOf(href + '/') === 0)) {
      if (!best || href.length > best.getAttribute('href').length) best = a;
    }
  });
  if (best) { best.classList.add('active'); best.setAttribute('aria-current', 'page'); }
  // footer with the wordmark
  var foot = document.createElement('footer');
  foot.className = 'sitefoot';
  foot.innerHTML = '<img src="/img/logo-wordmark.png" alt="FixIt"><span>Trusted technicians, on demand</span>';
  document.body.appendChild(foot);
  // screen readers announce messages as they appear
  document.querySelectorAll('.msg').forEach(function (m) { m.setAttribute('role', 'status'); m.setAttribute('aria-live', 'polite'); });
});
