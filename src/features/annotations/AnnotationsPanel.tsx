import { announce } from '@/a11y/announcer';
import { IconButton } from '@/components/common/IconButton';
import { useViewerStore } from '@/state/viewerStore';

import { useAnnotationStore } from './store';
import { ANNOTATION_LABELS } from './types';

/** Sidebar panel listing every annotation in the document. */
export function AnnotationsPanel() {
  const annotations = useAnnotationStore((s) => s.annotations);
  const remove = useAnnotationStore((s) => s.remove);
  const goToPage = useViewerStore((s) => s.goToPage);

  if (annotations.length === 0) {
    return (
      <p className="vnyl-sidebar__empty">
        No annotations yet. Select text in the document to highlight, underline or strike it
        through, or press <kbd>Ctrl/Cmd&nbsp;+&nbsp;Shift&nbsp;+&nbsp;H</kbd> to highlight.
      </p>
    );
  }

  const sorted = [...annotations].sort(
    (a, b) => a.pageNumber - b.pageNumber || a.createdAt - b.createdAt,
  );

  return (
    <ul className="vnyl-annotations-list">
      {sorted.map((annotation) => (
        <li key={annotation.id} className="vnyl-annotations-list__item">
          <button
            type="button"
            className="vnyl-annotations-list__jump"
            // Highlighted passages are usually longer than the row; without
            // this there is no way to tell two clipped highlights apart.
            title={annotation.text || undefined}
            onClick={() => goToPage(annotation.pageNumber)}
          >
            <span
              className="vnyl-annotations-list__swatch"
              style={{ backgroundColor: annotation.color }}
              aria-hidden="true"
            />
            <span className="vnyl-annotations-list__body">
              <span className="vnyl-annotations-list__kind">
                {ANNOTATION_LABELS[annotation.type]}
              </span>
              <span className="vnyl-annotations-list__text">{annotation.text || '(no text)'}</span>
            </span>
            <span className="vnyl-annotations-list__page">p.{annotation.pageNumber}</span>
          </button>
          <IconButton
            icon="trash"
            label={`Delete ${ANNOTATION_LABELS[annotation.type].toLowerCase()} on page ${annotation.pageNumber}`}
            onClick={() => {
              remove(annotation.id);
              announce(`${ANNOTATION_LABELS[annotation.type]} deleted`);
            }}
          />
        </li>
      ))}
    </ul>
  );
}
