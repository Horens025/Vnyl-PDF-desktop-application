import { announce } from '@/a11y/announcer';
import { uid } from '@/core/id';
import { getEngine, type DocumentSource } from '@/core/pdf';
import { useAnnotationStore } from '@/features/annotations';
import { useEditStore } from '@/features/editing';
import { useOcrStore } from '@/features/ocr';
import { useSignatureStore } from '@/features/signatures';
import { closeDocument, loadSource } from '@/state/actions';
import { onBeforeDocumentLoad, onDocumentClosed, onDocumentLoaded } from '@/state/documentHooks';
import { useDocumentStore } from '@/state/documentStore';
import { DEFAULT_SCALE, useViewerStore } from '@/state/viewerStore';

import { neighbourAfterClose, useWorkspaceStore, type Tab } from './store';

/** Set while switchTab drives a load, so the loaded hook does not add a tab. */
let switchingTo: string | null = null;
/** The fingerprint the engine reported for the last load; null if none landed. */
let landedFingerprint: string | null = null;

/** Snapshot the outgoing document's viewer state, and its bytes if it was edited. */
async function captureActive(): Promise<void> {
  const ws = useWorkspaceStore.getState();
  const doc = useDocumentStore.getState();
  if (!ws.activeId || doc.status !== 'ready') return;
  const viewer = useViewerStore.getState();
  const patch: Partial<Tab> = {
    page: viewer.currentPage,
    scale: viewer.scale,
    fitMode: viewer.fitMode,
  };
  const engine = getEngine();
  if (doc.docVersion > 0 || engine.getPendingEditCount() > 0) {
    try {
      patch.data = await engine.saveDocument();
      patch.edited = true;
    } catch {
      /* keep the previous bytes: the tab reopens as last opened rather than not at all */
    }
  }
  ws.patch(ws.activeId, patch);
}

function sourceOf(tab: Tab): DocumentSource | null {
  if (tab.data) {
    return { kind: 'bytes', data: tab.data.slice(), name: tab.name, path: tab.path ?? undefined };
  }
  return tab.url ? { kind: 'url', url: tab.url, name: tab.name } : null;
}

/** Show another open document. Returns false when it could not be switched to. */
export async function switchTab(id: string): Promise<boolean> {
  const ws = useWorkspaceStore.getState();
  const tab = ws.tabs.find((t) => t.id === id);
  if (!tab || id === ws.activeId) return false;
  const source = sourceOf(tab);
  if (!source) return false;

  switchingTo = id;
  landedFingerprint = null;
  try {
    await loadSource(source);
  } finally {
    switchingTo = null;
  }
  // loadSource declines (with a toast) when another operation holds the document.
  if (landedFingerprint === null) return false;

  if (landedFingerprint !== tab.fingerprint) {
    // Reloaded from edited bytes: its fingerprint differs from the one the
    // document's markups, signatures and edits were saved under. Point the
    // stores back at the original, exactly as reloadEditedBytes would have.
    useDocumentStore.setState((s) =>
      s.info ? { info: { ...s.info, fingerprint: tab.fingerprint } } : {},
    );
    useAnnotationStore.getState().loadForDocument(tab.fingerprint);
    useSignatureStore.getState().loadForDocument(tab.fingerprint);
    useEditStore.getState().loadForDocument(tab.fingerprint);
    useOcrStore.getState().loadForDocument(tab.fingerprint);
  }
  if (tab.edited) useDocumentStore.getState().bumpDocVersion();

  const viewer = useViewerStore.getState();
  if (tab.fitMode === 'custom') viewer.setScale(tab.scale);
  else viewer.setFitMode(tab.fitMode);
  if (tab.page > 1) viewer.goToPage(tab.page);
  useWorkspaceStore.getState().setActive(id);
  announce(`Switched to ${tab.name}`);
  return true;
}

/** Close a tab. The active one goes through closeDocument, which shows a neighbour. */
export async function closeTab(id: string): Promise<void> {
  const { activeId } = useWorkspaceStore.getState();
  if (id === activeId) {
    await closeDocument();
  } else {
    useWorkspaceStore.getState().remove(id);
  }
}

export function cycleTab(step: 1 | -1): void {
  const { tabs, activeId } = useWorkspaceStore.getState();
  if (tabs.length < 2) return;
  const i = tabs.findIndex((t) => t.id === activeId);
  const next = tabs[(i + step + tabs.length) % tabs.length];
  void switchTab(next.id);
}

let registered = false;

/** Wire the workspace to document loads and closes. Idempotent. */
export function registerWorkspace(): void {
  if (registered) return;
  registered = true;

  onBeforeDocumentLoad(captureActive);

  onDocumentLoaded((e) => {
    landedFingerprint = e.info.fingerprint;
    if (switchingTo) return; // switchTab owns the bookkeeping
    const ws = useWorkspaceStore.getState();
    const existing = ws.tabs.find((t) => t.fingerprint === e.info.fingerprint);
    const tab: Tab = {
      id: existing?.id ?? uid('tab'),
      name: e.info.name,
      fingerprint: e.info.fingerprint,
      data: e.bytes,
      url: e.url,
      path: e.path,
      edited: false,
      page: 1,
      scale: DEFAULT_SCALE,
      fitMode: 'custom',
    };
    ws.upsert(tab);
    ws.setActive(tab.id);
  });

  onDocumentClosed(() => {
    const ws = useWorkspaceStore.getState();
    const closing = ws.activeId;
    if (!closing) return;
    const next = neighbourAfterClose(ws.tabs, closing);
    ws.remove(closing);
    // closeDocument fires this while still holding the document lock, and
    // loadSource would be refused under it: switch once the lock is released.
    if (next) setTimeout(() => void switchTab(next.id), 0);
  });
}
