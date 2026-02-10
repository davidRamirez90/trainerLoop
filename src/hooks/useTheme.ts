import { useState, useEffect, useCallback } from 'react';

export type Theme = 'dark' | 'light';

const THEME_STORAGE_KEY = 'trainerLoop.theme.v1';

const getSystemPreference = (): Theme => {
  if (typeof window === 'undefined') {
    return 'dark';
  }
  const prefersLight = window.matchMedia('(prefers-color-scheme: light)').matches;
  return prefersLight ? 'light' : 'dark';
};

const loadThemeFromStorage = (): Theme | null => {
  if (typeof window === 'undefined') {
    return null;
  }
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (stored === 'dark' || stored === 'light') {
      return stored;
    }
  } catch {
    // Ignore storage errors
  }
  return null;
};

const saveThemeToStorage = (theme: Theme) => {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Ignore storage errors
  }
};

const applyThemeToDocument = (theme: Theme) => {
  if (typeof document === 'undefined') {
    return;
  }
  document.documentElement.setAttribute('data-theme', theme);
};

export const useTheme = () => {
  const [theme, setThemeState] = useState<Theme>(() => {
    const stored = loadThemeFromStorage();
    return stored ?? getSystemPreference();
  });

  useEffect(() => {
    applyThemeToDocument(theme);
  }, [theme]);

  const setTheme = useCallback((newTheme: Theme) => {
    setThemeState(newTheme);
    saveThemeToStorage(newTheme);
  }, []);

  const toggleTheme = useCallback(() => {
    const newTheme = theme === 'dark' ? 'light' : 'dark';
    setTheme(newTheme);
  }, [theme, setTheme]);

  // Listen for system preference changes when no user preference is stored
  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined;
    }
    
    const stored = loadThemeFromStorage();
    if (stored) {
      // User has a preference, don't listen to system changes
      return undefined;
    }

    const mediaQuery = window.matchMedia('(prefers-color-scheme: light)');
    const handleChange = (event: MediaQueryListEvent) => {
      const newTheme = event.matches ? 'light' : 'dark';
      setThemeState(newTheme);
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, []);

  return {
    theme,
    setTheme,
    toggleTheme,
  };
};
