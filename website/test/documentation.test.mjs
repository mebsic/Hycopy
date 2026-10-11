import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createDocumentationServer } from '../server.mjs';
import { loadDocumentation, renderMarkdown } from '../lib/content.mjs';

let server;
let base;
let iconBytes;
before(async () => {
  try { iconBytes = await readFile(new URL('../image.png', import.meta.url)); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  server = await createDocumentationServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  base = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { if (server?.listening) await new Promise(resolve => server.close(resolve)); });

test('overview is served at the root without redirecting', async () => {
  const response = await fetch(base + '/?ref=github', { redirect: 'manual' });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('location'), null);
  assert.match(await response.text(), /<h1 id="home-title">Hycopy<\/h1>/);
  const trailing = await fetch(base + '/configuration/?ref=github', { redirect: 'manual' });
  assert.equal(trailing.status, 308);
  assert.equal(trailing.headers.get('location'), '/configuration?ref=github');
});

test('old documentation URLs redirect to direct routes and preserve query strings', async () => {
  const { pages } = await loadDocumentation();
  const redirects = [
    ['/docs/', '/'],
    ...pages.map(page => [page.slug ? `/docs/${page.slug}` : '/docs', page.url]),
    ['/docs/configuration/', '/configuration'],
    ['/docs/search.json', '/search.json'],
    ['/docs/assets/styles.css', '/assets/styles.css'],
    ['/docs/Getting-Started-with-Docker', '/docker-quickstart'],
    ['/icon.png', '/image.png'],
    ['/creating-maps', '/murdermystery/creating-maps'],
    ['/edit-menu', '/murdermystery/edit-menu'],
    ['/creating-maps/', '/murdermystery/creating-maps'],
    ['/edit-menu/', '/murdermystery/edit-menu'],
    ['/docs/creating-maps', '/murdermystery/creating-maps'],
    ['/docs/edit-menu', '/murdermystery/edit-menu'],
    ['/docs/Creating-and-Configuring-Maps', '/murdermystery/creating-maps'],
  ];
  for (const [oldPath, newPath] of redirects) {
    const response = await fetch(base + oldPath + '?ref=github', { redirect: 'manual' });
    assert.equal(response.status, 301, oldPath);
    assert.equal(response.headers.get('location'), newPath + '?ref=github');
    assert.equal(response.headers.get('cache-control'), 'no-store');
    if (newPath === '/') assert.equal(response.headers.get('clear-site-data'), '"cache"');
    const expectedStatus = newPath === '/image.png' && !iconBytes ? 404 : 200;
    assert.equal((await fetch(base + response.headers.get('location'), { redirect: 'manual' })).status, expectedStatus);
  }
});

test('Murder Mystery guides use nested routes while Templates stays at the root', async () => {
  for (const path of ['/murdermystery/creating-maps', '/murdermystery/edit-menu', '/map-templates']) {
    const response = await fetch(base + path, { redirect: 'manual' });
    assert.equal(response.status, 200, path);
    assert.equal(response.headers.get('location'), null);
    assert.ok((await response.text()).includes(`rel="canonical" href="https://hycopy.net${path}"`));
    const trailing = await fetch(base + path + '/?ref=map', { redirect: 'manual' });
    assert.equal(trailing.status, 308);
    assert.equal(trailing.headers.get('location'), path + '?ref=map');
  }
});

test('every guide renders directly, its links resolve, and only external links open in a new tab', async () => {
  const { pages } = await loadDocumentation();
  for (const page of pages) {
    const response = await fetch(base + page.url, { redirect: 'manual' });
    assert.equal(response.status, 200, page.url);
    const html = await response.text();
    assert.match(html, /<main id="main-content"/);
    assert.doesNotMatch(html, /(?:href|src)="\/docs(?:\/|["?#])/);
    for (const match of html.matchAll(/<a\b[^>]*>/g)) {
      const href = match[0].match(/href="([^"]*)"/)?.[1];
      if (!href) continue;
      const destination = new URL(href, 'https://hycopy.net');
      if (destination.origin !== 'https://hycopy.net') {
        assert.match(match[0], /target="_blank"/, `${page.url}: ${match[0]}`);
        assert.match(match[0], /rel="noopener noreferrer"/, `${page.url}: ${match[0]}`);
      } else assert.doesNotMatch(match[0], /target="_blank"/, `${page.url}: ${match[0]}`);
    }
    for (const match of html.matchAll(/<a\b[^>]*href="(\/[^"#?]*)(?:#([^"?]*))?"/g)) {
      const target = pages.find(entry => entry.url === match[1]);
      assert.ok(target, `Missing page link: ${match[1]}`);
      if (match[2]) assert.ok(target.headings.some(heading => heading.id === match[2]), `Missing heading: ${match[1]}#${match[2]}`);
    }
  }
});

test('unknown routes and private files are not exposed; HEAD and unsupported methods behave correctly', async () => {
  for (const path of ['/missing', '/__proto__', '/constructor', '/.env', '/website/server.mjs', '/assets/%2e%2e%2f%2e%2e%2fpackage.json', '/docs/missing', '/docs//example.com']) {
    const response = await fetch(base + path);
    assert.equal(response.status, 404, path);
    assert.match(await response.text(), /<h1>404<\/h1><p>Page not found<\/p>/);
  }
  const head = await fetch(base + '/docker-quickstart', { method: 'HEAD' });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), '');
  const post = await fetch(base + '/', { method: 'POST' });
  assert.equal(post.status, 405);
  assert.equal(post.headers.get('allow'), 'GET, HEAD');
  const malformed = await fetch(base + '/%E0%A4%A');
  assert.equal(malformed.status, 400);
});

test('search contains guide text; assets, health, canonical URLs and sitemap are served', async () => {
  const index = await (await fetch(base + '/search.json')).json();
  assert.equal(index.length, 11);
  assert.equal(index[0].url, '/');
  assert.ok(index.every(page => !page.url.startsWith('/docs')));
  assert.match(index.find(page => page.url === '/murdermystery/edit-menu').text, /Player Spawn/);
  const asset = await fetch(base + '/assets/styles.css');
  assert.equal(asset.status, 200);
  assert.match(asset.headers.get('content-type'), /text\/css/);
  assert.ok(asset.headers.get('content-security-policy').includes("frame-ancestors 'none'"));
  assert.deepEqual(await (await fetch(base + '/healthz')).json(), { status: 'ok' });
  const home = await (await fetch(base + '/')).text();
  assert.match(home, /rel="canonical" href="https:\/\/hycopy.net\/"/);
  const iconResponse = await fetch(base + '/image.png');
  if (iconBytes) {
    assert.match(home, /rel="icon" type="image\/png" href="\/image\.png\?v=/);
    assert.match(home, /class="brand"[^]*?<img src="\/image\.png\?v=/);
    assert.equal(iconResponse.status, 200);
    assert.equal(iconResponse.headers.get('content-type'), 'image/png');
    assert.deepEqual(Buffer.from(await iconResponse.arrayBuffer()), iconBytes);
  } else {
    assert.doesNotMatch(home, /(?:href|src)="\/image\.png/);
    assert.equal(iconResponse.status, 404);
  }
  const assetUrls = new Set([...home.matchAll(/(?:href|src)="(\/assets\/[^"<>]+)"/g)].map(match => match[1]));
  assetUrls.add('/assets/network.svg');
  for (const assetUrl of assetUrls) assert.equal((await fetch(base + assetUrl)).status, 200, assetUrl);
  const discordPosition = home.indexOf('href="https://discord.com"');
  const githubPosition = home.indexOf('>GitHub ');
  assert.ok(discordPosition >= 0 && githubPosition > discordPosition);
  const sitemap = await (await fetch(base + '/sitemap.xml')).text();
  assert.match(sitemap, /<loc>https:\/\/hycopy.net\/<\/loc>/);
  assert.match(sitemap, /https:\/\/hycopy.net\/docker-quickstart/);
  assert.match(sitemap, /https:\/\/hycopy.net\/murdermystery\/creating-maps/);
  assert.match(sitemap, /https:\/\/hycopy.net\/murdermystery\/edit-menu/);
  assert.doesNotMatch(sitemap, /https:\/\/hycopy.net\/(?:creating-maps|edit-menu)</);
  assert.doesNotMatch(sitemap, /\/docs/);
  assert.match(await (await fetch(base + '/robots.txt')).text(), /Allow: \/\n/);
});

test('optional branding serves the supplied file when present and does not block startup when absent', async t => {
  const directory = await mkdtemp(join(tmpdir(), 'hycopy-icon-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const suppliedIcon = join(directory, 'image.png');
  await writeFile(suppliedIcon, Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5l8AAAAASUVORK5CYII=', 'base64'));
  const cases = [
    [suppliedIcon, true],
    [join(directory, 'not-supplied-icon.png'), false],
  ];
  for (const [iconFile, available] of cases) {
    const app = await createDocumentationServer({ iconFile });
    await new Promise(resolve => app.listen(0, '127.0.0.1', resolve));
    try {
      const origin = `http://127.0.0.1:${app.address().port}`;
      const home = await fetch(origin);
      assert.equal(home.status, 200);
      const html = await home.text();
      assert.equal(html.includes('rel="icon" type="image/png"'), available);
      const image = await fetch(origin + '/image.png');
      assert.equal(image.status, available ? 200 : 404);
      if (available) assert.deepEqual(Buffer.from(await image.arrayBuffer()), await readFile(iconFile));
    } finally { await new Promise(resolve => app.close(resolve)); }
  }
});

test('Heroku entrypoint uses PORT, serves direct routes, and shuts down on SIGTERM', { timeout: 10_000 }, async t => {
  const child = spawn(process.execPath, [fileURLToPath(new URL('../server.mjs', import.meta.url))], {
    cwd: tmpdir(),
    env: { ...process.env, PORT: '0', SITE_URL: 'https://hycopy.example' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  t.after(() => { if (child.exitCode === null) child.kill('SIGKILL'); });
  let output = '';
  let errors = '';
  child.stderr.on('data', chunk => { errors += chunk; });
  const origin = await new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', code => reject(new Error(`Server exited before startup (${code}): ${errors}`)));
    child.stdout.on('data', chunk => {
      output += chunk;
      const address = output.match(/http:\/\/localhost:(\d+)\//);
      if (address) resolve(`http://127.0.0.1:${address[1]}`);
    });
  });
  const root = await fetch(origin, { redirect: 'manual' });
  assert.equal(root.status, 200);
  assert.match(await root.text(), /rel="canonical" href="https:\/\/hycopy.example\/"/);
  assert.equal((await fetch(origin + '/configuration', { redirect: 'manual' })).status, 200);
  assert.equal((await fetch(origin + '/murdermystery/edit-menu', { redirect: 'manual' })).status, 200);
  assert.deepEqual(await (await fetch(origin + '/healthz')).json(), { status: 'ok' });
  const stopped = once(child, 'exit');
  child.kill('SIGTERM');
  assert.deepEqual(await stopped, [0, null]);
});

test('Markdown removes executable HTML while preserving screenshots, captions, and unique heading IDs', () => {
  const result = renderMarkdown('## Example\n\n## Example\n\n<script>alert(1)</script>\n\n[Unsafe](javascript:alert(1))\n\n<figure><img src="/assets/menu.png" onerror="alert(1)" alt="Map menu"><figcaption>Place a spawn.</figcaption></figure>');
  assert.doesNotMatch(result.html, /<script|onerror|javascript:/);
  assert.match(result.html, /<figcaption>Place a spawn\.<\/figcaption>/);
  assert.deepEqual(result.headings.map(heading => heading.id), ['example', 'example-1']);
  const overridden = renderMarkdown('<a href="https://example.com" target="_self" rel="opener">Example</a>');
  assert.match(overridden.html, /target="_blank"/);
  assert.match(overridden.html, /rel="noopener noreferrer"/);
  const internal = renderMarkdown('<a href="https://hycopy.net/" target="_blank">Home</a>\n\n[Editor](/murdermystery/edit-menu)\n\n[Section](#example)');
  assert.doesNotMatch(internal.html, /target="_blank"/);
});

