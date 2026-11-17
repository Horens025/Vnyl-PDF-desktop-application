import { useEffect, useId, useRef, useState } from 'react';

import { announce } from '@/a11y/announcer';
import { rotateSelection } from '@/features/pageops/operations';
import { usePageOpsStore } from '@/features/pageops/store';
import { Icon, IconButton } from '@/components/common';
import type { IconName } from '@/components/common';
import {
  GLASS_LOOK_LABELS,
  STYLE_LABELS,
  THEME_LABELS,
  useThemeStore,
  type GlassLook,
  type UiStyle,
} from '@/theme/themeStore';
import { useViewerStore, type PageLayoutMode } from '@/state/viewerStore';

const STYLES: UiStyle[] = ['glass', 'classic'];
const LOOKS: GlassLook[] = ['frosted', 'clear'];
const LAYOUTS: { value: PageLayoutMode; label: string; icon: IconName }[] = [
  { value: 'single', label: 'Single', icon: 'file' },
  { value: 'double', label: 'Double', icon: 'split-columns' },
  { value: 'cover', label: 'Cover', icon: 'book' },
  { value: 'reflow', label: 'Reflow', icon: 'type' },
];

/**
 * Reader view controls and the app's independent theme controls.
 */
export function AppearanceMenu({ showLabel = false }: { showLabel?: boolean }) {
  const theme = useThemeStore((s) => s.theme);
  const resolvedTheme = useThemeStore((s) => s.resolvedTheme);
  const setTheme = useThemeStore((s) => s.setTheme);
  const pdfColorMode = useThemeStore((s) => s.pdfColorMode);
  const setPdfColorMode = useThemeStore((s) => s.setPdfColorMode);
  const style = useThemeStore((s) => s.style);
  const setStyle = useThemeStore((s) => s.setStyle);
  const styleGroupId = useId();
  const glassLook = useThemeStore((s) => s.glassLook);
  const setGlassLook = useThemeStore((s) => s.setGlassLook);
  const pageLayout = useViewerStore((s) => s.pageLayout);
  const setPageLayout = useViewerStore((s) => s.setPageLayout);
  const verticalScrolling = useViewerStore((s) => s.verticalScrolling);
  const setVerticalScrolling = useViewerStore((s) => s.setVerticalScrolling);
  const cropPreview = useViewerStore((s) => s.cropPreview);
  const setCropPreview = useViewerStore((s) => s.setCropPreview);
  const numPages = useViewerStore((s) => s.numPages);
  const setSidebarTab = useViewerStore((s) => s.setSidebarTab);
  const [fullScreen, setFullScreen] = useState(Boolean(document.fullscreenElement));
  const lookGroupId = useId();
  const [open, setOpen] = useState(false);
  const [cropOpen, setCropOpen] = useState(false);
  const [showExtraPdfColors, setShowExtraPdfColors] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  // The two captions name their own radio set through aria-labelledby, so the
  // ids have to be unique per instance rather than hard-coded.
  const modeGroupId = useId();
  const schemeGroupId = useId();
  const colorModeGroupId = useId();

  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('pointerdown', onDown);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('pointerdown', onDown);
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  useEffect(() => {
    const update = () => setFullScreen(Boolean(document.fullscreenElement));
    document.addEventListener('fullscreenchange', update);
    return () => document.removeEventListener('fullscreenchange', update);
  }, []);

  useEffect(() => {
    if (!cropOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setCropOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [cropOpen]);

  const rotateAllPages = async () => {
    const ops = usePageOpsStore.getState();
    const previous = new Set(ops.selection);
    ops.selectAll(numPages);
    await rotateSelection(1);
    usePageOpsStore.getState().clearSelection();
    for (const page of previous) usePageOpsStore.getState().toggle(page);
  };

  const toggleFullScreen = async () => {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await document.querySelector('.vnyl-app')?.requestFullscreen();
    } catch {
      announce('Full screen is unavailable in this window', true);
    }
  };

  const modeLabel = THEME_LABELS[theme];

  return (
    <div className="vnyl-dropdown" ref={ref}>
      <IconButton
        // The trigger reports the mode in effect rather than the one a click
        // would switch to: it opens a menu now, so it is a state, not a verb.
        // Ctrl/Cmd + Shift + L still flips light/dark without opening anything,
        // so the hint stays on the control that owns appearance in the bar.
        icon={resolvedTheme === 'dark' ? 'moon' : 'sun'}
        label={`Appearance: ${modeLabel} (Ctrl/Cmd + Shift + L toggles light / dark)`}
        active={open}
        aria-haspopup="menu"
        aria-expanded={open}
        // IconButton emits aria-pressed from `active`, which would announce a
        // menu trigger as a toggle button whose pressed state tracks whether
        // the popup is showing. aria-expanded already says that, so drop it
        // here (rest wins over IconButton's own attribute) and keep `active`
        // purely for the pressed-looking styling.
        aria-pressed={undefined}
        onClick={() => setOpen((o) => !o)}
        className={showLabel ? 'vnyl-toolbar__view-mode' : undefined}
      >
        {showLabel && <span>View mode</span>}
      </IconButton>
      {open && (
        <div className="vnyl-dropdown__menu vnyl-dropdown__menu--view" role="menu" aria-label="View mode">
          <div role="group" aria-label="PDF page layout">
            <div className="vnyl-view-layouts">
              {LAYOUTS.map((layout) => (
                <button
                  key={layout.value}
                  type="button"
                  role="menuitemradio"
                  aria-checked={pageLayout === layout.value}
                  className={`vnyl-view-layout${pageLayout === layout.value ? ' is-active' : ''}`}
                  onClick={() => {
                    setPageLayout(layout.value);
                    announce(`${layout.label} PDF view`);
                  }}
                >
                  <Icon name={layout.icon} size={18} />
                  <span>{layout.label}</span>
                </button>
              ))}
            </div>
          </div>

          <button
            type="button"
            role="menuitemcheckbox"
            aria-checked={verticalScrolling}
            className="vnyl-dropdown__item vnyl-view-setting"
            onClick={() => setVerticalScrolling(!verticalScrolling)}
          >
            <Icon name="auto-scroll" size={16} />
            <span className="vnyl-dropdown__label">Vertical scrolling</span>
            <span className="vnyl-view-setting__state">{verticalScrolling ? 'On' : 'Off'}</span>
            <span className={`vnyl-toggle${verticalScrolling ? ' is-on' : ''}`} aria-hidden="true" />
          </button>

          <div className="vnyl-dropdown__sep" role="separator" />

          <div role="group" aria-labelledby={colorModeGroupId}>
            <div className="vnyl-dropdown__item vnyl-view-setting" id={colorModeGroupId}>
              <Icon name="contrast" size={16} />
              <span className="vnyl-dropdown__label">Color mode</span>
              <div className="vnyl-color-modes">
                {(['light', 'dark', 'sepia'] as const).map((mode) => (
                  <button
                    key={mode}
                    type="button"
                    className={`vnyl-color-mode vnyl-color-mode--${mode}${pdfColorMode === mode ? ' is-active' : ''}`}
                    aria-label={`${mode[0].toUpperCase()}${mode.slice(1)} PDF color mode`}
                    aria-pressed={pdfColorMode === mode}
                    title={`${mode[0].toUpperCase()}${mode.slice(1)}`}
                    onClick={() => {
                      setPdfColorMode(mode);
                      announce(`${mode[0].toUpperCase()}${mode.slice(1)} PDF color mode`);
                    }}
                  >{mode === 'light' ? <Icon name="sun" size={15} /> : mode === 'dark' ? <Icon name="moon" size={15} /> : 'A'}</button>
                ))}
                <button
                  type="button"
                  className={`vnyl-color-mode vnyl-color-mode--more${showExtraPdfColors ? ' is-active' : ''}`}
                  aria-label="More PDF color modes"
                  aria-expanded={showExtraPdfColors}
                  title="More color modes"
                  onClick={() => setShowExtraPdfColors((value) => !value)}
                ><Icon name="more" size={15} /></button>
              </div>
            </div>
          </div>

          {showExtraPdfColors && (
            <div role="group" aria-labelledby={schemeGroupId} className="vnyl-view-extra-colors">
              <span className="vnyl-dropdown__group-label" id={schemeGroupId}>More PDF colors</span>
              {(['green', 'amber'] as const).map((mode) => (
                <button
                  key={mode}
                  type="button"
                  role="menuitemradio"
                  aria-checked={pdfColorMode === mode}
                  className={`vnyl-dropdown__item${pdfColorMode === mode ? ' is-active' : ''}`}
                  onClick={() => {
                    setPdfColorMode(mode);
                    announce(`${mode === 'green' ? 'Green' : 'Amber'} PDF color mode`);
                  }}
                >
                  <span className={`vnyl-swatch vnyl-swatch--${mode}`} aria-hidden="true">A</span>
                  <span className="vnyl-dropdown__label">{mode === 'green' ? 'Green' : 'Amber'}</span>
                  {pdfColorMode === mode && <Icon name="check" size={16} />}
                </button>
              ))}
            </div>
          )}

          <div className="vnyl-dropdown__sep" role="separator" />

          <button type="button" role="menuitem" className="vnyl-dropdown__item" disabled={!numPages} onClick={() => void rotateAllPages()}>
            <Icon name="rotate-right" size={16} /><span className="vnyl-dropdown__label">Rotate pages clockwise</span>
          </button>
          <button type="button" role="menuitem" className="vnyl-dropdown__item" onClick={() => { setSidebarTab('thumbnails'); setOpen(false); }}>
            <Icon name="sidebar" size={16} /><span className="vnyl-dropdown__label">Thumbnails</span>
          </button>
          <button type="button" role="menuitemcheckbox" aria-checked={cropPreview > 0} className="vnyl-dropdown__item" onClick={() => { setOpen(false); setCropOpen(true); }}>
            <Icon name="crop" size={16} /><span className="vnyl-dropdown__label">Crop pages</span>
            {cropPreview > 0 && <Icon name="check" size={16} />}
          </button>
          <button type="button" role="menuitem" className="vnyl-dropdown__item" onClick={() => void toggleFullScreen()}>
            <Icon name="maximize" size={16} /><span className="vnyl-dropdown__label">{fullScreen ? 'Exit full screen' : 'Full screen'}</span>
          </button>

          <div className="vnyl-dropdown__sep" role="separator" />

          <div role="group" aria-labelledby={modeGroupId}>
            <div className="vnyl-dropdown__group-label" id={modeGroupId}>
              Theme
            </div>
            <button
              type="button"
              role="menuitemcheckbox"
              aria-checked={resolvedTheme === 'dark'}
              className="vnyl-dropdown__item vnyl-view-setting"
              onClick={() => {
                const next = resolvedTheme === 'dark' ? 'light' : 'dark';
                setTheme(next);
                announce(`App theme ${next}`);
              }}
            >
              <Icon name={resolvedTheme === 'dark' ? 'moon' : 'sun'} size={16} />
              <span className="vnyl-dropdown__label">Theme</span>
              <span className="vnyl-view-setting__state">{resolvedTheme === 'dark' ? 'Dark' : 'Light'}</span>
              <span className={`vnyl-toggle${resolvedTheme === 'dark' ? ' is-on' : ''}`} aria-hidden="true" />
            </button>
            <button
              type="button"
              role="menuitemradio"
              aria-checked={theme === 'system'}
              className="vnyl-dropdown__item"
              onClick={() => {
                setTheme('system');
                announce('App theme set to match system');
              }}
            >
              <Icon name="contrast" size={16} />
              <span className="vnyl-dropdown__label">Match system</span>
              {theme === 'system' && <Icon name="check" size={16} />}
            </button>
          </div>

          <div className="vnyl-dropdown__sep" role="separator" />

          <div role="group" aria-labelledby={styleGroupId}>
            <div className="vnyl-dropdown__group-label" id={styleGroupId}>
              Interface style
            </div>
            {STYLES.map((value) => (
              <button
                key={value}
                type="button"
                role="menuitemradio"
                aria-checked={style === value}
                className="vnyl-dropdown__item"
                onClick={() => {
                  setStyle(value);
                  announce(`Interface style ${STYLE_LABELS[value]}`);
                  setOpen(false);
                }}
              >
                <span className="vnyl-dropdown__label">{STYLE_LABELS[value]}</span>
                {style === value && <Icon name="check" size={16} />}
              </button>
            ))}
          </div>

          {style === 'glass' && <div className="vnyl-dropdown__sep" role="separator" />}
          {style === 'glass' && (
            <div role="group" aria-labelledby={lookGroupId}>
              <div className="vnyl-dropdown__group-label" id={lookGroupId}>
                Glass look
              </div>
              {LOOKS.map((value) => (
                <button
                  key={value}
                  type="button"
                  role="menuitemradio"
                  aria-checked={glassLook === value}
                  className="vnyl-dropdown__item"
                  onClick={() => {
                    setGlassLook(value);
                    announce(`Glass look ${GLASS_LOOK_LABELS[value]}`);
                    setOpen(false);
                  }}
                >
                  <span className="vnyl-dropdown__label">{GLASS_LOOK_LABELS[value]}</span>
                  {glassLook === value && <Icon name="check" size={16} />}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
      {cropOpen && (
        <div className="vnyl-modal-backdrop">
          <section
            className="vnyl-modal vnyl-crop-preview"
            role="dialog"
            aria-modal="true"
            aria-labelledby="vnyl-crop-preview-title"
          >
            <header className="vnyl-modal__header">
              <h2 className="vnyl-modal__title" id="vnyl-crop-preview-title">Crop pages</h2>
              <IconButton icon="x" label="Close crop preview" onClick={() => setCropOpen(false)} />
            </header>
            <div className="vnyl-modal__body">
              <p className="vnyl-crop-preview__copy">Trim the same amount from each edge to preview a cleaner reading area.</p>
              <label className="vnyl-crop-preview__label" htmlFor="vnyl-crop-preview-range">
                Crop each edge <strong>{cropPreview}%</strong>
              </label>
              <input
                id="vnyl-crop-preview-range"
                className="vnyl-crop-preview__range"
                type="range"
                min={0}
                max={18}
                step={1}
                value={cropPreview}
                onChange={(event) => setCropPreview(Number(event.target.value))}
              />
              <p className="vnyl-modal__hint">This is a reversible viewing preview. The original PDF is unchanged.</p>
            </div>
            <footer className="vnyl-modal__footer">
              <button type="button" className="vnyl-button vnyl-button--ghost" onClick={() => setCropPreview(0)}>Reset</button>
              <button type="button" className="vnyl-button vnyl-button--primary" onClick={() => setCropOpen(false)}>Done</button>
            </footer>
          </section>
        </div>
      )}
    </div>
  );
}
