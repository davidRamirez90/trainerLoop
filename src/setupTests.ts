import '@testing-library/jest-dom/vitest';

// Node 25+ exposes a process-global localStorage that is unavailable unless
// --localstorage-file is configured. Vitest can copy that unavailable value
// over jsdom's per-test storage, so install an isolated browser-compatible
// implementation for tests.
class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();

  get length(): number {
    return this.values.size;
  }

  clear(): void {
    this.values.clear();
  }

  getItem(key: string): string | null {
    return this.values.get(key) ?? null;
  }

  key(index: number): string | null {
    return Array.from(this.values.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.values.delete(key);
  }

  setItem(key: string, value: string): void {
    this.values.set(String(key), String(value));
  }
}

Object.defineProperty(window, 'localStorage', {
  configurable: true,
  value: new MemoryStorage(),
});
