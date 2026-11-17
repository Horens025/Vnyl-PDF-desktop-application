import type { IconName } from '@/components/common';
import type { SidebarTab } from '@/state/viewerStore';

export interface SidebarPanelOption {
  id: SidebarTab;
  title: string;
  icon: IconName;
}

/** Built-in panels available from the single sidebar panel picker. */
export const CORE_SIDEBAR_PANELS: SidebarPanelOption[] = [
  { id: 'thumbnails', title: 'Thumbnails', icon: 'image' },
  { id: 'outline', title: 'Outline', icon: 'list' },
  { id: 'annotations', title: 'Annotations', icon: 'highlighter' },
  { id: 'signatures', title: 'Signature library', icon: 'signature' },
];
