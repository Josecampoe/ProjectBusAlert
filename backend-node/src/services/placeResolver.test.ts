import { beforeAll, describe, expect, it } from 'vitest';
import type { TransitNetwork } from '../repositories/transitRepository';
import { createTestContext } from '../test/helpers';
import { resolvePlace } from './placeResolver';

const OPTIONS = { minTermLength: 4, maxResults: 20 };
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

  it('no devuelve nada para lugares inexistentes o palabras vacías', () => {
    expect(names('quiero ir a la luna')).toEqual([]);
    expect(names('para el de la')).toEqual([]);
    expect(names('   ')).toEqual([]);
  });
});
