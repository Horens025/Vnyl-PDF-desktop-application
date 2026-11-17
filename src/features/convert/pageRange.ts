export type PageRangeResult = { pages: number[] } | { error: string };

/**
 * Parse a page selection such as "1-3, 5, 8-" against a document of `total`
 * pages. Empty or "all" means every page; "8-" runs to the end and "-3" starts
 * at page 1. Duplicates are dropped and order is kept as typed.
 */
export function parsePageRange(input: string, total: number): PageRangeResult {
  const text = input.trim().toLowerCase();
  if (total < 1) return { error: 'This document has no pages.' };
  if (text === '' || text === 'all') {
    return { pages: Array.from({ length: total }, (_, i) => i + 1) };
  }
  const seen = new Set<number>();
  const pages: number[] = [];
  const add = (n: number) => {
    if (!seen.has(n)) {
      seen.add(n);
      pages.push(n);
    }
  };

  for (const raw of text.split(',')) {
    const part = raw.trim();
    if (part === '') continue;
    const m = part.match(/^(\d*)\s*(-)?\s*(\d*)$/);
    if (!m || (m[1] === '' && m[3] === '')) return { error: `"${part}" is not a page or a range.` };
    let from: number;
    let to: number;
    if (!m[2]) {
      from = to = Number(m[1]);
    } else {
      from = m[1] === '' ? 1 : Number(m[1]);
      to = m[3] === '' ? total : Number(m[3]);
    }
    if (from < 1 || to < 1 || from > total || to > total) {
      return { error: `Pages must be between 1 and ${total}.` };
    }
    if (from > to) return { error: `"${part}" counts backwards.` };
    for (let p = from; p <= to; p++) add(p);
  }
  if (pages.length === 0) return { error: 'Enter at least one page.' };
  return { pages };
}
