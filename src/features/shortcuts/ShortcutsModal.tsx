import { commandRegistry } from '@/commands';
import { Button, Modal } from '@/components/common';

import { groupShortcuts } from './format';
import { useShortcutsStore } from './store';

const isMac = () => /mac|iphone|ipad/i.test(navigator.platform || navigator.userAgent);

/** Every keyboard shortcut, read live from the command registry so it can never be out of date. */
export function ShortcutsModal() {
  const open = useShortcutsStore((s) => s.open);
  const close = useShortcutsStore((s) => s.close);
  const groups = open ? groupShortcuts(commandRegistry.all(), isMac()) : [];

  return (
    <Modal open={open} title="Keyboard shortcuts" onDismiss={close} size="wide">
      <div
        className="vnyl-modal__body vnyl-shortcuts"
        // A scrolling area must be reachable by keyboard, or its lower rows are out of reach.
        // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
        tabIndex={0}
        role="region"
        aria-label="Shortcut list"
      >
        {groups.map((g) => (
          <section key={g.category} aria-labelledby={`sc-${g.category}`}>
            <h3 id={`sc-${g.category}`} className="vnyl-tools__heading">
              {g.category}
            </h3>
            <dl>
              {g.rows.map((r) => (
                <div key={r.id} className="vnyl-shortcuts__row">
                  <dt>{r.title}</dt>
                  <dd>
                    {r.keys.map((k, i) => (
                      <kbd key={i}>{k}</kbd>
                    ))}
                  </dd>
                </div>
              ))}
            </dl>
          </section>
        ))}
      </div>
      <div className="vnyl-modal__footer">
        <Button variant="primary" onClick={close}>
          Done
        </Button>
      </div>
    </Modal>
  );
}
