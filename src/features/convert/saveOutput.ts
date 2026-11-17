import { invoke } from '@tauri-apps/api/core';
import { ask, open, save } from '@tauri-apps/plugin-dialog';

import { announce } from '@/a11y/announcer';
import { pushToast } from '@/components/common';
import { isTauri } from '@/core/document/openDocument';

import { downloadBlob, type OutputFile } from './engines';

export const extensionOf = (name: string): string => {
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.slice(dot + 1).toLowerCase() : '';
};

/** Join a directory and a file name with whichever separator the directory already uses. */
export function joinPath(dir: string, name: string): string {
  const sep = dir.includes('\\') && !dir.includes('/') ? '\\' : '/';
  return dir.endsWith(sep) ? dir + name : dir + sep + name;
}

/**
 * Hand a file to the Rust `write_export` command. The bytes travel as the raw
 * request body and the path as a percent-encoded header, exactly like
 * `write_document`, so a large page image is never expanded into a JSON array.
 */
async function writeExport(path: string, blob: Blob): Promise<void> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  await invoke('write_export', bytes, { headers: { 'Vnyl-Path': encodeURIComponent(path) } });
}

const FILTER_NAMES: Record<string, string> = {
  pdf: 'PDF document',
  png: 'PNG image',
  jpg: 'JPEG image',
  webp: 'WebP image',
  zip: 'Zip archive',
  txt: 'Text',
  md: 'Markdown',
  html: 'Web page',
};

/**
 * Save one converted file: a native Save dialog in the desktop app, a normal
 * download in a browser. Returns false if the user cancelled or it failed.
 */
export async function saveOutput(file: OutputFile): Promise<boolean> {
  try {
    if (!isTauri()) {
      downloadBlob(file.blob, file.name);
      announce(`Downloaded ${file.name}`);
      return true;
    }
    const ext = extensionOf(file.name);
    const path = await save({
      defaultPath: file.name,
      filters: ext
        ? [{ name: FILTER_NAMES[ext] ?? ext.toUpperCase(), extensions: [ext] }]
        : undefined,
    });
    if (!path) return false;
    await writeExport(path, file.blob);
    pushToast('Saved', 'success');
    announce(`Saved ${file.name}`);
    return true;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    announce(`Could not save ${file.name}: ${message}`, true);
    pushToast(`Could not save ${file.name}`, 'error');
    return false;
  }
}

/**
 * Desktop only: write every file into a folder the user picks. The files keep
 * their names, so existing files with the same names would be replaced; the
 * user is asked once before anything is written. Returns how many were saved,
 * or null if cancelled.
 */
export async function saveAllToFolder(files: readonly OutputFile[]): Promise<number | null> {
  if (!isTauri() || files.length === 0) return null;
  try {
    const dir = await open({
      directory: true,
      multiple: false,
      title: 'Choose a folder to save into',
    });
    if (typeof dir !== 'string') return null;
    const proceed = await ask(
      `Save ${files.length} file${files.length === 1 ? '' : 's'} to ${dir}? Any file there with the same name will be replaced.`,
      { title: 'Vnyl', kind: 'warning', okLabel: 'Save', cancelLabel: 'Cancel' },
    );
    if (!proceed) return null;
    for (const f of files) await writeExport(joinPath(dir, f.name), f.blob);
    pushToast(`Saved ${files.length} file${files.length === 1 ? '' : 's'}`, 'success');
    announce(`Saved ${files.length} files to ${dir}`);
    return files.length;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    announce(`Could not save the files: ${message}`, true);
    pushToast('Could not save the files', 'error');
    return null;
  }
}
