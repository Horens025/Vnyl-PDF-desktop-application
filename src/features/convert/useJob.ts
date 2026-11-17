import { useEffect, useRef, useState } from 'react';

import { ConversionCancelled } from './engines';

/** Run a conversion with progress and cancel; keeps the outputs for download. */
export function useJob<R>() {
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [error, setError] = useState('');
  const abort = useRef<AbortController | null>(null);
  useEffect(() => () => abort.current?.abort(), []);

  const run = async (work: (signal: AbortSignal) => Promise<R>): Promise<R | null> => {
    abort.current?.abort();
    const controller = new AbortController();
    abort.current = controller;
    setError('');
    setProgress({ done: 0, total: 1 });
    try {
      return await work(controller.signal);
    } catch (e) {
      if (!(e instanceof ConversionCancelled)) {
        setError(e instanceof Error ? e.message : 'The conversion failed');
      }
      return null;
    } finally {
      setProgress(null);
    }
  };
  return { progress, setProgress, error, run, cancel: () => abort.current?.abort() };
}
