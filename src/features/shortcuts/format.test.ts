import { describe, expect, it } from 'vitest';

import { groupShortcuts, keybindingParts } from './format';

describe('keybindingParts', () => {
  it('maps Mod to Ctrl or Command', () => {
    expect(keybindingParts('Mod+Shift+K', false)).toEqual(['Ctrl', 'Shift', 'K']);
    expect(keybindingParts('Mod+Shift+K', true)).toEqual(['⌘', '⇧', 'K']);
  });

  it('shows arrows and Esc as symbols and upper-cases letters', () => {
    expect(keybindingParts('Mod+Alt+ArrowRight', false)).toEqual(['Ctrl', 'Alt', '→']);
    expect(keybindingParts('Escape', false)).toEqual(['Esc']);
    expect(keybindingParts('Mod+]', false)).toEqual(['Ctrl', ']']);
  });
});

describe('groupShortcuts', () => {
  const commands = [
    { id: 'b', title: 'Zoom in', category: 'View', keybinding: 'Mod+=' },
    { id: 'a', title: 'Open document…', category: 'File', keybinding: 'Mod+O' },
    { id: 'c', title: 'No shortcut', category: 'File' },
    { id: 'd', title: 'Annotate', category: 'File', keybinding: 'Mod+Shift+A' },
  ];

  it('skips commands without a shortcut, groups by category and sorts', () => {
    const g = groupShortcuts(commands, false);
    expect(g.map((x) => x.category)).toEqual(['File', 'View']);
    expect(g[0].rows.map((r) => r.title)).toEqual(['Annotate', 'Open document']);
    expect(g[0].rows[1].keys).toEqual(['Ctrl', 'O']);
  });
});
