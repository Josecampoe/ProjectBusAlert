/**
 * Resuelve texto libre ("quiero ir a la universidad cooperativa") a
 * paradas de la base de datos. No usa IA: es determinista y solo devuelve
 * paradas que existen.
 *
 * Estrategia, de más a menos precisa:
 *   0. Texto (o texto sin palabras vacías) idéntico a un nombre o alias,
 *      sin importar su largo (permite siglas como "UCC").
 *   1. Nombre o alias de parada contenido como frase completa en el texto.
 *   2. Texto (sin palabras vacías) contenido en un nombre o alias (todas,
 *      ordenadas de la más parecida a la menos).
 *   3. Coincidencia por palabras sueltas significativas.
 * En los niveles 0, 1 y 3 se quedan solo las paradas con mejor puntaje.
 *
 * Además de paradas se buscan lugares de referencia (colegios, barrios...)
 * que no son parada: si gana un lugar, se devuelven sus paradas cercanas y
 * el nombre del lugar en `landmark`. Antes de buscar se corrigen errores de
 * tipeo contra el vocabulario de paradas y lugares.
 */
import type { AppConfig } from '../config/env';
import { correctSpelling, type SpellingOptions } from '../lib/spelling';
import { containsWholePhrase, normalizeForSearch } from '../lib/text';
import { SEARCH_STOPWORDS } from '../lib/stopwords';
import type { NetworkStop, TransitNetwork } from '../repositories/transitRepository';

export interface PlaceResolverOptions {
  minTermLength: number;
  maxResults: number;
  spelling?: SpellingOptions;
}

/** Opciones del buscador a partir de la configuración (las usan todos los endpoints). */
export function placeResolverOptions(config: AppConfig): PlaceResolverOptions {
  return {
    minTermLength: config.input.searchMinTermLength,
    maxResults: config.input.searchMaxResults,
    spelling: {
      maxEdits: config.input.typoMaxEdits,
      minTermLength: config.input.typoMinTermLength,
    },
  };
}

export type MatchKind = 'exact' | 'phrase_in_query' | 'query_in_name' | 'keywords';

export interface ResolvedPlace {
  stops: NetworkStop[];
  matchKind: MatchKind | null;
  /** Lugar de referencia (no es parada) al que corresponden `stops`, si ganó uno. */
  landmark: string | null;
}

const NO_MATCH: ResolvedPlace = { stops: [], matchKind: null, landmark: null };

/** Algo que se puede encontrar por nombre: una parada o un lugar con sus paradas cercanas. */
interface Searchable {
  name: string;
  /** Desempate estable: paradas primero, luego por id. */
  order: number;
  searchNames: string[];
  stops: NetworkStop[];
  landmark: string | null;
}

/** Peso que separa paradas de lugares en el orden de desempate. */
const PLACE_ORDER_OFFSET = 1_000_000_000;

function searchables(network: TransitNetwork): Searchable[] {
  const stops: Searchable[] = [...network.stops.values()].map((s) => ({
    name: s.name,
    order: s.id,
    searchNames: s.searchNames,
    stops: [s],
    landmark: null,
  }));
  const places: Searchable[] = network.places.map((p) => ({
    name: p.name,
    order: PLACE_ORDER_OFFSET + p.id,
    searchNames: p.searchNames,
    stops: p.stopIds.map((id) => network.stops.get(id)!),
    landmark: p.name,
  }));
  return [...stops, ...places];
}

function vocabulary(items: Searchable[]): Set<string> {
  return new Set(items.flatMap((i) => i.searchNames.flatMap((n) => n.split(' '))));
}

/** Junta las paradas de los ganadores. Si entre ellos hay paradas, los lugares se descartan. */
function toResult(winners: Searchable[], matchKind: MatchKind, maxResults: number): ResolvedPlace {
  const direct = winners.filter((w) => w.landmark === null);
  const chosen = direct.length > 0 ? direct : winners;
  const stops = [...new Map(chosen.flatMap((w) => w.stops).map((s) => [s.id, s])).values()];
  return {
    stops: stops.slice(0, maxResults),
    matchKind,
    landmark: direct.length > 0 ? null : (chosen[0]?.landmark ?? null),
  };
}

export function significantTerms(normalized: string, minTermLength: number): string[] {
  return normalized
    .split(' ')
    .filter((term) => term.length >= minTermLength && !SEARCH_STOPWORDS.has(term));
}

function bestBy(
  stops: Searchable[],
  score: (s: Searchable) => number,
  maxResults: number,
): Searchable[] {
  let best = 0;
  const scored = stops.map((s) => {
    const value = score(s);
    if (value > best) best = value;
    return { s, value };
  });
  if (best === 0) return [];
  return scored
    .filter((x) => x.value === best)
    .map((x) => x.s)
    .sort((a, b) => a.name.localeCompare(b.name) || a.order - b.order)
    .slice(0, maxResults);
}

export function resolvePlace(
  text: string,
  network: TransitNetwork,
  options: PlaceResolverOptions,
): ResolvedPlace {
  const stops = searchables(network);
  const raw = normalizeForSearch(text);
  if (raw.length === 0) return NO_MATCH;
  const query = options.spelling
    ? correctSpelling(raw, vocabulary(stops), SEARCH_STOPWORDS, options.spelling)
    : raw;
  const core = significantTerms(query, 1).join(' ');

  // 0. Coincidencia exacta.
  const exact = stops.filter((s) => s.searchNames.some((name) => name === query || name === core));
  if (exact.length > 0) {
    return toResult(
      bestBy(exact, () => 1, options.maxResults),
      'exact',
      options.maxResults,
    );
  }

  // 1. Un nombre/alias completo aparece en la frase. Gana el más largo (más específico).
  const phraseMatches = bestBy(
    stops,
    (s) =>
      Math.max(
        0,
        ...s.searchNames
          .filter(
            (name) => name.length >= options.minTermLength && containsWholePhrase(query, name),
          )
          .map((name) => name.length),
      ),
    options.maxResults,
  );
  if (phraseMatches.length > 0) {
    return toResult(phraseMatches, 'phrase_in_query', options.maxResults);
  }

  // 2. La frase (sin palabras vacías) está dentro de un nombre/alias.
  if (core.length >= options.minTermLength) {
    const contained = stops.filter((s) => s.searchNames.some((name) => name.includes(core)));
    if (contained.length > 0) {
      // Todas las que contienen el texto; primero las de nombre más corto (más parecidas).
      const sorted = contained
        .map((item) => ({ item, length: Math.min(...item.searchNames.map((n) => n.length)) }))
        .sort(
          (x, y) =>
            x.length - y.length ||
            x.item.name.localeCompare(y.item.name) ||
            x.item.order - y.item.order,
        )
        .map((x) => x.item);
      return toResult(sorted, 'query_in_name', options.maxResults);
    }
  }

  // 3. Palabras significativas en común.
  const terms = significantTerms(query, options.minTermLength);
  if (terms.length === 0) return NO_MATCH;
  const keywordMatches = bestBy(
    stops,
    (s) =>
      Math.max(
        0,
        ...s.searchNames.map(
          (name) => terms.filter((term) => containsWholePhrase(name, term)).length,
        ),
      ),
    options.maxResults,
  );
  return keywordMatches.length > 0
    ? toResult(keywordMatches, 'keywords', options.maxResults)
    : NO_MATCH;
}
