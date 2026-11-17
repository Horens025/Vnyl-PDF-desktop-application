import { exportDocument } from '@/features/export';
import { getTabSource, useWorkspaceStore } from '@/features/workspace';
import { withDocumentMutation } from '@/state/documentMutationStore';

/**
 * The bytes of an open document for a tool that rebuilds it (split, extract,
 * compress). The document on screen is exported the way Save does, so your
 * markups, signatures, text edits and filled forms come along; any other open
 * tab is just its own bytes.
 */
export async function loadSourceBytes(tabId: string): Promise<Uint8Array> {
  const { tabs, activeId } = useWorkspaceStore.getState();
  const tab = tabs.find((t) => t.id === tabId);
  if (!tab) throw new Error('That document is no longer open.');

  if (tab.id === activeId) {
    return withDocumentMutation(
      { owner: 'export', scope: 'content' },
      () => exportDocument(),
      () => {
        throw new Error('Another change to the document is in progress. Try again in a moment.');
      },
    );
  }
  const source = await getTabSource(tab, false);
  if (!source) throw new Error('That document can no longer be read.');
  if (source.kind === 'bytes') return source.data;
  const response = await fetch(source.url);
  if (!response.ok) throw new Error('That document could not be downloaded again.');
  return new Uint8Array(await response.arrayBuffer());
}
