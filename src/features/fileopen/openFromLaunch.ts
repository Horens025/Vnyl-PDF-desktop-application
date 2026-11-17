import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';

import { announce } from '@/a11y/announcer';
import { pushToast } from '@/components/common';
import { isTauri, readPath } from '@/core/document/openDocument';
import { takeResumeDocument } from '@/features/updates';
import { loadSource } from '@/state/actions';
import { useDocumentStore } from '@/state/documentStore';
import { useViewerStore } from '@/state/viewerStore';

/**
 * Read a PDF path from disk and load it into the viewer.
 *
 */
async function openPath(
  path: string,
  { page, silent = false }: { page?: number; silent?: boolean } = {},
): Promise<void> {
  try {
    const source = await readPath(path);
    await loadSource(source);
    // Resolving is not the same as loading: loadSource catches its own
    // failures, records them on the document store, and returns normally. On a
    // file whose bytes read fine but whose PDF structure does not parse, do not
    // announce a successful open over the viewer's failure message.
    if (useDocumentStore.getState().status !== 'ready') return;
    if (page && page > 1) useViewerStore.getState().goToPage(page);
    announce(`Opened ${source.name}`);
  } catch (error) {
    const messageText = error instanceof Error ? error.message : String(error);
    if (silent) console.warn('[fileopen] could not reopen', path, messageText);
    else pushToast(`Could not open the PDF: ${messageText}`, 'error');
  }
}

/**
 * Load a PDF the OS handed to Vnyl as the default viewer (desktop only).
 *
 * Three paths, the first two mirroring the `vnyl://` deep-link handling in
 * `../deeplink`:
 * - Cold start: Vnyl was launched with a file (double-click). The Rust side
 *   captured the path from argv; we consume it once via `take_launch_file`.
 * - Already running: a second launch forwards the file to this window as a
 *   `vnyl:open-pdf` event (see the single-instance / macOS `Opened` handlers).
 * - Update restart: restore the open file and page after an accepted update.
 *
 * Returns a cleanup function that detaches the event listener.
 */
export async function registerFileOpen(): Promise<() => void> {
  if (!isTauri()) return () => {};

  const resume = takeResumeDocument();

  let launchPath: string | null = null;
  try {
    launchPath = await invoke<string | null>('take_launch_file');
  } catch {
    // No launch file (normal), or the command is unavailable in dev.
  }

  try {
    if (launchPath) await openPath(launchPath);
    else if (resume) await openPath(resume.path, { page: resume.page, silent: true });
  } finally {
    // Launch handling has settled: from here on, an empty viewer really means
    // "no document", so the splash may show its open-a-document controls.
    useDocumentStore.getState().setBooted();
  }

  try {
    return await listen<string>('vnyl:open-pdf', (event) => {
      void openPath(event.payload);
    });
  } catch {
    return () => {};
  }
}
