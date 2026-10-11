import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { Marked } from 'marked';
import sanitizeHtml from 'sanitize-html';

export const escapeHtml = value => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const plainText = html => sanitizeHtml(html, { allowedTags: [], allowedAttributes: {} });

export function renderMarkdown(markdown, { siteUrl = 'https://hycopy.net' } = {}) {
  const siteOrigin = new URL(siteUrl).origin;
  const headings = [];
  const usedIds = new Map();
  const parser = new Marked({ gfm: true });
  parser.use({ renderer: {
    heading({ tokens, depth }) {
      const html = this.parser.parseInline(tokens);
      const text = plainText(html);
      const base = text.toLowerCase().replace(/[^\p{L}\p{N}\s-]/gu, '').trim().replace(/\s+/g, '-') || 'section';
      const count = usedIds.get(base) || 0;
      usedIds.set(base, count + 1);
      const id = count ? `${base}-${count}` : base;
      if (depth === 2 || depth === 3) headings.push({ id, text, depth });
      return `<h${depth} id="${escapeHtml(id)}">${html}<a class="heading-anchor" href="#${escapeHtml(id)}" aria-label="Link to ${escapeHtml(text)}">#</a></h${depth}>\n`;
    },
  } });
  const html = sanitizeHtml(parser.parse(markdown), {
    allowedTags: [...sanitizeHtml.defaults.allowedTags, 'img', 'figure', 'figcaption'],
    allowedAttributes: { ...sanitizeHtml.defaults.allowedAttributes, '*': ['id', 'class'], a: ['href', 'title', 'aria-label', 'target', 'rel'], img: ['src', 'alt', 'title', 'width', 'height', 'loading'], code: ['class'] },
    transformTags: {
      a: (tagName, { target, rel, ...attributes }) => {
        let external = false;
        try {
          const destination = new URL(attributes.href || '', siteUrl);
          external = ['http:', 'https:'].includes(destination.protocol) && destination.origin !== siteOrigin;
        } catch { /* Leave malformed URLs for the sanitizer to handle. */ }
        return { tagName, attribs: external ? { ...attributes, target: '_blank', rel: 'noopener noreferrer' } : attributes };
      },
    },
    allowedSchemes: ['http', 'https', 'mailto'],
    allowProtocolRelative: false,
  });
  const searchableHtml = html.replace(/<a class="heading-anchor"[^>]*>#[<]\/a>/g, '');
  return { html, headings, text: plainText(searchableHtml).replace(/\s+/g, ' ').trim() };
}

export async function loadDocumentation({ siteUrl = 'https://hycopy.net' } = {}) {
  const directory = new URL('../content/', import.meta.url);
  const navigation = JSON.parse(await readFile(new URL('navigation.json', directory), 'utf8'));
  const pages = [];
  async function loadEntries(entries, labels) {
    for (const entry of entries) {
      if (Array.isArray(entry.pages)) {
        await loadEntries(entry.pages, [...labels, entry.label]);
        continue;
      }
      const markdown = entry.slug ? await readFile(new URL(`${entry.slug}.md`, directory), 'utf8') : '';
      pages.push({ ...entry, group: labels.filter(Boolean).join(' / '), markdown, ...renderMarkdown(markdown, { siteUrl }), url: entry.slug ? `/${entry.slug}` : '/' });
    }
  }
  for (const group of navigation) await loadEntries(group.pages, [group.label]);
  return { navigation, pages, directory: fileURLToPath(directory) };
}
