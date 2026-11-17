import { useId } from 'react';

interface Option<T extends string> {
  value: T;
  label: string;
}

interface SegmentedProps<T extends string> {
  value: T;
  options: readonly Option<T>[];
  onChange(value: T): void;
  'aria-label': string;
}

/**
 * A segmented control: one choice out of a few, with a thumb that slides to the
 * selection (a spring under the Liquid Glass style, a plain highlight under
 * Classic). A WAI-ARIA radio group, so arrows move and select.
 */
export function Segmented<T extends string>({
  value,
  options,
  onChange,
  'aria-label': label,
}: SegmentedProps<T>) {
  const name = useId();
  const index = Math.max(
    0,
    options.findIndex((o) => o.value === value),
  );
  return (
    <div
      className="vnyl-segmented"
      role="radiogroup"
      aria-label={label}
      style={{ '--seg-count': options.length, '--seg-index': index } as React.CSSProperties}
    >
      <span className="vnyl-segmented__thumb" aria-hidden="true" />
      {options.map((o, i) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          id={`${name}-${i}`}
          aria-checked={o.value === value}
          tabIndex={o.value === value ? 0 : -1}
          className="vnyl-segmented__option"
          onClick={() => onChange(o.value)}
          onKeyDown={(e) => {
            const step =
              e.key === 'ArrowRight' || e.key === 'ArrowDown'
                ? 1
                : e.key === 'ArrowLeft' || e.key === 'ArrowUp'
                  ? -1
                  : 0;
            if (!step) return;
            e.preventDefault();
            const next = options[(i + step + options.length) % options.length];
            onChange(next.value);
            document
              .getElementById(`${name}-${(i + step + options.length) % options.length}`)
              ?.focus();
          }}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
