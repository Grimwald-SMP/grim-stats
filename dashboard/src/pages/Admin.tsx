import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { api, ApiError } from '../api/client';
import type { ApiKeyView, ConfigView, CreatedApiKey, PinnedRef, Role, StatTypeInfo, UserView } from '../api/types';
import { useDashboard } from '../components/Layout';
import { Loading, SectionTitle } from '../components/ui';
import { useAuth } from '../state/AuthContext';
import { useTheme } from '../state/ThemeContext';
import { humanizeKey, relativeTime, statTypeLabel } from '../lib/format';

export function Admin() {
  const { isAdmin } = useAuth();
  return isAdmin ? <AdminPanel /> : <LoginForm />;
}

function LoginForm() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Login failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-sm">
      <SectionTitle>Admin sign in</SectionTitle>
      <form onSubmit={submit} className="space-y-3 rounded-box border border-base-300 bg-base-100 p-5">
        <label className="form-control">
          <span className="label-text mb-1">Username</span>
          <input
            className="input input-bordered"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label className="form-control">
          <span className="label-text mb-1">Password</span>
          <input
            type="password"
            className="input input-bordered"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && <p className="text-sm text-error">{error}</p>}
        <button className="btn btn-primary w-full" disabled={busy}>
          {busy ? <span className="loading loading-spinner loading-sm" /> : 'Sign in'}
        </button>
        <p className="text-xs text-base-content/50">
          No account yet? Run <code className="rounded bg-base-200 px-1">/grimstats setup &lt;user&gt; &lt;password&gt;</code>{' '}
          in-game as an operator.
        </p>
      </form>
    </div>
  );
}

function AdminPanel() {
  const { logout, username, role } = useAuth();
  const { setTheme, themes } = useTheme();
  const { snapshot } = useDashboard();
  const [cfg, setCfg] = useState<ConfigView | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.getConfig().then(setCfg).catch(() => setStatus('Could not load config'));
  }, []);

  if (!cfg) return <Loading label="Loading settings…" />;

  const update = (patch: Partial<ConfigView>) => setCfg({ ...cfg, ...patch });

  const toggleIn = (list: string[], value: string): string[] =>
    list.includes(value) ? list.filter((v) => v !== value) : [...list, value];

  const save = async () => {
    setBusy(true);
    setStatus(null);
    try {
      const saved = await api.putConfig(cfg);
      setCfg(saved);
      setTheme(saved.defaultTheme);
      setStatus('Settings saved');
    } catch (e) {
      setStatus(e instanceof ApiError ? e.message : 'Save failed');
    } finally {
      setBusy(false);
    }
  };

  const refresh = async () => {
    await api.refresh().catch(() => undefined);
    setStatus('Snapshot refresh requested');
  };

  return (
    <div className="space-y-6">
      <SectionTitle
        action={
          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-base-content/50 sm:inline">
              {username} · <span className="uppercase">{role}</span>
            </span>
            <button className="btn btn-ghost btn-sm" onClick={() => void logout()}>
              Sign out
            </button>
          </div>
        }
      >
        Admin settings
      </SectionTitle>

      <AccountSection />
      <UsersSection />
      <ApiKeysSection />

      <Panel title="Appearance">
        <Field label="Default theme" hint="Applied for visitors who have not picked their own.">
          <select
            className="select select-bordered w-full max-w-xs"
            value={cfg.defaultTheme}
            onChange={(e) => update({ defaultTheme: e.target.value })}
          >
            {themes.map((t) => (
              <option key={t} value={t} className="capitalize">
                {t}
              </option>
            ))}
          </select>
        </Field>
      </Panel>

      <Panel title="Access & privacy">
        <Toggle
          label="Public dashboard"
          hint="When off, only signed-in admins can view stats."
          checked={cfg.publicDashboard}
          onChange={(v) => update({ publicDashboard: v })}
        />
        <Toggle
          label="Expose world seed"
          hint="Shows the seed to admins on the overview/world data."
          checked={cfg.exposeSeed}
          onChange={(v) => update({ exposeSeed: v })}
        />
        <Toggle
          label="Allow all origins (CORS)"
          hint="Lets any site or tool call the API. Safe since auth is token-based. Turn off to restrict to listed origins."
          checked={cfg.corsAllowAll}
          onChange={(v) => update({ corsAllowAll: v })}
        />
      </Panel>

      <Panel title="Collection">
        <Toggle
          label="Include offline players"
          hint="Reads saved stats for players who are not online."
          checked={cfg.includeOfflinePlayers}
          onChange={(v) => update({ includeOfflinePlayers: v })}
        />
        <Field label="Refresh interval (seconds)">
          <input
            type="number"
            min={1}
            className="input input-bordered w-28"
            value={cfg.snapshotIntervalSeconds}
            onChange={(e) => update({ snapshotIntervalSeconds: Number(e.target.value) })}
          />
        </Field>
        <Field label="Max players returned">
          <input
            type="number"
            min={1}
            className="input input-bordered w-28"
            value={cfg.maxPlayers}
            onChange={(e) => update({ maxPlayers: Number(e.target.value) })}
          />
        </Field>
      </Panel>

      <Panel
        title="Pinned homepage stats"
        hint="Pinned stats show as leaderboards on the homepage. Search to find a stat, then add it."
      >
        <PinnedManager
          pins={cfg.pinned ?? []}
          statTypes={snapshot?.statTypes ?? []}
          onChange={(pinned) => update({ pinned })}
        />
      </Panel>

      <Panel title="Visible statistics" hint="Unchecked stat types are hidden from non-admins.">
        <div className="grid grid-cols-1 gap-1 sm:grid-cols-2">
          {(snapshot?.statTypes ?? []).map((t) => (
            <label key={t.id} className="flex items-center gap-2 rounded px-1 py-1 text-sm">
              <input
                type="checkbox"
                className="checkbox checkbox-sm"
                checked={!cfg.hiddenStatTypes.includes(t.id)}
                onChange={() => update({ hiddenStatTypes: toggleIn(cfg.hiddenStatTypes, t.id) })}
              />
              <span>{statTypeLabel(t.id)}</span>
              {t.modded && <span className="badge badge-outline badge-xs">mod</span>}
            </label>
          ))}
        </div>
      </Panel>

      {(snapshot?.objectives.length ?? 0) > 0 && (
        <Panel title="Visible scoreboards" hint="Unchecked objectives are hidden from non-admins.">
          <div className="grid grid-cols-1 gap-1 sm:grid-cols-2">
            {(snapshot?.objectives ?? []).map((o) => (
              <label key={o.name} className="flex items-center gap-2 rounded px-1 py-1 text-sm">
                <input
                  type="checkbox"
                  className="checkbox checkbox-sm"
                  checked={!cfg.hiddenObjectives.includes(o.name)}
                  onChange={() => update({ hiddenObjectives: toggleIn(cfg.hiddenObjectives, o.name) })}
                />
                <span>{humanizeKey(o.name)}</span>
              </label>
            ))}
          </div>
        </Panel>
      )}

      <div className="sticky bottom-0 flex items-center gap-3 border-t border-base-300 bg-base-200/80 py-3 backdrop-blur">
        <button className="btn btn-primary" onClick={() => void save()} disabled={busy}>
          {busy ? <span className="loading loading-spinner loading-sm" /> : 'Save settings'}
        </button>
        <button className="btn btn-ghost" onClick={() => void refresh()}>
          Refresh data now
        </button>
        {status && <span className="text-sm text-base-content/60">{status}</span>}
      </div>
    </div>
  );
}

function PinnedManager({
  pins,
  statTypes,
  onChange,
}: {
  pins: PinnedRef[];
  statTypes: StatTypeInfo[];
  onChange: (pins: PinnedRef[]) => void;
}) {
  const [type, setType] = useState('');
  const [query, setQuery] = useState('');

  const activeType = statTypes.find((t) => t.id === type) ?? statTypes[0];
  const pinnedKeys = new Set(pins.map((p) => `${p.statType}/${p.statKey}`));

  const matches = useMemo(() => {
    if (!activeType) return [];
    const q = query.trim().toLowerCase();
    return activeType.keys
      .filter((k) => !pinnedKeys.has(`${activeType.id}/${k}`))
      .filter((k) => !q || humanizeKey(k).toLowerCase().includes(q) || k.includes(q))
      .slice(0, 40);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeType, query, pins]);

  const add = (key: string) => {
    if (activeType) onChange([...pins, { statType: activeType.id, statKey: key }]);
  };
  const remove = (p: PinnedRef) =>
    onChange(pins.filter((x) => !(x.statType === p.statType && x.statKey === p.statKey)));

  return (
    <div className="space-y-4">
      {pins.length === 0 ? (
        <p className="text-sm text-base-content/50">Nothing pinned yet.</p>
      ) : (
        <ul className="flex flex-wrap gap-2">
          {pins.map((p) => (
            <li
              key={`${p.statType}/${p.statKey}`}
              className="flex items-center gap-2 rounded-box border border-base-300 bg-base-200 py-1 pl-3 pr-1 text-sm"
            >
              <span>
                {humanizeKey(p.statKey)}
                <span className="ml-1 text-xs text-base-content/40">{statTypeLabel(p.statType)}</span>
              </span>
              <button
                className="btn btn-ghost btn-xs btn-square"
                aria-label={`Unpin ${humanizeKey(p.statKey)}`}
                onClick={() => remove(p)}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="rounded-box border border-base-300 p-3">
        <div className="flex flex-col gap-2 sm:flex-row">
          <select
            className="select select-bordered select-sm w-full sm:w-56"
            value={activeType?.id ?? ''}
            onChange={(e) => setType(e.target.value)}
          >
            {statTypes.map((t) => (
              <option key={t.id} value={t.id}>
                {statTypeLabel(t.id)}
                {t.modded ? ' (mod)' : ''}
              </option>
            ))}
          </select>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search stats to pin"
            className="input input-bordered input-sm w-full sm:flex-1"
          />
        </div>

        {activeType && (
          <ul className="mt-2 max-h-56 divide-y divide-base-200 overflow-y-auto">
            {matches.length === 0 ? (
              <li className="py-3 text-center text-sm text-base-content/50">No matching stats.</li>
            ) : (
              matches.map((k) => (
                <li key={k} className="flex items-center justify-between gap-2 py-1.5 text-sm">
                  <span className="min-w-0 truncate">{humanizeKey(k)}</span>
                  <button className="btn btn-ghost btn-xs" onClick={() => add(k)}>
                    Pin
                  </button>
                </li>
              ))
            )}
          </ul>
        )}
      </div>
    </div>
  );
}

function RoleBadge({ role }: { role: Role }) {
  const root = role === 'ROOT';
  return (
    <span
      className="badge badge-sm"
      style={
        root
          ? { background: 'var(--color-primary)', color: 'var(--color-primary-content)', border: 'none' }
          : { background: 'var(--color-base-300)', color: 'var(--color-base-content)', border: 'none' }
      }
    >
      {role}
    </span>
  );
}

function AccountSection() {
  const { username } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setStatus(null);
    try {
      await api.changePassword(current, next);
      setCurrent('');
      setNext('');
      setStatus('Password updated');
    } catch (err) {
      setStatus(err instanceof ApiError ? err.message : 'Failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Panel title="Your account" hint={`Signed in as ${username ?? ''}. Change your own password here.`}>
      <form onSubmit={submit} className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <label className="form-control w-full sm:w-56">
          <span className="label-text mb-1 text-xs">Current password</span>
          <input
            type="password"
            className="input input-bordered input-sm"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <label className="form-control w-full sm:w-56">
          <span className="label-text mb-1 text-xs">New password</span>
          <input
            type="password"
            className="input input-bordered input-sm"
            value={next}
            onChange={(e) => setNext(e.target.value)}
            autoComplete="new-password"
            required
          />
        </label>
        <button className="btn btn-primary btn-sm" disabled={busy}>
          Update
        </button>
        {status && <span className="text-sm text-base-content/60">{status}</span>}
      </form>
    </Panel>
  );
}

function UsersSection() {
  const { isRoot, username: me } = useAuth();
  const [users, setUsers] = useState<UserView[] | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [newUser, setNewUser] = useState({ username: '', password: '', role: 'ADMIN' as Role });
  const [resetFor, setResetFor] = useState<string | null>(null);
  const [resetPw, setResetPw] = useState('');

  const load = () => api.users().then(setUsers).catch(() => setStatus('Could not load users'));
  useEffect(() => {
    void load();
  }, []);

  const wrap = async (fn: () => Promise<unknown>, ok: string) => {
    setStatus(null);
    try {
      await fn();
      setStatus(ok);
      await load();
    } catch (err) {
      setStatus(err instanceof ApiError ? err.message : 'Failed');
    }
  };

  return (
    <Panel
      title="Users"
      hint={isRoot ? 'Add admins, change roles, reset passwords, or remove users.' : 'Only ROOT users can manage accounts.'}
    >
      <ul className="divide-y divide-base-200">
        {(users ?? []).map((u) => (
          <li key={u.username} className="py-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-medium">{u.username}</span>
              {u.username === me && <span className="text-xs text-base-content/40">(you)</span>}
              {isRoot ? (
                <select
                  className="select select-bordered select-xs"
                  value={u.role}
                  onChange={(e) => void wrap(() => api.updateUser(u.username, { role: e.target.value as Role }), 'Role updated')}
                >
                  <option value="ADMIN">ADMIN</option>
                  <option value="ROOT">ROOT</option>
                </select>
              ) : (
                <RoleBadge role={u.role} />
              )}
              {isRoot && (
                <div className="ml-auto flex gap-1">
                  <button
                    className="btn btn-ghost btn-xs"
                    onClick={() => {
                      setResetFor(resetFor === u.username ? null : u.username);
                      setResetPw('');
                    }}
                  >
                    Reset password
                  </button>
                  <button
                    className="btn btn-ghost btn-xs text-error"
                    onClick={() => void wrap(() => api.deleteUser(u.username), `Removed ${u.username}`)}
                  >
                    Remove
                  </button>
                </div>
              )}
            </div>
            {isRoot && resetFor === u.username && (
              <div className="mt-2 flex gap-2">
                <input
                  type="password"
                  className="input input-bordered input-xs w-48"
                  placeholder="New password"
                  value={resetPw}
                  onChange={(e) => setResetPw(e.target.value)}
                />
                <button
                  className="btn btn-primary btn-xs"
                  disabled={resetPw.length < 4}
                  onClick={() =>
                    void wrap(() => api.updateUser(u.username, { password: resetPw }), 'Password reset').then(() => {
                      setResetFor(null);
                      setResetPw('');
                    })
                  }
                >
                  Save
                </button>
              </div>
            )}
          </li>
        ))}
        {users?.length === 0 && <li className="py-3 text-sm text-base-content/50">No users.</li>}
      </ul>

      {isRoot && (
        <form
          className="mt-4 flex flex-col gap-2 rounded-box border border-base-300 p-3 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            void wrap(() => api.addUser(newUser.username, newUser.password, newUser.role), `Added ${newUser.username}`).then(
              () => setNewUser({ username: '', password: '', role: 'ADMIN' }),
            );
          }}
        >
          <label className="form-control w-full sm:w-40">
            <span className="label-text mb-1 text-xs">Username</span>
            <input
              className="input input-bordered input-sm"
              value={newUser.username}
              onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
              required
            />
          </label>
          <label className="form-control w-full sm:w-40">
            <span className="label-text mb-1 text-xs">Password</span>
            <input
              type="password"
              className="input input-bordered input-sm"
              value={newUser.password}
              onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
              required
            />
          </label>
          <label className="form-control w-full sm:w-28">
            <span className="label-text mb-1 text-xs">Role</span>
            <select
              className="select select-bordered select-sm"
              value={newUser.role}
              onChange={(e) => setNewUser({ ...newUser, role: e.target.value as Role })}
            >
              <option value="ADMIN">ADMIN</option>
              <option value="ROOT">ROOT</option>
            </select>
          </label>
          <button className="btn btn-primary btn-sm">Add user</button>
        </form>
      )}

      {status && <p className="mt-2 text-sm text-base-content/60">{status}</p>}
    </Panel>
  );
}

function ApiKeysSection() {
  const { isRoot } = useAuth();
  const [keys, setKeys] = useState<ApiKeyView[] | null>(null);
  const [name, setName] = useState('');
  const [role, setRole] = useState<Role>('ADMIN');
  const [created, setCreated] = useState<CreatedApiKey | null>(null);
  const [copied, setCopied] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const load = () => api.apiKeys().then(setKeys).catch(() => setStatus('Could not load keys'));
  useEffect(() => {
    void load();
  }, []);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    setStatus(null);
    setCopied(false);
    try {
      const key = await api.createApiKey(name, role);
      setCreated(key);
      setName('');
      setRole('ADMIN');
      await load();
    } catch (err) {
      setStatus(err instanceof ApiError ? err.message : 'Failed');
    }
  };

  const revoke = async (id: string) => {
    setStatus(null);
    try {
      await api.revokeApiKey(id);
      await load();
    } catch (err) {
      setStatus(err instanceof ApiError ? err.message : 'Failed');
    }
  };

  return (
    <Panel
      title="API keys"
      hint="Programmatic access. Send as an Authorization: Bearer header. Any admin can create ADMIN keys."
    >
      {created && (
        <div className="mb-3 rounded-box border border-warning/50 bg-warning/10 p-3">
          <p className="mb-2 text-sm font-medium">Copy your new key now. It will not be shown again.</p>
          <div className="flex items-center gap-2">
            <code className="min-w-0 flex-1 truncate rounded bg-base-300 px-2 py-1 text-xs">{created.key}</code>
            <button
              className="btn btn-sm"
              onClick={() => {
                void navigator.clipboard?.writeText(created.key);
                setCopied(true);
              }}
            >
              {copied ? 'Copied' : 'Copy'}
            </button>
            <button className="btn btn-ghost btn-sm" onClick={() => setCreated(null)}>
              Done
            </button>
          </div>
        </div>
      )}

      <form className="flex flex-col gap-2 sm:flex-row sm:items-end" onSubmit={create}>
        <label className="form-control w-full sm:flex-1">
          <span className="label-text mb-1 text-xs">Key name</span>
          <input
            className="input input-bordered input-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Grafana, mobile app"
            required
          />
        </label>
        <label className="form-control w-full sm:w-28">
          <span className="label-text mb-1 text-xs">Role</span>
          <select
            className="select select-bordered select-sm"
            value={role}
            onChange={(e) => setRole(e.target.value as Role)}
          >
            <option value="ADMIN">ADMIN</option>
            {isRoot && <option value="ROOT">ROOT</option>}
          </select>
        </label>
        <button className="btn btn-primary btn-sm">Create key</button>
      </form>

      <ul className="mt-3 divide-y divide-base-200">
        {(keys ?? []).map((k) => (
          <li key={k.id} className="flex flex-wrap items-center gap-2 py-2 text-sm">
            <span className="font-medium">{k.name}</span>
            <RoleBadge role={k.role} />
            <code className="text-xs text-base-content/40">{k.preview}</code>
            <span className="text-xs text-base-content/40">
              by {k.createdBy} · {k.lastUsedEpochMs ? `used ${relativeTime(k.lastUsedEpochMs)}` : 'never used'}
            </span>
            <button className="btn btn-ghost btn-xs ml-auto text-error" onClick={() => void revoke(k.id)}>
              Revoke
            </button>
          </li>
        ))}
        {keys?.length === 0 && <li className="py-3 text-sm text-base-content/50">No API keys yet.</li>}
      </ul>

      {status && <p className="mt-2 text-sm text-base-content/60">{status}</p>}
    </Panel>
  );
}

function Panel({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <section className="rounded-box border border-base-300 bg-base-100 p-4">
      <h3 className="font-medium">{title}</h3>
      {hint && <p className="mb-3 mt-0.5 text-xs text-base-content/50">{hint}</p>}
      <div className={hint ? 'space-y-3' : 'mt-3 space-y-3'}>{children}</div>
    </section>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
      <div>
        <div className="text-sm">{label}</div>
        {hint && <div className="text-xs text-base-content/50">{hint}</div>}
      </div>
      {children}
    </div>
  );
}

function Toggle({
  label,
  hint,
  checked,
  onChange,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <Field label={label} hint={hint}>
      <input
        type="checkbox"
        className="toggle toggle-primary"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
    </Field>
  );
}
