import { escapeHtml as e } from './content.mjs';

const paths = {
  home: '<path d="m3 10 9-7 9 7v10H3z"/><path d="M9 20v-7h6v7"/>',
  terminal: '<path d="m5 7 5 5-5 5m8 0h6"/><rect x="2" y="3" width="20" height="18" rx="3"/>',
  settings: '<path d="M4 7h16M4 17h16"/><circle cx="8" cy="7" r="3"/><circle cx="16" cy="17" r="3"/>',
  network: '<rect x="8" y="2" width="8" height="6" rx="1"/><path d="M12 8v6M5 14h14M5 14v3m14-3v3"/><rect x="2" y="17" width="6" height="5" rx="1"/><rect x="16" y="17" width="6" height="5" rx="1"/>',
  upload: '<path d="M12 16V3m-5 5 5-5 5 5M4 16v5h16v-5"/>',
  chart: '<path d="M4 3v18h17M8 16l4-5 4 2 5-7"/>',
  help: '<circle cx="12" cy="12" r="9"/><path d="M9 9a3 3 0 0 1 6 0c0 2-3 2-3 5m0 3h.01"/>',
  map: '<path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2zm6-2v16m6-14v16"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  layers: '<path d="m12 3 10 5-10 5L2 8zm-10 9 10 5 10-5M2 16l10 5 10-5"/>',
  code: '<path d="m8 5-6 7 6 7m8-14 6 7-6 7m-3-16-2 18"/>',
  arrow: '<path d="M4 12h16m-6-6 6 6-6 6"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  external: '<path d="M14 3h7v7m0-7L10 14M10 3H3v18h18v-7"/>',
  search: '<circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/>',
  copy: '<rect x="8" y="8" width="13" height="13" rx="2"/><path d="M16 8V3H3v13h5"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  light: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42"/>',
  dark: '<path d="M20.5 13A8.8 8.8 0 0 1 11 3.5 9 9 0 1 0 20.5 13Z"/>',
  book: '<path d="M12 5v16M12 5c-3-3-7-3-10-2v16c4-1 7-1 10 2 3-3 6-3 10-2V3c-3-1-7-1-10 2"/>',
};
export const icon = (name, className = '') => `<svg class="icon ${className}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.book}</svg>`;

function containsPage(entries, slug) {
  return entries.some(entry => Array.isArray(entry.pages) ? containsPage(entry.pages, slug) : entry.slug === slug);
}

function navigationItems(entries, slug) {
  const dropdownCount = entries.filter(entry => Array.isArray(entry.pages)).length;
  return entries.map(entry => {
    if (Array.isArray(entry.pages)) {
      const current = containsPage(entry.pages, slug);
      const expanded = dropdownCount === 1 || current;
      return `<li><details class="nav-dropdown${current ? ' nav-dropdown-current' : ''}"${expanded ? ' open' : ''}><summary>${icon(entry.icon)}<span>${e(entry.label)}</span>${icon('chevron', 'nav-chevron')}</summary><ul class="nav-dropdown-pages">${navigationItems(entry.pages, slug)}</ul></details></li>`;
    }
    return `<li><a href="${entry.slug ? `/${e(entry.slug)}` : '/'}" ${entry.slug === slug ? 'aria-current="page"' : ''}>${icon(entry.icon)}<span>${e(entry.title)}</span>${entry.slug === slug ? '<span class="active-dot"></span>' : ''}</a></li>`;
  }).join('');
}

function sidebar(navigation, slug) {
  return `<aside class="sidebar" id="sidebar" aria-label="Documentation navigation">
    <div class="sidebar-intro"><span class="server-invite">Try it out</span><p class="server-description">Minecraft Java Edition</p><button class="server-address" data-copy="mc.hycopy.net" aria-label="Copy Minecraft server address"><span>mc.hycopy.net</span>${icon('copy')}</button></div>
    <nav>${navigation.map(group => `<section class="nav-group${group.label ? '' : ' nav-group-unlabeled'}">${group.label ? `<h2>${e(group.label)}</h2>` : ''}<ul>${navigationItems(group.pages, slug)}</ul></section>`).join('')}</nav>
    <a target="_blank" rel="noopener noreferrer" class="sidebar-source" href="https://github.com/mebsic/Hycopy">${icon('code')} Open source on GitHub ${icon('external')}</a>
  </aside>`;
}

function home() {
  return `<div class="home-content">
    <section class="hero" aria-labelledby="home-title">
      <h1 id="home-title">Hycopy</h1>
      <p class="hero-description">Open-source Hypixel recreation. This website covers installation, server configuration, map creation, and network operations.</p>
      <div class="hero-actions"><a class="button button-primary" href="/docker-quickstart">Quickstart ${icon('arrow')}</a><a class="button button-subtle" href="/architecture">Architecture ${icon('arrow')}</a></div>
      <div class="hero-meta"><span>${icon('layers')} Paper + Velocity</span><span>${icon('terminal')} Deployed using Docker</span><span>${icon('code')} Minecraft Java Edition</span></div>
    </section>
    <section class="start-section" aria-labelledby="start-title"><div class="section-heading"><h2 id="start-title">Guides</h2><span class="section-note">Choose a topic to get started.</span></div>
      <div class="guide-cards">
        <a class="guide-card" href="/docker-quickstart"><span class="card-icon">${icon('terminal')}</span><h3>Quickstart</h3><p>Start the Docker stack, configure your environment, and connect to the local server.</p><span class="card-link">Read more ${icon('arrow')}</span></a>
        <a class="guide-card" href="/murdermystery/creating-maps"><span class="card-icon">${icon('map')}</span><h3>Creation</h3><p>Create a build world, place spawns, and export a reusable map template.</p><span class="card-link">Read more ${icon('arrow')}</span></a>
        <a class="guide-card" href="/architecture"><span class="card-icon">${icon('network')}</span><h3>Architecture</h3><p>Understand the proxy, Paper servers, databases, and service discovery.</p><span class="card-link">Read more ${icon('arrow')}</span></a>
      </div>
    </section>
    <section class="quickstart-panel" aria-labelledby="quickstart-title"><div class="quickstart-copy"><h2 id="quickstart-title">Setup</h2><p>Use Docker Compose to run the proxy, Paper servers, MongoDB, Redis, and the internal control panel.</p><a class="text-link" href="/docker-quickstart">Follow the setup guide ${icon('arrow')}</a></div>
      <div class="terminal-preview"><div class="terminal-top"><span class="terminal-dots"><i></i><i></i><i></i></span><span>local setup</span>${icon('terminal')}</div><pre><code class="language-bash"># From the repository root, after configuring .env\n./gradlew shadowAll\n\ndocker compose up --build\n\n# Connect with Minecraft Java Edition\n# localhost:25565</code></pre><div class="terminal-foot"><span>Environment settings</span><a href="/configuration">Configuration ${icon('arrow')}</a></div></div>
    </section>
    <section class="explore-section"><div class="section-heading"><h2>Resources</h2></div><div class="explore-links">
      <a href="/deployment">${icon('upload')}<span><strong>Deployment</strong><small>Update plugins and restart services.</small></span>${icon('arrow')}</a>
      <a href="/troubleshooting">${icon('help')}<span><strong>Troubleshooting</strong><small>Diagnose startup and map issues.</small></span>${icon('arrow')}</a>
      <a href="/contributing">${icon('code')}<span><strong>Contributing</strong><small>Report issues and propose changes.</small></span>${icon('arrow')}</a>
    </div></section>
  </div>`;
}

function article(page, pages) {
  const index = pages.findIndex(entry => entry.slug === page.slug);
  const previous = pages[index - 1];
  const next = pages[index + 1];
  return `<div class="article-layout"><div class="article-column">
    <header class="article-header"><div class="breadcrumbs"><a href="/">Documentation</a>${page.group ? `<span>/</span><span>${e(page.group)}</span>` : ''}</div><h1>${e(page.title)}</h1><p>${e(page.description)}</p><div class="article-meta"><span>${icon('book')} ${Math.max(1, Math.ceil(page.text.split(/\s+/).length / 220))} min read</span></div></header>
    <article class="prose">${page.html}</article>
    <nav class="page-pagination" aria-label="Previous and next guides">${previous ? `<a href="${e(previous.url)}"><small>PREVIOUS</small><strong>← ${e(previous.title)}</strong></a>` : '<span></span>'}${next ? `<a href="${e(next.url)}"><small>NEXT</small><strong>${e(next.title)} →</strong></a>` : '<span></span>'}</nav>
    <div class="feedback-link">Something missing? <a target="_blank" rel="noopener noreferrer" href="https://github.com/mebsic/Hycopy/issues/new">Help improve this guide ${icon('external')}</a></div>
  </div><aside class="table-of-contents" aria-label="On this page"><span class="section-label">ON THIS PAGE</span><nav>${page.headings.map(heading => `<a href="#${e(heading.id)}" class="toc-depth-${heading.depth}">${e(heading.text)}</a>`).join('')}</nav><div class="toc-note"><a target="_blank" rel="noopener noreferrer" href="https://github.com/mebsic/Hycopy">${icon('code')}<span>View the source</span>${icon('external')}</a></div></aside></div>`;
}

export function renderPage({ page, navigation, pages, siteUrl, assetVersion = '', iconAvailable = true, notFound = false }) {
  const assetQuery = assetVersion ? `?v=${encodeURIComponent(assetVersion)}` : '';
  const favicon = iconAvailable ? `<link rel="icon" type="image/png" href="/image.png${assetQuery}">` : '';
  const brand = iconAvailable ? `<a class="brand" href="/" aria-label="Hycopy documentation"><img src="/image.png${assetQuery}" width="32" height="32" alt=""></a>` : '';
  const title = `Hycopy - ${notFound ? 'Page not found' : page.title}`;
  const description = notFound ? 'Find your way back to the Hycopy documentation.' : page.description;
  const body = notFound ? `<div class="not-found"><h1>404</h1><p>Page not found</p><a class="button button-primary" href="/">Back to overview ${icon('arrow')}</a></div>` : page.slug ? article(page, pages) : home();
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="color-scheme" content="light dark"><title>${e(title)}</title><meta name="description" content="${e(description)}">${notFound ? '<meta name="robots" content="noindex">' : `<link rel="canonical" href="${e(siteUrl + page.url)}">`}<meta property="og:title" content="${e(title)}"><meta property="og:description" content="${e(description)}"><meta property="og:type" content="website">${favicon}<link rel="stylesheet" href="/assets/styles.css${assetQuery}"><script src="/assets/theme.js${assetQuery}"></script><script src="/assets/app.js${assetQuery}" defer></script></head><body>
    <a class="skip-link" href="#main-content">Skip to content</a>
    <header class="site-header"><div class="header-brand"><button class="menu-toggle icon-button" aria-label="Open navigation" aria-expanded="false" aria-controls="sidebar">${icon('menu')}</button>${brand}</div><nav class="header-nav" aria-label="Primary"><a href="/" ${!page.slug ? 'aria-current="page"' : ''}>Overview</a><a href="/docker-quickstart" ${page.slug ? 'aria-current="page"' : ''}>Guides</a><a target="_blank" rel="noopener noreferrer" href="https://discord.com">Discord ${icon('external')}</a><a target="_blank" rel="noopener noreferrer" href="https://github.com/mebsic/Hycopy">GitHub ${icon('external')}</a></nav><div class="header-actions"><button class="search-trigger" aria-label="Search documentation" aria-haspopup="dialog">${icon('search')}<span>Search</span><kbd>⌘ K</kbd></button><button class="theme-toggle icon-button" aria-label="Toggle color theme" aria-pressed="false">${icon('light', 'theme-light')}${icon('dark', 'theme-dark')}</button></div></header>
    ${sidebar(navigation, notFound ? null : page.slug)}<button class="nav-overlay" aria-label="Close navigation" tabindex="-1"></button>
    <main id="main-content" tabindex="-1">${body}<footer class="site-footer"><span>Built by <a target="_blank" rel="noopener noreferrer" href="https://github.com/mebsic">mebsic</a></span><span>Unofficial project. Not affiliated with Hypixel Inc.</span><a target="_blank" rel="noopener noreferrer" href="https://github.com/mebsic/Hycopy/blob/main/LICENSE">GPL-3.0 ${icon('external')}</a></footer></main>
    <dialog id="search-dialog" aria-labelledby="search-title">
      <div class="search-modal-top">${icon('search')}<label class="sr-only" id="search-title" for="search-input">Search documentation</label><input id="search-input" type="search" placeholder="What would you like to find?" autocomplete="off"><button class="search-close" aria-label="Close search">Esc</button></div>
      <div class="search-shortcuts"><span><kbd>↑</kbd> <kbd>↓</kbd> to navigate</span><span><kbd>↵</kbd> to open</span></div>
      <p class="search-status" role="status">Search guides, commands, and configuration.</p><ul id="search-results" aria-label="Search results"></ul>
    </dialog><div class="toast" role="status" aria-live="polite"></div>
  </body></html>`;
}
