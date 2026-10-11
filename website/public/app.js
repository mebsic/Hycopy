const toast = document.querySelector('.toast');
let toastTimeout;
function notify(message) {
  toast.textContent = message;
  toast.classList.add('visible');
  clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => toast.classList.remove('visible'), 2500);
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text);
    notify('Copied to clipboard');
    return true;
  } catch {
    notify('Clipboard unavailable. Select the text to copy it.');
    return false;
  }
}

document.querySelectorAll('[data-copy]').forEach(button => button.addEventListener('click', () => copy(button.dataset.copy)));
document.querySelectorAll('.prose pre').forEach(pre => {
  const code = pre.querySelector('code');
  if (!code) return;
  const language = document.createElement('span');
  language.className = 'code-language';
  language.textContent = code.className.replace('language-', '') || 'text';
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'copy-code';
  button.textContent = 'Copy';
  button.setAttribute('aria-label', 'Copy code example');
  button.addEventListener('click', async () => {
    if (await copy(code.textContent.trimEnd())) {
      button.textContent = 'Copied';
      setTimeout(() => button.textContent = 'Copy', 2000);
    }
  });
  pre.prepend(language, button);
});
document.querySelectorAll('.prose table').forEach(table => {
  const wrapper = document.createElement('div');
  wrapper.className = 'table-wrap';
  table.replaceWith(wrapper);
  wrapper.append(table);
});

const themeButton = document.querySelector('.theme-toggle');
function updateThemeButton() {
  const dark = document.documentElement.dataset.theme === 'dark';
  themeButton.setAttribute('aria-pressed', String(dark));
  const label = `Switch to ${dark ? 'light' : 'dark'} mode`;
  themeButton.setAttribute('aria-label', label);
  themeButton.title = label;
}
themeButton.addEventListener('click', () => {
  const theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = theme;
  try { localStorage.setItem('hycopy-theme', theme); } catch { /* Storage is optional. */ }
  updateThemeButton();
});
updateThemeButton();

const menuButton = document.querySelector('.menu-toggle');
function setMenu(open) {
  document.body.classList.toggle('nav-open', open);
  menuButton.setAttribute('aria-expanded', String(open));
  menuButton.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
  if (open) document.querySelector('.sidebar a[aria-current], .sidebar a').focus();
}
menuButton.addEventListener('click', () => setMenu(!document.body.classList.contains('nav-open')));
document.querySelector('.nav-overlay').addEventListener('click', () => { setMenu(false); menuButton.focus(); });
document.querySelectorAll('.sidebar a').forEach(link => link.addEventListener('click', () => setMenu(false)));
window.addEventListener('resize', () => { if (window.innerWidth > 720) setMenu(false); });

const dialog = document.querySelector('#search-dialog');
const input = document.querySelector('#search-input');
const results = document.querySelector('#search-results');
const status = document.querySelector('.search-status');
let searchIndex;
let selected = 0;
let matches = [];

function renderResults() {
  if (!searchIndex) return;
  const query = input.value.trim().toLowerCase();
  const terms = query.split(/\s+/).filter(Boolean);
  matches = searchIndex.map(page => {
    const title = page.title.toLowerCase();
    const body = `${page.title} ${page.description} ${page.text}`.toLowerCase();
    if (!terms.every(term => body.includes(term))) return null;
    const score = title === query ? 100 : terms.reduce((sum, term) => sum + (title.includes(term) ? 10 : 1), 0);
    return { ...page, score };
  }).filter(Boolean).sort((a, b) => b.score - a.score).slice(0, 8);
  selected = 0;
  results.replaceChildren();
  status.textContent = terms.length ? `${matches.length ? matches.length + ' matching guide' + (matches.length > 1 ? 's' : '') : 'No guides found. Try a different search.'}` : 'Explore the documentation';
  matches.forEach((page, index) => {
    const item = document.createElement('li');
    const link = document.createElement('a');
    link.href = page.url;
    if (!index) link.setAttribute('aria-current', 'true');
    const group = document.createElement('small');
    group.textContent = page.group;
    const title = document.createElement('strong');
    title.textContent = page.title;
    const excerpt = document.createElement('p');
    const matchPosition = terms.length ? page.text.toLowerCase().indexOf(terms[0]) : -1;
    if (matchPosition >= 0 && !page.title.toLowerCase().includes(terms[0])) {
      const start = Math.max(0, matchPosition - 55);
      excerpt.textContent = `${start ? '…' : ''}${page.text.slice(start, start + 160)}${page.text.length > start + 160 ? '…' : ''}`;
    } else excerpt.textContent = page.description;
    if (page.group) link.append(group);
    link.append(title, excerpt);
    item.append(link);
    results.append(item);
  });
}

async function openSearch() {
  if (!dialog.open) dialog.showModal();
  input.focus();
  if (!searchIndex) {
    status.textContent = 'Loading documentation…';
    try {
      const response = await fetch('/search.json');
      if (!response.ok) throw new Error('Search unavailable');
      searchIndex = await response.json();
    } catch {
      status.textContent = 'Search could not load. Close and reopen to try again.';
      return;
    }
  }
  renderResults();
}
document.querySelector('.search-trigger').addEventListener('click', openSearch);
document.querySelector('.search-close').addEventListener('click', () => dialog.close());
dialog.addEventListener('click', event => { if (event.target === dialog && event.offsetY < 0) dialog.close(); });
input.addEventListener('input', renderResults);
input.addEventListener('keydown', event => {
  if (['ArrowDown', 'ArrowUp'].includes(event.key) && matches.length) {
    event.preventDefault();
    selected = (selected + (event.key === 'ArrowDown' ? 1 : -1) + matches.length) % matches.length;
    results.querySelectorAll('a').forEach((link, index) => {
      if (index === selected) { link.setAttribute('aria-current', 'true'); link.scrollIntoView({ block: 'nearest' }); }
      else link.removeAttribute('aria-current');
    });
  }
  if (event.key === 'Enter' && matches[selected]) {
    event.preventDefault();
    window.location.assign(matches[selected].url);
  }
});
document.addEventListener('keydown', event => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); openSearch(); }
  if (event.key === 'Escape' && document.body.classList.contains('nav-open')) { setMenu(false); menuButton.focus(); }
});

const tocLinks = document.querySelectorAll('.table-of-contents nav a');
if (tocLinks.length && 'IntersectionObserver' in window) {
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      tocLinks.forEach(link => {
        if (link.hash === `#${entry.target.id}`) link.setAttribute('aria-current', 'location');
        else link.removeAttribute('aria-current');
      });
    });
  }, { rootMargin: '-100px 0px -65% 0px' });
  document.querySelectorAll('.prose h2, .prose h3').forEach(heading => observer.observe(heading));
}
