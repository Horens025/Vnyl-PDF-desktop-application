import { beforeEach, describe, expect, it, vi } from 'vitest';

import type * as Engines from './engines';

const mocks = vi.hoisted(() => ({
  isTauri: vi.fn(() => false),
  invoke: vi.fn(async () => undefined),
  save: vi.fn(),
  open: vi.fn(),
  ask: vi.fn(),
  downloadBlob: vi.fn(),
}));

vi.mock('@/core/document/openDocument', () => ({ isTauri: mocks.isTauri }));
vi.mock('@tauri-apps/api/core', () => ({ invoke: mocks.invoke }));
vi.mock('@tauri-apps/plugin-dialog', () => ({
  save: mocks.save,
  open: mocks.open,
  ask: mocks.ask,
}));
vi.mock('./engines', async (orig) => ({
  ...(await orig<typeof Engines>()),
  downloadBlob: mocks.downloadBlob,
}));

import { extensionOf, joinPath, saveAllToFolder, saveOutput } from './saveOutput';

const file = (name: string, text = 'data') => ({ name, blob: new Blob([text]) });

beforeEach(() => {
  vi.clearAllMocks();
  mocks.isTauri.mockReturnValue(false);
});

describe('paths', () => {
  it('finds extensions and joins with the directory’s own separator', () => {
    expect(extensionOf('a.PNG')).toBe('png');
    expect(extensionOf('noext')).toBe('');
    expect(joinPath('C:\\Users\\me\\out', 'a.png')).toBe('C:\\Users\\me\\out\\a.png');
    expect(joinPath('/home/me/out/', 'a.png')).toBe('/home/me/out/a.png');
    expect(joinPath('/home/me', 'a.png')).toBe('/home/me/a.png');
  });
});

describe('saveOutput', () => {
  it('downloads in a browser', async () => {
    expect(await saveOutput(file('p.png'))).toBe(true);
    expect(mocks.downloadBlob).toHaveBeenCalledOnce();
    expect(mocks.invoke).not.toHaveBeenCalled();
  });

  it('uses the native Save dialog and write_export on desktop', async () => {
    mocks.isTauri.mockReturnValue(true);
    mocks.save.mockResolvedValue('C:\\Users\\Ömer\\p.png');
    expect(await saveOutput(file('p.png', 'abc'))).toBe(true);
    expect(mocks.save).toHaveBeenCalledWith(
      expect.objectContaining({
        defaultPath: 'p.png',
        filters: [{ name: 'PNG image', extensions: ['png'] }],
      }),
    );
    const [cmd, bytes, options] = mocks.invoke.mock.calls[0] as unknown as [
      string,
      Uint8Array,
      { headers: Record<string, string> },
    ];
    expect(cmd).toBe('write_export');
    expect(Array.from(bytes)).toEqual([97, 98, 99]);
    expect(options.headers['Vnyl-Path']).toBe(encodeURIComponent('C:\\Users\\Ömer\\p.png'));
    expect(mocks.downloadBlob).not.toHaveBeenCalled();
  });

  it('writes nothing when the dialog is cancelled', async () => {
    mocks.isTauri.mockReturnValue(true);
    mocks.save.mockResolvedValue(null);
    expect(await saveOutput(file('p.png'))).toBe(false);
    expect(mocks.invoke).not.toHaveBeenCalled();
  });

  it('reports a failed write instead of throwing', async () => {
    mocks.isTauri.mockReturnValue(true);
    mocks.save.mockResolvedValue('/x/p.png');
    mocks.invoke.mockRejectedValueOnce(new Error('disk full'));
    expect(await saveOutput(file('p.png'))).toBe(false);
  });
});

describe('saveAllToFolder', () => {
  it('is a desktop-only action', async () => {
    expect(await saveAllToFolder([file('a.png')])).toBeNull();
  });

  it('asks before replacing, then writes each file into the folder', async () => {
    mocks.isTauri.mockReturnValue(true);
    mocks.open.mockResolvedValue('/out');
    mocks.ask.mockResolvedValue(true);
    expect(await saveAllToFolder([file('a.png'), file('b.png')])).toBe(2);
    const paths = mocks.invoke.mock.calls.map((c) =>
      decodeURIComponent(
        (c as unknown as [string, Uint8Array, { headers: Record<string, string> }])[2].headers[
          'Vnyl-Path'
        ],
      ),
    );
    expect(paths).toEqual(['/out/a.png', '/out/b.png']);
  });

  it('writes nothing if the user declines, or cancels the folder pick', async () => {
    mocks.isTauri.mockReturnValue(true);
    mocks.open.mockResolvedValue('/out');
    mocks.ask.mockResolvedValue(false);
    expect(await saveAllToFolder([file('a.png')])).toBeNull();
    mocks.open.mockResolvedValue(null);
    expect(await saveAllToFolder([file('a.png')])).toBeNull();
    expect(mocks.invoke).not.toHaveBeenCalled();
  });
});
