import { beforeAll, describe, expect, it } from 'vitest';
import type { TransitNetwork } from '../repositories/transitRepository';
import { createTestContext } from '../test/helpers';
import { resolvePlace } from './placeResolver';

const OPTIONS = { minTermLength: 4, maxResults: 20 };
const WITH_SPELLING = { ...OPTIONS, spelling: { maxEdits: 1, minTermLength: 5 } };
let network: TransitNetwork;

beforeAll(() => {
  network = createTestContext().repo.getNetwork();
});

const names = (text: string) => resolvePlace(text, network, OPTIONS).stops.map((s) => s.name);

describe('resolvePlace', () => {
  it('encuentra un alias dentro de una frase libre', () => {
    expect(names('¿Cómo voy a UDENAR?')).toEqual(['Udenar Torobajo']);
    expect(names('quiero ir a la universidad cooperativa')).toEqual(['Universidad Cooperativa']);
  });

  it('ignora tildes y mayúsculas', () => {
    expect(names('estoy en el parque bolivar')).toEqual(['Parque Bolívar']);
    expect(names('PARQUE NARIÑO')).toEqual(['Parque Nariño']);
  });

  it('prefiere el nombre más específico', () => {
    // "Udenar" y "Universidad de Nariño" son alias de la misma parada; no debe confundir con Parque Nariño.
    expect(names('universidad de nariño')).toEqual(['Udenar Torobajo']);
  });

  it('acepta texto parcial contenido en un nombre', () => {
    expect(names('termin')).toEqual(['Terminal de Transportes']);
  });

  it('con texto parcial devuelve todas las coincidencias, las más parecidas primero', () => {
    expect(names('parque')).toEqual(['Parque Nariño', 'Parque Bolívar']);
  });

  it('no devuelve nada para lugares inexistentes o palabras vacías', () => {
    expect(names('quiero ir a la luna')).toEqual([]);
    expect(names('para el de la')).toEqual([]);
    expect(names('   ')).toEqual([]);
  });
});

describe('resolvePlace — lugares de referencia', () => {
  it('un lugar que no es parada devuelve sus paradas cercanas y su nombre', () => {
    const result = resolvePlace('voy a la cooperativa san vicente', network, OPTIONS);
    expect(result.stops.map((s) => s.name)).toEqual(['Parque Bolívar']);
    expect(result.landmark).toBe('Cooperativa San Vicente');
  });

  it('no confunde el lugar con la parada que comparte una palabra', () => {
    // "Universidad Cooperativa" también tiene "cooperativa"; gana el nombre más completo.
    expect(resolvePlace('cooperativa san vicente', network, OPTIONS).landmark).toBe(
      'Cooperativa San Vicente',
    );
  });

  it('una parada se devuelve sin lugar de referencia', () => {
    const result = resolvePlace('parque bolivar', network, OPTIONS);
    expect(result.landmark).toBeNull();
  });
});

describe('resolvePlace — errores de tipeo', () => {
  const typo = (text: string) =>
    resolvePlace(text, network, WITH_SPELLING).stops.map((s) => s.name);

  it('corrige una letra de menos, de más o cambiada', () => {
    expect(typo('universidad coperativa')).toEqual(['Universidad Cooperativa']);
    expect(typo('termminal')).toEqual(['Terminal de Transportes']);
    expect(typo('parque bolivr')).toEqual(['Parque Bolívar']);
  });

  it('corrige el nombre de un lugar de referencia', () => {
    const result = resolvePlace('coperativa san vicnte', network, WITH_SPELLING);
    expect(result.landmark).toBe('Cooperativa San Vicente');
  });

  it('sin corrección configurada no adivina', () => {
    expect(names('coperativa')).toEqual([]);
    // Sin corregir, "universidad" sola empata entre varias universidades.
    expect(names('universidad coperativa')).toContain('Udenar Torobajo');
  });

  it('no corrige palabras cortas ni lugares inexistentes', () => {
    expect(typo('quiero ir a la luna')).toEqual([]);
    expect(typo('montaña inexistente')).toEqual([]);
  });
});
