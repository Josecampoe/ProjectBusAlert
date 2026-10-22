/**
 * Resuelve texto libre ("quiero ir a la universidad cooperativa") a
 * paradas de la base de datos. No usa IA: es determinista y solo devuelve
 * paradas que existen.
 *
 * Estrategia, de más a menos precisa:
 *   0. Texto (o texto sin palabras vacías) idéntico a un nombre o alias,
 *      sin importar su largo (permite siglas como "UCC").
 *   1. Nombre o alias de parada contenido como frase completa en el texto.
 *   2. Texto (sin palabras vacías) contenido en un nombre o alias.
 *   3. Coincidencia por palabras sueltas significativas.
 * En cada nivel se quedan solo las paradas con mejor puntaje.
 */
import { containsWholePhrase, normalizeForSearch } from '../lib/text';
import { SEARCH_STOPWORDS } from '../lib/stopwords';
import type { NetworkStop, TransitNetwork } from '../repositories/transitRepository';

export interface PlaceResolverOptions {
  minTermLength: number;
  maxResults: number;
}

export type MatchKind = 'exact' | 'phrase_in_query' | 'query_in_name' | 'keywords';

export interface ResolvedPlace {
  stops: NetworkStop[];
  matchKind: MatchKind | null;
}

const NO_MATCH: ResolvedPlace = { stops: [], matchKind: null };

export function significantTerms(normalized: string, minTermLength: number): string[] {
  return normalized
    .split(' ')
    .filter((term) => term.length >= minTermLength && !SEARCH_STOPWORDS.has(term));
}

function bestBy(
  stops: NetworkStop[],
  score: (s: NetworkStop) => number,
  maxResults: number,
): NetworkStop[] {
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
    .sort((a, b) => a.name.localeCompare(b.name) || a.id - b.id)
    .slice(0, maxResults);
}

export function resolvePlace(
  text: string,
  network: TransitNetwork,
  options: PlaceResolverOptions,
): ResolvedPlace {
  const query = normalizeForSearch(text);
  if (query.length === 0) return NO_MATCH;
  const stops = [...network.stops.values()];
  const core = significantTerms(query, 1).join(' ');

  // 0. Coincidencia exacta.
  const exact = stops.filter((s) => s.searchNames.some((name) => name === query || name === core));
  if (exact.length > 0) {
    return { stops: bestBy(exact, () => 1, options.maxResults), matchKind: 'exact' };
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
  if (phraseMatches.length > 0) return { stops: phraseMatches, matchKind: 'phrase_in_query' };

  // 2. La frase (sin palabras vacías) está dentro de un nombre/alias.
  if (core.length >= options.minTermLength) {
    const contained = stops.filter((s) => s.searchNames.some((name) => name.includes(core)));
    if (contained.length > 0) {
      return {
        stops: bestBy(
          contained,
          (s) => 1 / Math.min(...s.searchNames.map((n) => n.length)),
          options.maxResults,
        ),
        matchKind: 'query_in_name',
      };
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
  return keywordMatches.length > 0 ? { stops: keywordMatches, matchKind: 'keywords' } : NO_MATCH;
}
