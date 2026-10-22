/**
 * Caché en memoria con expiración (TTL) e invalidación explícita.
 * Se usa para la red de transporte (rutas, paradas y recorridos), que
 * cambia solo cuando se vuelve a ejecutar el seed.
 */
export class MemoryCache {
  private readonly entries = new Map<string, { value: unknown; expiresAt: number }>();

  constructor(
    private readonly defaultTtlSeconds: number,
    private readonly now: () => number = Date.now,
  ) {}

  get(key: string): unknown {
    const entry = this.entries.get(key);
    if (!entry) return undefined;
    if (this.now() > entry.expiresAt) {
      this.entries.delete(key);
      return undefined;
    }
    return entry.value;
  }

  set(key: string, value: unknown, ttlSeconds: number = this.defaultTtlSeconds): void {
    this.entries.set(key, { value, expiresAt: this.now() + ttlSeconds * 1000 });
  }

  getOrSet<T>(key: string, factory: () => T, ttlSeconds?: number): T {
    const cached = this.get(key);
    if (cached !== undefined) return cached as T;
    const value = factory();
    this.set(key, value, ttlSeconds);
    return value;
  }

  invalidate(key?: string): void {
    if (key === undefined) this.entries.clear();
    else this.entries.delete(key);
  }
}
