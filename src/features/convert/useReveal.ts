import { useEffect, useRef } from 'react';

/** Scroll a freshly produced result into view, so its Download buttons are not hidden below the fold. */
export function useRevealOnMount() {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
  }, []);
  return ref;
}
