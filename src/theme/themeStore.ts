import { create } from 'zustand';

export type UiTheme = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';
/** Chrome look: translucent Liquid Glass (default) or the flat Classic UI. */
export type UiStyle = 'glass' | 'classic';
/** How see-through Liquid Glass is: milky frost (default) or clear. */
export type GlassLook = 'frosted' | 'clear';
/** Which color scheme the page renders in when the UI is dark. */
export type DarkScheme = 'night' | 'green' | 'amber';
export type PdfColorMode = 'light' | 'dark' | 'sepia' | 'green' | 'amber';

const THEME_KEY = 'vnyl.theme';
const STYLE_KEY = 'vnyl.style';
const GLASS_KEY = 'vnyl.glass';
const DARK_SCHEME_KEY = 'vnyl.darkScheme';
const PDF_COLOR_MODE_KEY = 'vnyl.pdfColorMode';
const DARK_SCHEMES: DarkScheme[] = ['night', 'green', 'amber'];

/**
 * Page tint (RGB 0-255) multiplied over the inverted page for each dark scheme.
 * Night has no tint (plain white-on-black); Green/Amber colour the ink. Applied
 * at raster time in {@link PdfJsEngine.renderPage}.
 */
export const DARK_SCHEME_TINT: Record<DarkScheme, [number, number, number] | null> = {
  night: null,
  // The Linux console's bright ANSI green (#55FF55), so green-on-black pages
  // read like a terminal rather than a pastel.
  green: [85, 255, 85],
  amber: [240, 185, 80],
};

export const DARK_SCHEME_LABELS: Record<DarkScheme, string> = {
  night: 'Night',
  green: 'Green',
  amber: 'Amber',
};

/** Display names for the viewing mode. Shared rather than repeated per control
    because three surfaces (the toolbar's appearance menu, the More menu's
    cycling row, the View menu) both label and announce the same change, and a
    screen-reader user hearing two different sentences for it would have no way
    to tell they are the same setting. */
export const STYLE_LABELS: Record<UiStyle, string> = {
  glass: 'Liquid Glass',
  classic: 'Classic',
};

export const GLASS_LOOK_LABELS: Record<GlassLook, string> = {
  frosted: 'Frosted',
  clear: 'Clear',
};

export const THEME_LABELS: Record<UiTheme, string> = {
  light: 'Light',
  dark: 'Dark',
  system: 'Match system',
};

function readStored<T extends string>(key: string, allowed: readonly T[], fallback: T): T {
  try {
    const value = localStorage.getItem(key);
    return value && (allowed as readonly string[]).includes(value) ? (value as T) : fallback;
  } catch {
    return fallback;
  }
}

function persist(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* storage may be unavailable (private mode); ignore */
  }
}

interface ThemeState {
  theme: UiTheme;
  resolvedTheme: ResolvedTheme;
  darkScheme: DarkScheme;
  pdfColorMode: PdfColorMode;
  style: UiStyle;
  glassLook: GlassLook;

  setStyle(style: UiStyle): void;
  setGlassLook(look: GlassLook): void;
  setTheme(theme: UiTheme): void;
  toggleTheme(): void;
  setResolvedTheme(resolved: ResolvedTheme): void;
  setDarkScheme(scheme: DarkScheme): void;
  setPdfColorMode(mode: PdfColorMode): void;
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  theme: readStored<UiTheme>(THEME_KEY, ['light', 'dark', 'system'], 'system'),
  resolvedTheme: 'light',
  darkScheme: readStored<DarkScheme>(DARK_SCHEME_KEY, DARK_SCHEMES, 'night'),
  pdfColorMode: readStored<PdfColorMode>(PDF_COLOR_MODE_KEY, ['light', 'dark', 'sepia', 'green', 'amber'], 'light'),
  style: readStored<UiStyle>(STYLE_KEY, ['glass', 'classic'], 'glass'),

  glassLook: readStored<GlassLook>(GLASS_KEY, ['frosted', 'clear'], 'frosted'),
  setGlassLook: (glassLook) => {
    persist(GLASS_KEY, glassLook);
    set({ glassLook });
  },

  setStyle: (style) => {
    persist(STYLE_KEY, style);
    set({ style });
  },

  setTheme: (theme) => {
    persist(THEME_KEY, theme);
    set({ theme });
  },
  toggleTheme: () => {
    const next: UiTheme = get().resolvedTheme === 'dark' ? 'light' : 'dark';
    persist(THEME_KEY, next);
    set({ theme: next });
  },
  setResolvedTheme: (resolvedTheme) => set({ resolvedTheme }),
  setDarkScheme: (darkScheme) => {
    persist(DARK_SCHEME_KEY, darkScheme);
    set({ darkScheme });
  },
  setPdfColorMode: (pdfColorMode) => {
    persist(PDF_COLOR_MODE_KEY, pdfColorMode);
    set({ pdfColorMode });
  },
}));
