import { Icon } from '@/components/common';
import type { IconName } from '@/components/common';
import type { MouseEventHandler } from 'react';

interface ToolbarActionProps {
  icon: IconName;
  label: string;
  /** Short visible caption while `label` remains the full accessible name. */
  displayLabel?: string;
  ariaKeyShortcuts?: string;
  onClick: () => void;
  disabled?: boolean;
  active?: boolean;
  className?: string;
  onMouseDown?: MouseEventHandler<HTMLButtonElement>;
}

/** A compact icon-and-caption button for high-frequency toolbar actions. */
export function ToolbarAction({
  icon,
  label,
  displayLabel,
  ariaKeyShortcuts,
  onClick,
  disabled = false,
  active = false,
  className,
  onMouseDown,
}: ToolbarActionProps) {
  return (
    <button
      type="button"
      className={['vnyl-toolbar-action', active ? 'is-active' : '', className ?? '']
        .filter(Boolean)
        .join(' ')}
      aria-label={label}
      aria-pressed={active}
      aria-keyshortcuts={ariaKeyShortcuts}
      title={label}
      disabled={disabled}
      onMouseDown={onMouseDown}
      onClick={onClick}
    >
      <Icon name={icon} size={17} />
      <span>{displayLabel ?? label}</span>
    </button>
  );
}
