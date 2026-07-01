import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
// Geist (the typeface used on grimwald.xyz). Self-contained variable fonts, bundled into the build.
import '@fontsource-variable/geist';
import '@fontsource-variable/geist-mono';
import './index.css';
import { App } from './App';
import { ThemeProvider } from './state/ThemeContext';
import { AuthProvider } from './state/AuthContext';

// BrowserRouter gives real path URLs (/players/<uuid>, /leaderboards) rather than #fragments. This
// matters for link sharing: the mod's HTTP server injects Open Graph/Discord preview tags per route,
// and a URL fragment is never sent to the server so hash routes could not be previewed. The mod's
// StaticHandler serves index.html as an SPA fallback for these paths, so no extra rewrite rules are
// needed.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ThemeProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>,
);
