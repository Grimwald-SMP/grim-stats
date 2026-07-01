import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { ThemeMenu } from '../src/components/ThemeMenu';
import { ThemeProvider, THEMES } from '../src/state/ThemeContext';

function renderMenu() {
  return render(
    <ThemeProvider>
      <ThemeMenu />
    </ThemeProvider>,
  );
}

describe('ThemeMenu', () => {
  beforeEach(() => {
    // Pre-seed a stored theme so the provider does not attempt a network fetch on mount.
    localStorage.setItem('grimstats.theme', 'grimstats');
    document.documentElement.removeAttribute('data-theme');
  });

  it('applies the stored theme to the document', () => {
    renderMenu();
    expect(document.documentElement.getAttribute('data-theme')).toBe('grimstats');
  });

  it('lists every bundled theme', async () => {
    renderMenu();
    // Each theme appears as an option button.
    for (const t of THEMES) {
      expect(screen.getAllByText(t, { exact: false }).length).toBeGreaterThan(0);
    }
  });

  it('switches theme on selection', async () => {
    renderMenu();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'nord' }));
    expect(document.documentElement.getAttribute('data-theme')).toBe('nord');
    expect(localStorage.getItem('grimstats.theme')).toBe('nord');
  });
});
