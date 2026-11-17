import { useState } from 'react';

import { commandRegistry } from '@/commands';
import { Button, Icon, Modal } from '@/components/common';

import { filterCatalog, findTool, type ToolDef } from './catalog';
import { ImagesToPdfPanel } from './ImagesToPdfPanel';
import { PdfToImagesPanel, PdfToTextPanel } from './panels';
import { CompressPanel, ExtractPanel, SplitPanel } from './PdfToolPanels';
import { useHubStore } from './store';
import { useWorkspaceStore } from '@/features/workspace';

function Card({ tool }: { tool: ToolDef }) {
  const setView = useHubStore((s) => s.setView);
  const close = useHubStore((s) => s.close);
  const hasDocument = useWorkspaceStore((s) => s.tabs.length > 0);
  const command = tool.command ? commandRegistry.get(tool.command) : undefined;

  let blocked = '';
  if (tool.needsDocument && !hasDocument) blocked = 'Open a PDF first.';
  else if (command?.when && !command.when()) blocked = 'Not available right now.';

  const activate = () => {
    if (blocked) return;
    if (tool.panel) {
      setView(tool.panel);
    } else if (tool.command) {
      close();
      // After the dialog unmounts, so focus returns before the next one opens.
      window.setTimeout(() => void commandRegistry.execute(tool.command!), 0);
    }
  };

  return (
    <button
      type="button"
      className={`vnyl-tool-card${blocked ? ' is-blocked' : ''}`}
      aria-disabled={blocked ? true : undefined}
      title={blocked || undefined}
      onClick={activate}
    >
      <span className="vnyl-tool-card__icon" aria-hidden="true">
        <Icon name={tool.icon} size={20} />
      </span>
      <span className="vnyl-tool-card__title">{tool.title}</span>
      <span className="vnyl-tool-card__desc">{tool.description}</span>
    </button>
  );
}

/** The Xodo-style tool gallery, and the panels the converters open into. */
export function ToolsHub() {
  const open = useHubStore((s) => s.open);
  const view = useHubStore((s) => s.view);
  const setView = useHubStore((s) => s.setView);
  const close = useHubStore((s) => s.close);
  const [query, setQuery] = useState('');
  const groups = filterCatalog(query);
  const tool = view === 'hub' ? null : findTool(view);

  return (
    <Modal open={open} title={tool ? tool.title : 'Tools'} onDismiss={close} size="wide">
      <div className="vnyl-modal__body vnyl-tools">
        {tool ? (
          <>
            <div>
              <Button onClick={() => setView('hub')}>← All tools</Button>
            </div>
            {view === 'pdf-to-images' && <PdfToImagesPanel />}
            {view === 'pdf-to-text' && <PdfToTextPanel />}
            {view === 'images-to-pdf' && <ImagesToPdfPanel />}
            {view === 'split-pdf' && <SplitPanel />}
            {view === 'extract-pages' && <ExtractPanel />}
            {view === 'compress-pdf' && <CompressPanel />}
          </>
        ) : (
          <>
            <input
              type="search"
              className="vnyl-tools__search"
              placeholder="Search tools"
              aria-label="Search tools"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            {groups.length === 0 && (
              <p className="vnyl-compare__note">No tool matches “{query}”.</p>
            )}
            {groups.map((c) => (
              <section key={c.id} aria-labelledby={`tools-${c.id}`}>
                <h3 id={`tools-${c.id}`} className="vnyl-tools__heading">
                  {c.title}
                </h3>
                <div className="vnyl-tools__grid">
                  {c.tools.map((t) => (
                    <Card key={t.id} tool={t} />
                  ))}
                </div>
              </section>
            ))}
          </>
        )}
      </div>
      <div className="vnyl-modal__footer">
        <Button variant="primary" onClick={close}>
          Done
        </Button>
      </div>
    </Modal>
  );
}
