import { useState } from 'react';

import { Button, Modal } from '@/components/common';
import { isTauri } from '@/core/document/openDocument';
import { checkForUpdates } from '@/features/updates';
import { useViewerStore } from '@/state/viewerStore';

/** Format the ISO build timestamp for display; fall back to the raw value. */
function formatBuildDate(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/** About dialog: Vnyl version and build information. */
export function AboutModal() {
  const open = useViewerStore((s) => s.aboutModalOpen);
  const setOpen = useViewerStore((s) => s.setAboutModalOpen);
  const [checking, setChecking] = useState(false);

  const close = () => setOpen(false);
  const onCheck = async () => {
    setChecking(true);
    try {
      await checkForUpdates(false);
    } finally {
      setChecking(false);
    }
  };

  // Rounded to 3 dp so fractional Windows scaling (1.25, 1.5) reads clearly.
  const dpr = Math.round((window.devicePixelRatio || 1) * 1000) / 1000;
  const rows: Array<{ label: string; value: string }> = [
    { label: 'Version', value: __APP_VERSION__ },
    { label: 'Commit', value: __COMMIT_HASH__ },
    { label: 'Built', value: formatBuildDate(__BUILD_DATE__) },
    { label: 'Pixel ratio', value: `${dpr}× (${window.innerWidth}×${window.innerHeight})` },
  ];

  return (
    <Modal open={open} title="About Vnyl" onDismiss={close} size="narrow">
      <div className="vnyl-modal__body">
        <dl className="vnyl-about">
          {rows.map((r) => (
            <div key={r.label} className="vnyl-about__row">
              <dt className="vnyl-about__label">{r.label}</dt>
              <dd className="vnyl-about__value">{r.value}</dd>
            </div>
          ))}
        </dl>
      </div>

      <div className="vnyl-modal__footer">
        {isTauri() && (
          <Button onClick={onCheck} disabled={checking}>
            {checking ? 'Checking…' : 'Check for updates'}
          </Button>
        )}
        <Button variant="primary" onClick={close}>
          Close
        </Button>
      </div>
    </Modal>
  );
}
