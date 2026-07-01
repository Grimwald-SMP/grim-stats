import { NavLink, Outlet, useOutletContext } from 'react-router-dom';
import type { StatsSnapshot } from '../api/types';
import { useSnapshot } from '../hooks/useSnapshot';
import { useAuth } from '../state/AuthContext';
import { ThemeMenu } from './ThemeMenu';

export interface DashboardContext {
  snapshot: StatsSnapshot | null;
  connected: boolean;
  error: string | null;
}

export function useDashboard(): DashboardContext {
  return useOutletContext<DashboardContext>();
}

const navItems = [
  { to: '/', label: 'Overview', end: true },
  { to: '/players', label: 'Players', end: false },
  { to: '/leaderboards', label: 'Leaderboards', end: false },
  { to: '/scoreboards', label: 'Scoreboards', end: false },
  { to: '/seasons', label: 'Seasons', end: false },
];

export function Layout() {
  const { snapshot, connected, error } = useSnapshot();
  const { isAdmin } = useAuth();

  return (
    <div className="min-h-full bg-base-200 text-base-content">
      <header className="navbar sticky top-0 z-30 bg-base-100 border-b border-base-300 px-3 sm:px-6">
        <div className="navbar-start gap-1">
          {/* Mobile menu */}
          <div className="dropdown sm:hidden">
            <button tabIndex={0} className="btn btn-ghost btn-square" aria-label="Open menu">
              <MenuIcon />
            </button>
            <ul
              tabIndex={0}
              className="menu dropdown-content mt-2 w-52 rounded-box bg-base-100 p-2 shadow border border-base-300 z-40"
            >
              {navItems.map((item) => (
                <li key={item.to}>
                  <NavLink to={item.to} end={item.end}>
                    {item.label}
                  </NavLink>
                </li>
              ))}
              <li>
                <NavLink to="/admin">Admin</NavLink>
              </li>
            </ul>
          </div>
          <NavLink to="/" className="btn btn-ghost px-2 text-lg font-semibold normal-case">
            Grim<span className="text-primary">Stats</span>
          </NavLink>
        </div>

        <nav className="navbar-center hidden sm:flex">
          <ul className="menu menu-horizontal gap-1">
            {navItems.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} end={item.end} className={({ isActive }) => (isActive ? 'active' : '')}>
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="navbar-end gap-1">
          <ConnectionDot connected={connected} hasData={snapshot !== null} />
          <ThemeMenu />
          <NavLink to="/admin" className="btn btn-ghost btn-sm hidden sm:inline-flex">
            {isAdmin ? 'Admin' : 'Sign in'}
          </NavLink>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl px-3 py-4 sm:px-6 sm:py-6">
        {error && !snapshot && (
          <div className="alert alert-warning mb-4">
            <span>Could not reach the GrimStats API. Retrying…</span>
          </div>
        )}
        <Outlet context={{ snapshot, connected, error } satisfies DashboardContext} />
      </main>

      <footer className="mx-auto w-full max-w-6xl px-3 py-6 text-center text-xs text-base-content/50 sm:px-6">
        GrimStats · server statistics
      </footer>
    </div>
  );
}

function ConnectionDot({ connected, hasData }: { connected: boolean; hasData: boolean }) {
  const label = connected ? 'Live' : hasData ? 'Polling' : 'Connecting';
  const color = connected ? 'bg-success' : hasData ? 'bg-warning' : 'bg-base-content/30';
  return (
    <span className="hidden items-center gap-2 px-2 text-xs text-base-content/60 sm:flex" title={label}>
      <span className={`inline-block h-2 w-2 rounded-full ${color}`} />
      {label}
    </span>
  );
}

function MenuIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
    </svg>
  );
}
