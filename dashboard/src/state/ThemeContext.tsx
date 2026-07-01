import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { apiUrl } from '../api/client';

// The set of daisyUI themes bundled in index.css. Keep in sync with the @plugin "daisyui" themes list.
export const THEMES = [
  'grimwald',
  'grimstats',
  'light',
  'dark',
  'nord',
  'business',
  'dracula',
  'emerald',
  'corporate',
] as const;

export type Theme = (typeof THEMES)[number];

interface ThemeContextValue {
  theme: string;
  setTheme: (theme: string) => void;
  themes: readonly string[];
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

const STORAGE_KEY = 'grimstats.theme';

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<string>(() => localStorage.getItem(STORAGE_KEY) ?? 'grimwald');

  // Apply the server's default theme on first load if the user has not chosen one locally.
  useEffect(() => {
    if (localStorage.getItem(STORAGE_KEY)) return;
    let cancelled = false;
    fetch(apiUrl('/api/ui'))
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (!cancelled && data?.defaultTheme) setThemeState(data.defaultTheme);
      })
      .catch(() => {
        /* keep local default */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const setTheme = (next: string) => {
    localStorage.setItem(STORAGE_KEY, next);
    setThemeState(next);
  };

  const value = useMemo(() => ({ theme, setTheme, themes: THEMES }), [theme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
