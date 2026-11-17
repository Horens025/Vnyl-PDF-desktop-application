import { beforeEach, describe, expect, it } from 'vitest';

import { useThemeStore } from './themeStore';

describe('themeStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useThemeStore.setState({ theme: 'system', resolvedTheme: 'light' });
  });

  it('setTheme updates state and persists', () => {
    useThemeStore.getState().setTheme('dark');
    expect(useThemeStore.getState().theme).toBe('dark');
    expect(localStorage.getItem('vnyl.theme')).toBe('dark');
  });

  it('toggleTheme flips relative to the resolved theme', () => {
    useThemeStore.getState().setResolvedTheme('dark');
    useThemeStore.getState().toggleTheme();
    expect(useThemeStore.getState().theme).toBe('light');

    useThemeStore.getState().setResolvedTheme('light');
    useThemeStore.getState().toggleTheme();
    expect(useThemeStore.getState().theme).toBe('dark');
  });

  it('setStyle updates state and persists; defaults to glass', () => {
    useThemeStore.setState({ style: 'glass' });
    expect(useThemeStore.getState().style).toBe('glass');
    useThemeStore.getState().setStyle('classic');
    expect(useThemeStore.getState().style).toBe('classic');
    expect(localStorage.getItem('vnyl.style')).toBe('classic');
  });
});

describe('glass look', () => {
  it('defaults to frosted and persists the clear look', () => {
    useThemeStore.setState({ glassLook: 'frosted' });
    useThemeStore.getState().setGlassLook('clear');
    expect(useThemeStore.getState().glassLook).toBe('clear');
    expect(localStorage.getItem('vnyl.glass')).toBe('clear');
  });
});
