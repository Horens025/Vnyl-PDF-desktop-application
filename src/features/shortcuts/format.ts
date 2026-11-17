const SYMBOLS: Record<string, string> = {
  ArrowLeft: '←',
  ArrowRight: '→',
  ArrowUp: '↑',
  ArrowDown: '↓',
  Escape: 'Esc',
  ' ': 'Space',
};

/**
 * "Mod+Shift+K" -> ["Ctrl", "Shift", "K"] (or ["⌘", "⇧", "K"] on a Mac). `Mod`
 * is Cmd on macOS and Ctrl elsewhere, the same rule the shortcut handler uses.
 */
export function keybindingParts(binding: string, mac: boolean): string[] {
  return binding.split('+').map((part) => {
    if (part === 'Mod') return mac ? '⌘' : 'Ctrl';
    if (part === 'Shift') return mac ? '⇧' : 'Shift';
    if (part === 'Alt') return mac ? '⌥' : 'Alt';
    if (part === 'Ctrl') return mac ? '⌃' : 'Ctrl';
    return SYMBOLS[part] ?? (part.length === 1 ? part.toUpperCase() : part);
  });
}

export interface ShortcutRow {
  id: string;
  title: string;
  keys: string[];
}

export interface ShortcutGroup {
  category: string;
  rows: ShortcutRow[];
}

interface CommandLike {
  id: string;
  title: string;
  category?: string;
  keybinding?: string;
}

/** Group every command that has a shortcut by category, in a stable, readable order. */
export function groupShortcuts(commands: readonly CommandLike[], mac: boolean): ShortcutGroup[] {
  const groups = new Map<string, ShortcutRow[]>();
  for (const c of commands) {
    if (!c.keybinding) continue;
    const category = c.category ?? 'Other';
    const rows = groups.get(category) ?? [];
    rows.push({
      id: c.id,
      title: c.title.replace(/…$/, ''),
      keys: keybindingParts(c.keybinding, mac),
    });
    groups.set(category, rows);
  }
  return [...groups.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([category, rows]) => ({
      category,
      rows: rows.sort((a, b) => a.title.localeCompare(b.title)),
    }));
}
