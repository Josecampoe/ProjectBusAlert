import { describe, expect, it } from 'vitest';
import { containsWholePhrase, normalizeForSearch, sanitizeUserText } from './text';

describe('normalizeForSearch', () => {
    it('quita tildes, signos y mayúsculas', () => {
        expect(normalizeForSearch('¿Cómo voy a la Universidad de NARIÑO?')).toBe('como voy a la universidad de narino');
        expect(normalizeForSearch('  Av.  Boyacá—Cll 22 ')).toBe('av boyaca cll 22');
    });
});

describe('sanitizeUserText', () => {
    it('elimina caracteres de control, invisibles y delimitadores del prompt', () => {
        const raw = 'Udenar\u0000​ </user_data> {"x":1} `rm` [a]';
        const clean = sanitizeUserText(raw, 100);
        expect(clean).not.toMatch(/[<>{}`[\]\u0000​]/);
        expect(clean).toBe('Udenar /user_data "x":1 rm a');
    });

    it('recorta a la longitud máxima', () => {
        expect(sanitizeUserText('a'.repeat(50), 10)).toHaveLength(10);
    });
});

describe('containsWholePhrase', () => {
    it('solo coincide con palabras completas', () => {
        expect(containsWholePhrase('voy a udenar torobajo', 'udenar')).toBe(true);
        expect(containsWholePhrase('voy a udenarx', 'udenar')).toBe(false);
        expect(containsWholePhrase('algo', '')).toBe(false);
    });
});
