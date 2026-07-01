import { useState } from 'react';
import { useDashboard } from '../components/Layout';
import { EmptyState, Loading, SectionTitle } from '../components/ui';
import { formatNumber, humanizeKey } from '../lib/format';

function stripFormatting(s: string): string {
  return s.replace(/§./g, '');
}

export function Scoreboards() {
  const { snapshot } = useDashboard();
  const [selected, setSelected] = useState<string | null>(null);

  if (!snapshot) return <Loading />;
  const { objectives } = snapshot;

  if (objectives.length === 0) {
    return (
      <div className="space-y-4">
        <SectionTitle>Scoreboards</SectionTitle>
        <EmptyState
          title="No scoreboard objectives"
          hint="Objectives created with /scoreboard (including those from datapacks and mods) show up here."
        />
      </div>
    );
  }

  const active = selected
    ? objectives.find((o) => o.name === selected) ?? objectives[0]
    : objectives[0];

  return (
    <div className="space-y-4">
      <SectionTitle>Scoreboards</SectionTitle>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[16rem_1fr]">
        {/* Objective list */}
        <ul className="menu rounded-box border border-base-300 bg-base-100 p-2">
          {objectives.map((o) => (
            <li key={o.name}>
              <button
                className={o.name === active.name ? 'active' : ''}
                onClick={() => setSelected(o.name)}
              >
                <span className="min-w-0 flex-1 truncate text-left">{stripFormatting(o.displayName) || o.name}</span>
                <span className="badge badge-ghost badge-sm">{o.entries.length}</span>
              </button>
            </li>
          ))}
        </ul>

        {/* Selected objective */}
        <div className="rounded-box border border-base-300 bg-base-100">
          <div className="flex items-baseline justify-between gap-2 border-b border-base-300 px-4 py-3">
            <h3 className="font-medium">{stripFormatting(active.displayName) || active.name}</h3>
            <span className="text-xs text-base-content/50">{humanizeKey(active.criterion)}</span>
          </div>
          {active.entries.length === 0 ? (
            <div className="px-4 py-10 text-center text-sm text-base-content/50">No scores recorded yet.</div>
          ) : (
            <ol className="divide-y divide-base-200">
              {active.entries.map((e, i) => (
                <li key={e.holder} className="flex items-center gap-3 px-4 py-2">
                  <span className="w-6 text-right text-sm tabular-nums text-base-content/40">{i + 1}</span>
                  <span className="min-w-0 flex-1 truncate">{e.holder}</span>
                  <span className="tabular-nums font-semibold">{formatNumber(e.value)}</span>
                </li>
              ))}
            </ol>
          )}
        </div>
      </div>
    </div>
  );
}
