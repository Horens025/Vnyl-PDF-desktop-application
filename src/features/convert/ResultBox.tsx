import { useRevealOnMount } from './useReveal';

/** The result card of a tool; scrolls itself into view so its buttons are not left below the fold. */
export function ResultBox({ children }: { children: React.ReactNode }) {
  const ref = useRevealOnMount();
  return (
    <div ref={ref} className="vnyl-tool-results" aria-live="polite">
      {children}
    </div>
  );
}
