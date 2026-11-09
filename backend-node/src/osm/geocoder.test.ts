import { describe, expect, it } from 'vitest';
import { geocodeStops, parseStreets, type RouteStopRef, type StopToLocate } from './geocoder';
import { buildOverpassQuery, toSnapshot, type OsmFeature, type OsmSnapshot } from './overpass';

const feature = (id: string, name: string, kind: string, lat: number, lon: number): OsmFeature => ({
  id,
  names: [name],
  kind,
  lat,
  lon,
});

/** Calles en cruz: la Calle 18 va de oeste a este y la Carrera 29 de sur a norte. */
const snapshot: OsmSnapshot = {
  source: 'openstreetmap',
  attribution: 'test',
  fetched_at: '2026-11-09T00:00:00.000Z',
  bbox: [1.1, -77.4, 1.32, -77.15],
  features: [
    feature('node/1', 'Fátima', 'place=neighbourhood', 1.203, -77.2732),
    feature(
      'node/2',
      'Universidad Cooperativa de Colombia',
      'amenity=university',
      1.2298,
      -77.2899,
    ),
    feature('node/3', 'Villaflor II Mz. 12', 'place=city_block', 1.2018, -77.257),
    feature('node/4', 'Las Cuadras', 'place=neighbourhood', 1.2185, -77.2797),
    // Dos "Bellavista" en extremos opuestos de la ciudad.
    feature('node/5', 'Bellavista', 'place=neighbourhood', 1.19, -77.26),
    feature('node/6', 'Bellavista', 'place=neighbourhood', 1.24, -77.3),
    feature('node/7', 'La Laguna', 'place=hamlet', 1.2041, -77.2096),
    feature('node/8', 'Lorenzo', 'place=neighbourhood', 1.1975, -77.2651),
    // Homónimo lejano de una parada del recorrido.
    feature('node/9', 'Villa María', 'place=hamlet', 1.2525, -77.3234),
    feature('node/10', 'El Dorado', 'place=neighbourhood', 1.2248, -77.2873),
  ],
  streets: [
    {
      id: 'way/1',
      name: 'Calle 18',
      points: [
        [1.2183, -77.284],
        [1.2183, -77.276],
      ],
    },
    {
      id: 'way/2',
      name: 'Carrera 29',
      points: [
        [1.214, -77.2804],
        [1.222, -77.2804],
      ],
    },
    {
      id: 'way/3',
      name: 'Calle 19',
      points: [
        [1.2187, -77.284],
        [1.2187, -77.276],
      ],
    },
    {
      id: 'way/4',
      name: 'Avenida de Las Américas',
      points: [
        [1.2183, -77.2832],
        [1.21, -77.2832],
      ],
    },
  ],
};

const stop = (
  name: string,
  aliases: string[] = [],
  externalId: string | null = null,
): StopToLocate => ({
  external_id: externalId,
  name,
  aliases,
  neighborhood: null,
});

function locateOne(name: string, aliases: string[] = []) {
  const result = geocodeStops([stop(name, aliases)], snapshot);
  return { located: result.located.at(0), notFound: result.notFound.at(0) };
}

describe('parseStreets', () => {
  it.each([
    ['Calle 18 Carrera 29', ['calle18', 'carrera29'], ''],
    ['Cra 32B Cl 6 Sur', ['carrera32b', 'calle6sur'], ''],
    ['Av. Las Américas Calle 16', ['avenidaamericas', 'calle16'], ''],
    ['Avenida Carrera 27', ['carrera27'], ''],
    ['Las Cuadras Calle 19', ['calle19'], 'las cuadras'],
    ['Villa Flor II Carrera 9 Este', ['carrera9este'], 'villa flor ii'],
    ['Fátima', [], 'fatima'],
  ])('%s', (name, streets, rest) => {
    expect(parseStreets(name)).toEqual({ streets, rest });
  });
});

describe('geocodeStops', () => {
  it('ubica un barrio por su nombre sin importar tildes ni mayúsculas', () => {
    const { located } = locateOne('FATIMA');
    expect(located).toMatchObject({ latitude: 1.203, longitude: -77.2732, method: 'place' });
  });

  it('quita palabras genéricas como "Parroquia"', () => {
    expect(locateOne('Parroquia Fátima').located?.osm_ids).toEqual(['node/1']);
  });

  it('reconoce el comienzo de un nombre largo', () => {
    expect(locateOne('Universidad Cooperativa').located?.matched).toContain(
      'Universidad Cooperativa de Colombia',
    );
  });

  it('une palabras separadas y entiende "Manzana" como "Mz."', () => {
    expect(locateOne('Villa Flor II Manzana 12').located?.osm_ids).toEqual(['node/3']);
  });

  it('usa los alias de la parada', () => {
    expect(locateOne('Barrio del Norte', ['Lorenzo']).located?.osm_ids).toEqual(['node/8']);
  });

  it('ubica el cruce de dos calles', () => {
    const { located } = locateOne('Calle 18 Carrera 29');
    expect(located?.method).toBe('intersection');
    expect(located?.latitude).toBeCloseTo(1.2183, 4);
    expect(located?.longitude).toBeCloseTo(-77.2804, 4);
  });

  it('reconoce avenidas abreviadas', () => {
    expect(locateOne('Av. Las Américas Calle 18').located?.method).toBe('intersection');
  });

  it('ajusta un lugar a la calle que acompaña su nombre', () => {
    const { located } = locateOne('Las Cuadras Calle 19');
    expect(located?.method).toBe('place_on_street');
    expect(located?.latitude).toBeCloseTo(1.2187, 4);
  });

  it('no ubica nombres que coinciden con lugares lejanos entre sí', () => {
    expect(locateOne('Bellavista').notFound?.reason).toMatch(/varios lugares/);
  });

  it('no ubica una calle sola porque no dice en qué punto', () => {
    expect(locateOne('Calle 18').notFound?.reason).toMatch(/genérico/);
  });

  it('no ubica "Vía X" en el lugar X', () => {
    expect(locateOne('Vía La Laguna', ['La Laguna']).notFound?.reason).toMatch(/tramo de vía/);
  });

  it('reporta lo que no existe en OpenStreetMap', () => {
    expect(locateOne('Lugar Inventado').notFound?.reason).toMatch(/no aparece/);
  });

  describe('con recorridos', () => {
    const route = (...names: string[]): RouteStopRef[] =>
      names.map((n) => ({ stop_external_id: null, stop_name: n }));
    const stops = ['Universidad Cooperativa', 'Villa María', 'El Dorado', 'Fátima'].map((n) =>
      stop(n),
    );

    it('descarta la parada que obliga a un desvío enorme (homónimo lejano)', () => {
      const result = geocodeStops(stops, snapshot, [
        route('Universidad Cooperativa', 'Villa María', 'El Dorado', 'Fátima'),
      ]);
      expect(result.located.map((s) => s.name)).toEqual([
        'Universidad Cooperativa',
        'El Dorado',
        'Fátima',
      ]);
      expect(result.notFound).toEqual([
        expect.objectContaining({ name: 'Villa María', reason: expect.stringMatching(/lejos/) }),
      ]);
    });

    it('descarta las paradas que no están en ningún recorrido', () => {
      const result = geocodeStops(stops, snapshot, [route('Universidad Cooperativa', 'El Dorado')]);
      expect(result.located.map((s) => s.name)).toEqual(['Universidad Cooperativa', 'El Dorado']);
      expect(result.notFound.map((s) => s.reason)).toEqual([
        expect.stringMatching(/ningún recorrido/),
        expect.stringMatching(/ningún recorrido/),
      ]);
    });

    it('relaciona las paradas del recorrido por código', () => {
      const coded = [stop('Universidad Cooperativa', [], '1360'), stop('El Dorado', [], '1400')];
      const result = geocodeStops(coded, snapshot, [
        [
          { stop_external_id: '1360', stop_name: 'Otro nombre' },
          { stop_external_id: '1400', stop_name: 'Otro nombre 2' },
        ],
      ]);
      expect(result.located).toHaveLength(2);
    });
  });
});

describe('toSnapshot', () => {
  it('separa calles con trazado de lugares con centro y descarta lo que no tiene nombre', () => {
    const result = toSnapshot(
      [
        {
          type: 'node',
          id: 1,
          lat: 1.2,
          lon: -77.27,
          tags: { name: 'Fátima', place: 'neighbourhood', alt_name: 'Barrio Fátima' },
        },
        {
          type: 'way',
          id: 2,
          center: { lat: 1.23, lon: -77.29 },
          tags: { name: 'UCC', amenity: 'university' },
        },
        {
          type: 'way',
          id: 3,
          geometry: [
            { lat: 1.2, lon: -77.2 },
            { lat: 1.21, lon: -77.2 },
          ],
          tags: { name: 'Calle 18', highway: 'residential' },
        },
        { type: 'node', id: 4, lat: 1.2, lon: -77.2, tags: { amenity: 'bench' } },
        {
          type: 'node',
          id: 1,
          lat: 1.2,
          lon: -77.27,
          tags: { name: 'Fátima', place: 'neighbourhood' },
        },
      ],
      new Date('2026-11-09T00:00:00Z'),
    );
    expect(result.features).toEqual([
      {
        id: 'node/1',
        names: ['Fátima', 'Barrio Fátima'],
        kind: 'place=neighbourhood',
        lat: 1.2,
        lon: -77.27,
      },
      { id: 'way/2', names: ['UCC'], kind: 'amenity=university', lat: 1.23, lon: -77.29 },
    ]);
    expect(result.streets).toEqual([
      {
        id: 'way/3',
        name: 'Calle 18',
        points: [
          [1.2, -77.2],
          [1.21, -77.2],
        ],
      },
    ]);
    expect(result.fetched_at).toBe('2026-11-09T00:00:00.000Z');
  });

  it('la consulta pide lugares con nombre y calles con trazado dentro del área', () => {
    const query = buildOverpassQuery([1, 2, 3, 4]);
    expect(query).toContain('nwr["name"]["place"](1,2,3,4);');
    expect(query).toContain('way["highway"]["name"](1,2,3,4);');
    expect(query).toContain('out tags geom;');
  });
});
