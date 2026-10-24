// Small helpers shared by every page.
const $ = (sel, root) => (root || document).querySelector(sel);

/** Calls the REST API with the signed-in session. Throws Error(message) on failure. */
async function api(method, url, body, isForm) {
  const opts = { method, headers: {}, credentials: 'same-origin' };
  if (body !== undefined && !isForm) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  } else if (isForm) {
    opts.body = body;
  }
  const res = await fetch(url, opts);
  if (res.status === 401) {
    location.href = '/login';
    throw new Error('Please log in');
  }
  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch (e) { data = null; }
  }
  if (!res.ok) {
    let m = (data && data.error) || ('Request failed (' + res.status + ')');
    if (data && data.details) m += ': ' + Object.values(data.details).join(', ');
    throw new Error(m);
  }
  return data;
}

function esc(v) {
  return String(v == null ? '' : v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function money(n) {
  return n == null ? '-' : Number(n).toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function when(iso) {
  if (!iso) return '-';
  const d = new Date(iso);
  return isNaN(d) ? iso : d.toLocaleString('en-LK', { dateStyle: 'medium', timeStyle: 'short' });
}

function badge(status) {
  return '<span class="badge ' + esc(status) + '">' + esc(String(status).replace('_', ' ')) + '</span>';
}

/** Shows a message in an element (green by default, red when ok === false). */
function flash(el, text, ok) {
  if (!el) return;
  el.textContent = text || '';
  el.classList.toggle('bad', ok === false);
}

/** Wraps a click/submit handler so errors land in the message box. */
function guarded(msgEl, fn) {
  return async function (ev) {
    if (ev && ev.preventDefault) ev.preventDefault();
    try { await fn(ev); } catch (e) { flash(msgEl, e.message, false); window.scrollTo({ top: 0, behavior: 'smooth' }); }
  };
}

function checkboxes(container, items, selectedIds, name) {
  container.innerHTML = items.map(i =>
    '<label><input type="checkbox" name="' + name + '" value="' + i.id + '"' +
    (selectedIds.includes(i.id) ? ' checked' : '') + '> ' + esc(i.name) + '</label>').join('');
}

function checkedIds(container) {
  return Array.from(container.querySelectorAll('input[type=checkbox]:checked')).map(c => Number(c.value));
}

function numOrNull(v) {
  return v === '' || v == null ? null : Number(v);
}
