/** Characters that are illegal in file names on Windows (a superset of the other platforms). */
const ILLEGAL = /[\\/:*?"<>|]/g;

/** Drop control characters (code points below space), which no file system accepts. */
const withoutControls = (s: string) =>
  Array.from(s)
    .filter((c) => c.charCodeAt(0) >= 32)
    .join('');

export function sanitizeFileName(name: string): string {
  const cleaned = withoutControls(name)
    .replace(ILLEGAL, '_')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^\.+/, '');
  return cleaned === '' ? 'file' : cleaned.slice(0, 180);
}

export function baseNameOf(fileName: string): string {
  return fileName.replace(/\.[^./\\]+$/, '') || fileName;
}

export interface NameParts {
  name: string;
  page: number;
  total: number;
}

export const DEFAULT_TEMPLATE = '{name}-page-{page}';

/**
 * Fill a naming template. `{name}` is the document name without extension,
 * `{page}` the page number zero-padded to the width of the last page (so files
 * sort correctly), and `{total}` the page count.
 */
export function applyTemplate(template: string, parts: NameParts): string {
  const width = String(parts.total).length;
  const filled = (template.trim() || DEFAULT_TEMPLATE)
    .replace(/\{name\}/g, parts.name)
    .replace(/\{page\}/g, String(parts.page).padStart(width, '0'))
    .replace(/\{total\}/g, String(parts.total));
  return sanitizeFileName(filled);
}

/** Make `name` unique within `taken`, adding -2, -3 ... before the extension. */
export function uniqueName(name: string, taken: Set<string>): string {
  if (!taken.has(name)) {
    taken.add(name);
    return name;
  }
  const dot = name.lastIndexOf('.');
  const stem = dot > 0 ? name.slice(0, dot) : name;
  const ext = dot > 0 ? name.slice(dot) : '';
  for (let n = 2; ; n++) {
    const candidate = `${stem}-${n}${ext}`;
    if (!taken.has(candidate)) {
      taken.add(candidate);
      return candidate;
    }
  }
}
