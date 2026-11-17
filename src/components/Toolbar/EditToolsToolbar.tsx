import { useMemo } from 'react';

import { commandRegistry } from '@/commands';
import { useNotesUi, useMarkupUi, type TextMarkupType } from '@/features/annotations';
import { usePlacementStore } from '@/features/placement';
import { usePageOpsStore } from '@/features/pageops/store';
import { useTextEditStore } from '@/features/textedit';
import { useDocumentStore } from '@/state/documentStore';
import { useViewerStore } from '@/state/viewerStore';

import { ToolbarAction } from './ToolbarAction';
import { ToolbarOverflow } from './ToolbarOverflow';
import type { OverflowTool } from './toolbarTools';

const run = (id: string) => commandRegistry.execute(id);

const MARKUP: { type: TextMarkupType; icon: 'highlighter' | 'underline' | 'strikethrough' | 'squiggly'; label: string }[] = [
  { type: 'highlight', icon: 'highlighter', label: 'Highlight' },
  { type: 'underline', icon: 'underline', label: 'Underline' },
  { type: 'strikeout', icon: 'strikethrough', label: 'Strikeout' },
  { type: 'squiggly', icon: 'squiggly', label: 'Squiggly' },
];

/** The contextual annotation/edit ribbon opened by the main Edit action. */
export function EditToolsToolbar() {
  const hasDoc = useDocumentStore((s) => s.status === 'ready');
  const handMode = useViewerStore((s) => s.handMode);
  const setHandMode = useViewerStore((s) => s.setHandMode);
  const close = useViewerStore((s) => s.setEditToolbarOpen);
  const markupTool = useMarkupUi((s) => s.tool);
  const setMarkupTool = useMarkupUi((s) => s.setTool);
  const addingNote = useNotesUi((s) => s.adding);
  const textEditActive = useTextEditStore((s) => s.active);
  const textUndoCount = useTextEditStore((s) => s.undoStack.length);
  const pageUndoCount = usePageOpsStore((s) => s.undoStack.length);

  const moreTools = useMemo<OverflowTool[]>(() => [
    {
      id: 'edit-text', icon: 'type', label: 'Edit PDF text', menuLabel: 'Edit PDF text',
      active: textEditActive, disabled: !hasDoc, onClick: () => run('textedit.toggle'),
    },
    { id: 'add-image', icon: 'image', label: 'Add image', menuLabel: 'Add image', disabled: !hasDoc, onClick: () => run('edit.addImage') },
  ], [hasDoc, textEditActive]);

  const closeRibbon = () => {
    setMarkupTool(null);
    useNotesUi.getState().setAdding(false);
    usePlacementStore.getState().cancel();
    setHandMode(false);
    close(false);
  };

  return (
    <header className="vnyl-toolbar vnyl-edit-toolbar" role="toolbar" aria-label="Edit tools">
      <div className="vnyl-edit-toolbar__scroll">
        <ToolbarAction icon="x" label="Close edit tools" onClick={closeRibbon} />
        <span className="vnyl-toolbar__divider" aria-hidden="true" />
        <ToolbarAction
          icon="note" label="Sticky note" disabled={!hasDoc} active={addingNote}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => { setMarkupTool(null); void run('annotate.addNote'); }}
        />
        {MARKUP.map(({ type, icon, label }) => (
          <ToolbarAction
            key={type} icon={icon} label={label} disabled={!hasDoc} active={markupTool === type}
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => { setMarkupTool(markupTool === type ? null : type); useNotesUi.getState().setAdding(false); }}
          />
        ))}
        <ToolbarAction icon="signature" label="Add signature" disabled={!hasDoc} onClick={() => void run('sign.addSignature')} />
        <ToolbarAction icon="hand" label="Pan" disabled={!hasDoc} active={handMode} onClick={() => setHandMode(!handMode)} />
        <ToolbarOverflow items={moreTools} visibleCount={0} triggerLabel="More edit tools" />
        <span className="vnyl-toolbar__divider" aria-hidden="true" />
        <ToolbarAction
          icon="undo" label="Undo" disabled={!hasDoc || (textEditActive ? textUndoCount === 0 : pageUndoCount === 0)}
          onClick={() => void run(textEditActive ? 'textedit.undo' : 'pageops.undo')}
        />
      </div>
    </header>
  );
}
