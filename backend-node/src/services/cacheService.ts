/**
 * Servicio de cache en memoria para optimizar consultas recurrentes de rutas y paradas
 */
class MemoryCacheService {
  private cache = new Map<string, { value: any; expiry: number }>();

  set(key: string, value: any, ttlSeconds: number = 300): void {
    const expiry = Date.now() + ttlSeconds * 1000;
    this.cache.set(key, { value, expiry });
  }

  get<T>(key: string): T | null {
    const item = this.cache.get(key);
    if (!item) return null;
    if (Date.now() > item.expiry) {
      this.cache.delete(key);
      return null;
    }
    return item.value as T;
  }

  clear(): void {
    this.cache.clear();
  }
}

export const memoryCache = new MemoryCacheService();
