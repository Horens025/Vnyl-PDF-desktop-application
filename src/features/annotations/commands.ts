import { announce } from '@/a11y/announcer';
import { commandRegistry } from '@/commands';
import { useDocumentStore } from '@/state/documentStore';

import { NOTE_COLOR, useNotesUi } from './notesUi';
import { useAnnotationStore } from './store';
import { useMarkupUi } from './markupUi';
import {
  ANNOTATION_LABELS,
  MARKUP_SWATCHES,
  markupColor,
  type NormalizedRect,
  type TextMarkupType,
} from './types';

/**
 * Anchor a comment to the current text selection: stores the selected text (what
 * the note refers to) and its line rects (to underline it). Returns false if
 * there is no usable selection, so the caller can fall back to point placement.
 */
export function addNoteFromSelection(color: string = NOTE_COLOR): boolean {
  const selection = window.getSelection();
  if (!selection || selection.isCollapsed || selection.rangeCount === 0) return false;

  const range = selection.getRangeAt(0);
  const startNode = range.startContainer;
  const startEl = startNode instanceof HTMLElement ? startNode : startNode.parentElement;
  const pageEl = startEl?.closest<HTMLElement>('.vnyl-page');
  if (!pageEl) return false;

  const pageNumber = Number(pageEl.dataset.pageNumber);
  if (!Number.isFinite(pageNumber) || pageNumber < 1) return false;

  const pageRect = pageEl.getBoundingClientRect();
  const rects: NormalizedRect[] = Array.from(range.getClientRects())
    .filter((r) => r.width > 0 && r.height > 0)
    .map((r) => ({
      x: (r.left - pageRect.left) / pageRect.width,
      y: (r.top - pageRect.top) / pageRect.height,
      width: r.width / pageRect.width,
      height: r.height / pageRect.height,
    }));
  if (rects.length === 0) return false;

  const text = selection.toString().replace(/\s+/g, ' ').trim();
  // Pin sits at the start of the referenced text.
  const anchor = { x: rects[0].x, y: rects[0].y };
  const note = useAnnotationStore.getState().addNote(pageNumber, anchor, text, color, rects);
  selection.removeAllRanges();

  const ui = useNotesUi.getState();
  ui.setAdding(false);
  ui.setActive(note.id);
  announce('Comment added on the selected text');
  return true;
}

/** The color a new markup of this type gets from the picker's current choice. */
export function currentMarkupColor(type: TextMarkupType): string {
  const { swatch, opacity } = useMarkupUi.getState();
  return markupColor(type, MARKUP_SWATCHES[swatch].rgb, opacity);
}

/**
 * Create a text-markup annotation (highlight, underline, strikethrough or
 * squiggly) from the current text selection. Client rects from the selection
 * Range are converted to page-relative fractions so it is zoom-independent.
 * Returns whether one was created.
 */
export function addMarkupFromSelection(type: TextMarkupType, color?: string): boolean {
  const label = ANNOTATION_LABELS[type];
  const selection = window.getSelection();
  if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
    announce(`Select some text first, then add a ${label.toLowerCase()}`, true);
    return false;
  }

  const range = selection.getRangeAt(0);
  const startNode = range.startContainer;
  const startEl = startNode instanceof HTMLElement ? startNode : startNode.parentElement;
  const pageEl = startEl?.closest<HTMLElement>('.vnyl-page');
  if (!pageEl) return false;

  const pageNumber = Number(pageEl.dataset.pageNumber);
  if (!Number.isFinite(pageNumber) || pageNumber < 1) return false;

  const pageRect = pageEl.getBoundingClientRect();
  const rects: NormalizedRect[] = Array.from(range.getClientRects())
    .filter((r) => r.width > 0 && r.height > 0)
    .map((r) => ({
      x: (r.left - pageRect.left) / pageRect.width,
      y: (r.top - pageRect.top) / pageRect.height,
      width: r.width / pageRect.width,
      height: r.height / pageRect.height,
    }));
  if (rects.length === 0) return false;

  const text = selection.toString().replace(/\s+/g, ' ').trim();
  useAnnotationStore
    .getState()
    .addMarkup(type, pageNumber, rects, text, color ?? currentMarkupColor(type));
  selection.removeAllRanges();
  announce(`${label} added`);
  return true;
}

/** Create a highlight from the current text selection. */
export function addHighlightFromSelection(color?: string): void {
  addMarkupFromSelection('highlight', color);
}

let registered = false;

/** Register annotation commands. Idempotent. */
export function registerAnnotationCommands(): void {
  if (registered) return;
  registered = true;

  commandRegistry.register({
    id: 'annotate.highlight',
    title: 'Highlight selection',
    category: 'Annotate',
    keybinding: 'Mod+Shift+H',
    when: () => useDocumentStore.getState().status === 'ready',
    run: () => addHighlightFromSelection(),
  });

  const markupCommands: [TextMarkupType, string, string | undefined][] = [
    ['underline', 'Underline selection', 'Mod+Shift+U'],
    ['strikeout', 'Strikethrough selection', 'Mod+Shift+X'],
    ['squiggly', 'Squiggly underline selection', undefined],
  ];
  for (const [type, title, keybinding] of markupCommands) {
    commandRegistry.register({
      id: `annotate.${type}`,
      title,
      category: 'Annotate',
      keybinding,
      when: () => useDocumentStore.getState().status === 'ready',
      run: () => void addMarkupFromSelection(type),
    });
  }

  commandRegistry.register({
    id: 'annotate.toolbar',
    title: 'Toggle markup toolbar',
    category: 'Annotate',
    keybinding: 'Mod+Shift+A',
    when: () => useDocumentStore.getState().status === 'ready',
    run: () => {
      useMarkupUi.getState().toggleToolbar();
      announce(
        useMarkupUi.getState().toolbarOpen
          ? 'Markup toolbar open. Pick a tool, then select text'
          : 'Markup toolbar closed',
      );
    },
  });

  commandRegistry.register({
    id: 'annotate.addNote',
    title: 'Add a sticky note',
    category: 'Annotate',
    keybinding: 'Mod+Shift+M',
    when: () => useDocumentStore.getState().status === 'ready',
    run: () => {
      // If text is selected, anchor the comment to it. Otherwise enter
      // point-placement mode (for figures/images with no selectable text).
      if (addNoteFromSelection()) return;
      useNotesUi.getState().toggleAdding();
      const adding = useNotesUi.getState().adding;
      announce(
        adding
          ? 'Select text to comment on it, or click a spot on the page to place a note'
          : 'Note placement cancelled',
      );
    },
  });
}
