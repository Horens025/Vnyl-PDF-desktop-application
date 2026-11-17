/** One run of a word diff. */
export interface DiffOp {
  type: 'equal' | 'add' | 'remove';
  text: string;
}

const tokenize = (s: string): string[] => s.split(/\s+/).filter(Boolean);

/** Above this many cells the exact diff is skipped for a coarse one (memory/time guard). */
const MAX_LCS_CELLS = 4_000_000;

function push(ops: DiffOp[], type: DiffOp['type'], words: string[]): void {
  if (words.length === 0) return;
  const last = ops[ops.length - 1];
  const text = words.join(' ');
  if (last && last.type === type) last.text += ` ${text}`;
  else ops.push({ type, text });
}

/**
 * Word-level diff of two texts (longest common subsequence). Whitespace and
 * line breaks are not significant, which is what you want when comparing the
 * text PDF.js extracts from two revisions of a document.
 *
 * Common words at both ends are trimmed first, so the quadratic part only runs
 * over the region that actually changed. If even that is huge, it degrades to
 * "this whole region was replaced" rather than hanging the tab.
 */
export function diffWords(a: string, b: string): DiffOp[] {
  const A = tokenize(a);
  const B = tokenize(b);
  let start = 0;
  while (start < A.length && start < B.length && A[start] === B[start]) start++;
  let endA = A.length;
  let endB = B.length;
  while (endA > start && endB > start && A[endA - 1] === B[endB - 1]) {
    endA--;
    endB--;
  }

  const ops: DiffOp[] = [];
  push(ops, 'equal', A.slice(0, start));

  const midA = A.slice(start, endA);
  const midB = B.slice(start, endB);
  const n = midA.length;
  const m = midB.length;

  if (n === 0 || m === 0 || n * m > MAX_LCS_CELLS) {
    push(ops, 'remove', midA);
    push(ops, 'add', midB);
  } else {
    // lcs[i][j] = length of the LCS of midA[i..] and midB[j..]
    const w = m + 1;
    const lcs = new Uint32Array((n + 1) * w);
    for (let i = n - 1; i >= 0; i--) {
      for (let j = m - 1; j >= 0; j--) {
        lcs[i * w + j] =
          midA[i] === midB[j]
            ? lcs[(i + 1) * w + j + 1] + 1
            : Math.max(lcs[(i + 1) * w + j], lcs[i * w + j + 1]);
      }
    }
    let i = 0;
    let j = 0;
    while (i < n && j < m) {
      if (midA[i] === midB[j]) {
        push(ops, 'equal', [midA[i]]);
        i++;
        j++;
      } else if (lcs[(i + 1) * w + j] >= lcs[i * w + j + 1]) {
        push(ops, 'remove', [midA[i++]]);
      } else {
        push(ops, 'add', [midB[j++]]);
      }
    }
    push(ops, 'remove', midA.slice(i));
    push(ops, 'add', midB.slice(j));
  }

  push(ops, 'equal', A.slice(endA));
  return ops;
}

export interface DiffStats {
  added: number;
  removed: number;
}

export function diffStats(ops: readonly DiffOp[]): DiffStats {
  const count = (t: DiffOp['type']) =>
    ops.filter((o) => o.type === t).reduce((n, o) => n + tokenize(o.text).length, 0);
  return { added: count('add'), removed: count('remove') };
}

export interface Pixels {
  width: number;
  height: number;
  /** RGBA, row-major, as from ImageData. */
  data: Uint8ClampedArray;
}

export interface PixelDiff {
  /** RGBA image: unchanged ink faded to grey, removed ink red, added ink green. */
  data: Uint8ClampedArray;
  changed: number;
  /** changed / total pixels. */
  ratio: number;
}

const luminance = (d: Uint8ClampedArray, i: number) =>
  0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2];

/**
 * Visual diff of two same-sized renders. A pixel that is darker in A than in B
 * is ink only A has (red, "removed"); darker in B is ink only B has (green,
 * "added"). Anything within `threshold` of equal is drawn as faded grey so the
 * changes stand out against the page they sit on.
 */
export function diffPixels(a: Pixels, b: Pixels, threshold = 48): PixelDiff {
  if (a.width !== b.width || a.height !== b.height) {
    throw new Error('diffPixels needs two images of the same size');
  }
  const total = a.width * a.height;
  const out = new Uint8ClampedArray(total * 4);
  let changed = 0;
  for (let p = 0; p < total; p++) {
    const i = p * 4;
    const la = luminance(a.data, i);
    const lb = luminance(b.data, i);
    if (Math.abs(la - lb) > threshold) {
      changed++;
      if (la < lb) {
        out[i] = 230;
        out[i + 1] = 57;
        out[i + 2] = 70;
      } else {
        out[i] = 40;
        out[i + 1] = 180;
        out[i + 2] = 90;
      }
    } else {
      const v = 255 - (255 - la) * 0.35;
      out[i] = out[i + 1] = out[i + 2] = v;
    }
    out[i + 3] = 255;
  }
  return { data: out, changed, ratio: total ? changed / total : 0 };
}
