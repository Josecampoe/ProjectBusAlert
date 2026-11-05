import { describe, expect, it } from 'vitest';
import { correctSpelling, editDistance } from './spelling';

const OPTIONS = { maxEdits: 1, minTermLength: 5 };
const VOCAB = new Set(['cooperativa', 'universidad', 'terminal', 'nacional', 'liceo']);
const IGNORE = new Set(['quiero']);

describe('editDistance', () => {
  it('cuenta letras de más, de menos, cambiadas e invertidas', () => {
    expect(editDistance('coperativa', 'cooperativa', 2)).toBe(1);
    expect(editDistance('terminall', 'terminal', 2)).toBe(1);
    expect(editDistance('termibal', 'terminal', 2)).toBe(1);
    expect(editDistance('temrinal', 'terminal', 2)).toBe(1);
    expect(editDistance('terminal', 'terminal', 2)).toBe(0);
  });

  it('corta en cuanto supera el límite', () => {
    expect(editDistance('abc', 'abcdefgh', 1)).toBe(2);
    expect(editDistance('zzzzzzzz', 'terminal', 1)).toBe(2);
  });
});

describe('correctSpelling', () => {
  it('corrige solo las palabras desconocidas', () => {
    expect(correctSpelling('voy a la coperativa', VOCAB, IGNORE, OPTIONS)).toBe(
      'voy a la cooperativa',
    );
    expect(correctSpelling('universidad', VOCAB, IGNORE, OPTIONS)).toBe('universidad');
  });

  it('no toca palabras vacías, cortas ni con empate entre candidatos', () => {
    expect(correctSpelling('quiera', VOCAB, IGNORE, OPTIONS)).toBe('quiera');
    expect(correctSpelling('quiero', VOCAB, IGNORE, OPTIONS)).toBe('quiero');
    expect(correctSpelling('lice', VOCAB, IGNORE, OPTIONS)).toBe('lice');
    // "nacinal" queda a 1 de "nacional"; "terminal" está lejos: sin empate, se corrige.
    expect(correctSpelling('nacinal', VOCAB, IGNORE, OPTIONS)).toBe('nacional');
    const tied = new Set(['casas', 'casos']);
    expect(correctSpelling('casis', tied, IGNORE, OPTIONS)).toBe('casis');
  });

  it('con 0 ediciones no corrige nada', () => {
    expect(correctSpelling('coperativa', VOCAB, IGNORE, { ...OPTIONS, maxEdits: 0 })).toBe(
      'coperativa',
    );
  });
});
