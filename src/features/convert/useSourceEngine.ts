import { useEffect, useRef, useState } from 'react';

import { PdfJsEngine } from '@/core/pdf/PdfJsEngine';
import { getTabSource, useWorkspaceStore } from '@/features/workspace';

import { baseNameOf } from './naming';

interface SourceState {
  status: 'idle' | 'loading' | 'ready' | 'error';
  numPages: number;
  name: string;
  error: string;
}

const IDLE: SourceState = { status: 'idle', numPages: 0, name: '', error: '' };

/**
 * Load one open tab into a private engine so a tool can read its pages without
 * touching the document on screen. The engine is released when the tool closes
 * or the source changes.
 */
export function useSourceEngine(sourceId: string | null) {
  const engineRef = useRef<PdfJsEngine | null>(null);
  const [state, setState] = useState<SourceState>(IDLE);

  useEffect(() => {
    const engine = new PdfJsEngine();
    engineRef.current = engine;
    let live = true;
    void (async () => {
      const { tabs, activeId } = useWorkspaceStore.getState();
      const tab = tabs.find((t) => t.id === sourceId);
      if (!tab) {
        setState(IDLE);
        return;
      }
      setState({ ...IDLE, status: 'loading', name: baseNameOf(tab.name) });
      try {
        const source = await getTabSource(tab, tab.id === activeId);
        if (!source) throw new Error('That document can no longer be read');
        const info = await engine.loadDocument(source);
        if (live) {
          setState({
            status: 'ready',
            numPages: info.numPages,
            name: baseNameOf(tab.name),
            error: '',
          });
        }
      } catch (e) {
        if (live) {
          setState({
            ...IDLE,
            status: 'error',
            error: e instanceof Error ? e.message : 'Could not open the document',
          });
        }
      }
    })();
    return () => {
      live = false;
      engineRef.current = null;
      void engine.closeDocument();
    };
  }, [sourceId]);

  return { engineRef, ...state };
}
