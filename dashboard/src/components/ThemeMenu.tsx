import { Palette } from "lucide-react";
import { useTheme } from '../state/ThemeContext';

/** Compact theme picker. Lets any visitor preview the palette locally; admins set the default. */
export function ThemeMenu() {
  const { theme, setTheme, themes } = useTheme();
  return (
    <div className="dropdown dropdown-end">
      <button tabIndex={0} className="btn btn-ghost btn-sm gap-2" aria-label="Choose theme">
          <Palette size={15} aria-hidden />
        <span className="hidden md:inline capitalize">{theme}</span>
      </button>
      <ul
        tabIndex={0}
        className="menu dropdown-content mt-2 max-h-80 w-44 overflow-y-auto rounded-box bg-base-100 p-2 shadow border border-base-300 z-40"
      >
        {themes.map((t) => (
          <li key={t}>
            <button className={t === theme ? 'active capitalize' : 'capitalize'} onClick={() => setTheme(t)}>
              {t}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
