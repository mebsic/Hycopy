import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { extname, resolve, sep } from 'node:path';
import { loadDocumentation, escapeHtml } from './lib/content.mjs';
import { renderPage } from './lib/views.mjs';

const assetsDirectory = resolve(fileURLToPath(new URL('./public/', import.meta.url)));
const iconPath = fileURLToPath(new URL('./image.png', import.meta.url));
const types = { '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp' };
const aliases = new Map([
  ['Getting-Started-with-Docker', 'docker-quickstart'],
  ['Creating-and-Configuring-Maps', 'murdermystery/creating-maps'],
  ['creating-maps', 'murdermystery/creating-maps'],
  ['edit-menu', 'murdermystery/edit-menu'],
]);

export async function createDocumentationServer({ siteUrl = process.env.SITE_URL || 'https://hycopy.net', iconFile = iconPath } = {}) {
  const canonical = new URL(siteUrl);
  if (!['http:', 'https:'].includes(canonical.protocol) || canonical.pathname !== '/' || canonical.search || canonical.hash || canonical.username || canonical.password) throw new Error('SITE_URL must be an HTTP(S) origin, such as https://hycopy.net');
  siteUrl = canonical.origin;
  const documentation = await loadDocumentation({ siteUrl });
  let iconStats;
  try { iconStats = await stat(iconFile); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  const iconAvailable = Boolean(iconStats?.isFile());
  const assetPaths = ['styles.css', 'app.js', 'theme.js'].map(name => resolve(assetsDirectory, name));
  const assetStats = await Promise.all(assetPaths.map(path => stat(path)));
  if (iconAvailable) assetStats.push(iconStats);
  const assetVersion = Math.trunc(Math.max(...assetStats.map(asset => asset.mtimeMs))).toString(36);
  const pageMap = new Map(documentation.pages.map(page => [page.url, page]));
  const search = JSON.stringify(documentation.pages.map(page => ({ title: page.title, description: page.description, group: page.group, url: page.url, text: page.text })));

  return createServer(async (request, response) => {
    response.setHeader('X-Content-Type-Options', 'nosniff');
    response.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
    response.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    function send(status, type, body, extra = {}) {
      response.writeHead(status, { 'Content-Type': type, ...extra });
      response.end(request.method === 'HEAD' ? undefined : body);
    }
    function redirect(location, status = 301, extra = {}) { send(status, 'text/plain; charset=utf-8', `Redirecting to ${location}`, { Location: location, 'Cache-Control': 'no-store', ...extra }); }
    try {
      if (!['GET', 'HEAD'].includes(request.method)) return send(405, 'text/plain; charset=utf-8', 'Method not allowed', { Allow: 'GET, HEAD' });
      const url = new URL(request.url, 'http://localhost');
      const path = decodeURIComponent(url.pathname);
      // Clear the previously cached / -> /docs redirect before returning to the new root.
      if (path === '/docs' || path === '/docs/') return redirect(`/${url.search}`, 301, { 'Clear-Site-Data': '"cache"' });
      if (path.startsWith('/docs/')) {
        const legacyPath = path.slice('/docs'.length).replace(/\/$/, '');
        const alias = aliases.get(legacyPath.slice(1));
        const destination = alias ? `/${alias}` : legacyPath;
        if (pageMap.has(destination) || destination === '/search.json' || destination.startsWith('/assets/')) return redirect(destination + url.search);
      }
      if (path === '/healthz') return send(200, 'application/json; charset=utf-8', JSON.stringify({ status: 'ok' }));
      if (path === '/robots.txt') return send(200, 'text/plain; charset=utf-8', `User-agent: *\nAllow: /\nSitemap: ${siteUrl}/sitemap.xml\n`);
      if (path === '/sitemap.xml') return send(200, 'application/xml; charset=utf-8', `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${documentation.pages.map(page => `<url><loc>${escapeHtml(siteUrl + page.url)}</loc></url>`).join('')}</urlset>`);
      if (path === '/search.json') return send(200, 'application/json; charset=utf-8', search, { 'Cache-Control': 'public, max-age=300' });
      if (path === '/icon.png') return redirect('/image.png' + url.search);
      if (path === '/image.png') {
        if (!iconAvailable) return send(404, 'text/plain; charset=utf-8', 'Icon not available');
        return send(200, 'image/png', await readFile(iconFile), { 'Cache-Control': 'public, max-age=300' });
      }
      if (path.startsWith('/assets/')) {
        const asset = resolve(assetsDirectory, path.slice('/assets/'.length));
        if (asset.startsWith(assetsDirectory + sep) && types[extname(asset)]) {
          try {
            if ((await stat(asset)).isFile()) return send(200, types[extname(asset)], await readFile(asset), { 'Cache-Control': 'public, max-age=300' });
          } catch (error) { if (!['ENOENT', 'ENOTDIR'].includes(error.code)) throw error; }
        }
      }
      const alias = aliases.get(path.slice(1).replace(/\/$/, ''));
      if (alias) return redirect(`/${alias}${url.search}`);
      if (path.endsWith('/') && pageMap.has(path.slice(0, -1))) return redirect(path.slice(0, -1) + url.search, 308);
      const page = pageMap.get(path);
      return send(page ? 200 : 404, 'text/html; charset=utf-8', renderPage({ ...documentation, page: page || documentation.pages[0], siteUrl, assetVersion, iconAvailable, notFound: !page }));
    } catch (error) {
      if (error instanceof URIError || error instanceof TypeError) return send(400, 'text/plain; charset=utf-8', 'Bad request');
      console.error('Documentation request failed:', error.message);
      return send(500, 'text/plain; charset=utf-8', 'Unable to load this page.');
    }
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 3000);
  const server = await createDocumentationServer();
  server.listen(port, '0.0.0.0', () => console.log(`Hycopy documentation: http://localhost:${server.address().port}/`));
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => {
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 10_000).unref();
  });
}
