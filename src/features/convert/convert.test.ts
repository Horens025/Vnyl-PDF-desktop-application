import { describe, expect, it } from 'vitest';

import { applyTemplate, baseNameOf, sanitizeFileName, uniqueName } from './naming';
import { parsePageRange } from './pageRange';
import { crc32, createZip } from './zip';

describe('parsePageRange', () => {
  it('treats empty and "all" as every page', () => {
    expect(parsePageRange('', 3)).toEqual({ pages: [1, 2, 3] });
    expect(parsePageRange(' ALL ', 2)).toEqual({ pages: [1, 2] });
  });

  it('parses singles, ranges and open ends, dropping duplicates', () => {
    expect(parsePageRange('1-3, 5, 8-', 10)).toEqual({ pages: [1, 2, 3, 5, 8, 9, 10] });
    expect(parsePageRange('-2,2,1', 5)).toEqual({ pages: [1, 2] });
    expect(parsePageRange('4,2', 5)).toEqual({ pages: [4, 2] });
  });

  it('rejects bad input with a readable message', () => {
    expect(parsePageRange('0', 5)).toHaveProperty('error');
    expect(parsePageRange('6', 5)).toHaveProperty('error');
    expect(parsePageRange('3-1', 5)).toHaveProperty('error');
    expect(parsePageRange('a-b', 5)).toHaveProperty('error');
    expect(parsePageRange('-', 5)).toHaveProperty('error');
    expect(parsePageRange('1', 0)).toHaveProperty('error');
  });
});

describe('file naming', () => {
  it('pads page numbers to the width of the last page', () => {
    expect(applyTemplate('{name}-p{page}', { name: 'report', page: 7, total: 120 })).toBe(
      'report-p007',
    );
    expect(applyTemplate('{page} of {total}', { name: 'x', page: 2, total: 9 })).toBe('2 of 9');
  });

  it('falls back to the default template and strips illegal characters', () => {
    expect(applyTemplate('  ', { name: 'a', page: 1, total: 1 })).toBe('a-page-1');
    expect(sanitizeFileName('a/b:c*d?.png')).toBe('a_b_c_d_.png');
    expect(sanitizeFileName('...')).toBe('file');
    expect(baseNameOf('My Report.v2.pdf')).toBe('My Report.v2');
  });

  it('makes duplicate names unique', () => {
    const taken = new Set<string>();
    expect(uniqueName('a.png', taken)).toBe('a.png');
    expect(uniqueName('a.png', taken)).toBe('a-2.png');
    expect(uniqueName('a.png', taken)).toBe('a-3.png');
  });
});

describe('zip', () => {
  it('computes the standard CRC-32 check value', () => {
    expect(crc32(new TextEncoder().encode('123456789'))).toBe(0xcbf43926);
  });

  it('writes a structurally valid stored archive', () => {
    const enc = new TextEncoder();
    const zip = createZip(
      [
        { name: 'a.txt', data: enc.encode('hello') },
        { name: 'dir/é.txt', data: enc.encode('world!') },
      ],
      new Date(2024, 4, 6, 12, 30, 10),
    );
    const v = new DataView(zip.buffer);
    expect(v.getUint32(0, true)).toBe(0x04034b50);
    // End-of-central-directory record: 2 entries, central dir inside the file.
    const eocd = zip.length - 22;
    expect(v.getUint32(eocd, true)).toBe(0x06054b50);
    expect(v.getUint16(eocd + 10, true)).toBe(2);
    const cdOffset = v.getUint32(eocd + 16, true);
    expect(v.getUint32(cdOffset, true)).toBe(0x02014b50);
    // First entry's stored size and CRC are recorded in the central directory.
    expect(v.getUint32(cdOffset + 20, true)).toBe(5);
    expect(v.getUint32(cdOffset + 16, true)).toBe(crc32(enc.encode('hello')));
  });

  it('makes an empty archive', () => {
    expect(createZip([]).length).toBe(22);
  });
});
