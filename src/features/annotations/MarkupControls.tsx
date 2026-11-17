import { MARKUP_SWATCHES } from './types';

interface Props {
  /** Index into MARKUP_SWATCHES, or -1 when the current color matches none. */
  swatch: number;
  onSwatch(index: number): void;
  /** Highlight opacity 0.1..1; omit to hide the slider (e.g. for line markups). */
  opacity?: number;
  onOpacity?(opacity: number): void;
}

/** Color swatches and (optionally) the highlight opacity slider. */
export function MarkupColorControls({ swatch, onSwatch, opacity, onOpacity }: Props) {
  return (
    <div className="vnyl-markup-controls">
      <div role="radiogroup" aria-label="Markup color" className="vnyl-markup-popup__swatches">
        {MARKUP_SWATCHES.map((s, i) => (
          <button
            key={s.name}
            type="button"
            role="radio"
            aria-checked={swatch === i}
            aria-label={s.name}
            title={s.name}
            className={`vnyl-markup-swatch${swatch === i ? ' is-active' : ''}`}
            style={{ backgroundColor: `rgb(${s.rgb.join(',')})` }}
            onClick={() => onSwatch(i)}
          />
        ))}
      </div>
      {opacity !== undefined && onOpacity && (
        <label className="vnyl-markup-popup__opacity" title="Highlight opacity">
          <span className="vnyl-sr-only">Highlight opacity</span>
          <input
            type="range"
            min={10}
            max={100}
            step={5}
            value={Math.round(opacity * 100)}
            onChange={(e) => onOpacity(Number(e.target.value) / 100)}
          />
        </label>
      )}
    </div>
  );
}
