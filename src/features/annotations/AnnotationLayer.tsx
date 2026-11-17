import { useMemo, type CSSProperties } from 'react';

import { useAnnotationStore } from './store';
import { isTextMarkup } from './types';

/**
 * Renders a page's text markup (highlight, underline, strikethrough, squiggly) as an overlay above the text layer. Normalized
 * rects are scaled to the current page box, so highlights track zoom without
 * being re-computed.
 */
export function AnnotationLayer({ pageNumber }: { pageNumber: number }) {
  const all = useAnnotationStore((s) => s.annotations);
  const annotations = useMemo(
    () => all.filter((a) => a.pageNumber === pageNumber && isTextMarkup(a.type)),
    [all, pageNumber],
  );

  if (annotations.length === 0) return null;

  return (
    <div className="vnyl-annotation-layer" aria-hidden="true">
      {annotations.flatMap((annotation) =>
        annotation.rects.map((rect, index) => (
          <div
            key={`${annotation.id}-${index}`}
            className={`vnyl-annotation vnyl-annotation--${annotation.type}`}
            title={annotation.text}
            style={
              {
                left: `${rect.x * 100}%`,
                top: `${rect.y * 100}%`,
                width: `${rect.width * 100}%`,
                height: `${rect.height * 100}%`,
                '--markup-color': annotation.color,
              } as CSSProperties
            }
          />
        )),
      )}
    </div>
  );
}
