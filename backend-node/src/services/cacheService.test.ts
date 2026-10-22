import { describe, expect, it } from 'vitest';
import { MemoryCache } from './cacheService';

describe('MemoryCache', () => {
  it('guarda y expira según el TTL', () => {
    let now = 0;
    const cache = new MemoryCache(10, () => now);
    cache.set('a', 1);
    now = 10_000;
    expect(cache.get('a')).toBe(1);
    now = 10_001;
    expect(cache.get('a')).toBeUndefined();
  });

  it('getOrSet solo llama a la fábrica cuando no hay valor vigente', () => {
    let now = 0;
    let calls = 0;
    const cache = new MemoryCache(1, () => now);
    const factory = () => ++calls;
    expect(cache.getOrSet('k', factory)).toBe(1);
    expect(cache.getOrSet('k', factory)).toBe(1);
    now = 2_000;
    expect(cache.getOrSet('k', factory)).toBe(2);
  });

  it('invalida una clave o todo', () => {
    const cache = new MemoryCache(60);
    cache.set('a', 1);
    cache.set('b', 2);
    cache.invalidate('a');
    expect(cache.get('a')).toBeUndefined();
    expect(cache.get('b')).toBe(2);
    cache.invalidate();
    expect(cache.get('b')).toBeUndefined();
  });
});

describe('caché de la red de transporte', () => {
  it('el repositorio reutiliza la red hasta invalidarla', async () => {
    const { createTestContext } = await import('../test/helpers');
    const { repo } = createTestContext();
    const first = repo.getNetwork();
    expect(repo.getNetwork()).toBe(first);
    repo.invalidateCache();
    expect(repo.getNetwork()).not.toBe(first);
  });
});
