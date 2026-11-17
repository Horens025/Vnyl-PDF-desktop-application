import type { IconName } from '@/components/common';

/** A right-group toolbar tool that can collapse into the overflow (⋯) menu. */
export interface OverflowTool {
  id: string;
  icon: IconName;
  /** Full label: the button's aria-label and tooltip (may include a shortcut). */
  label: string;
  /** Short label shown as the row text in the overflow menu. */
  menuLabel: string;
  /** Optional caption shown beneath the icon when the tool fits inline. */
  displayLabel?: string;
  onClick: () => void;
  disabled?: boolean;
  active?: boolean;
  /** Indicates a mutually exclusive selected option inside a menu. */
  selection?: 'radio';
  /** Preserve the page text selection on click (Comment / Highlight need it). */
  preserveSelection?: boolean;
}
