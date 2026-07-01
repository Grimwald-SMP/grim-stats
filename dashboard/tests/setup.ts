import '@testing-library/jest-dom/vitest';

// jsdom does not implement EventSource; provide a no-op so useSnapshot falls back to polling
// cleanly in component tests that mount the layout.
if (!('EventSource' in globalThis)) {
  // @ts-expect-error minimal stub for tests
  globalThis.EventSource = class {
    close() {
      /* noop */
    }
    addEventListener() {
      /* noop */
    }
    onerror: (() => void) | null = null;
  };
}
