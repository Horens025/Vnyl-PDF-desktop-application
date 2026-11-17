import { useMemo, useState } from 'react';

import { useWorkspaceStore } from '@/features/workspace';

import { parsePageRange } from './pageRange';
import { useSourceEngine } from './useSourceEngine';

/** Source picker + page range, shared by every panel that converts a document. */
export function useSourceAndPages() {
  const tabs = useWorkspaceStore((s) => s.tabs);
  const activeId = useWorkspaceStore((s) => s.activeId);
  const [sourceId, setSourceId] = useState<string | null>(activeId ?? tabs[0]?.id ?? null);
  const [pageText, setPageText] = useState('');
  const source = useSourceEngine(sourceId);
  const range = useMemo(
    () => (source.status === 'ready' ? parsePageRange(pageText, source.numPages) : null),
    [pageText, source.status, source.numPages],
  );
  return { tabs, sourceId, setSourceId, pageText, setPageText, source, range };
}
