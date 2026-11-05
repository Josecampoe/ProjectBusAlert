/**
 * Corrección de errores de tipeo contra el vocabulario de paradas y lugares
 * ("coperativa" → "cooperativa"). Solo cambia palabras que no existen en el
 * vocabulario y que tienen un único candidato a la menor distancia.
 */

export interface SpellingOptions {
  /** Ediciones máximas (letra de más, de menos, cambiada o dos letras invertidas). 0 desactiva. */
  maxEdits: number;
  /** Largo mínimo de una palabra para intentar corregirla (las cortas dan falsos positivos). */
  minTermLength: number;
}

/** Distancia de edición con transposición de letras vecinas (Damerau restringida). */
export function editDistance(a: string, b: string, limit: number): number {
  if (Math.abs(a.length - b.length) > limit) return limit + 1;
  const rows = a.length + 1;
  const cols = b.length + 1;
  const d: number[][] = Array.from({ length: rows }, (_, i) =>
    Array.from({ length: cols }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)),
  );
  for (let i = 1; i < rows; i++) {
    let rowMin = Number.POSITIVE_INFINITY;
    for (let j = 1; j < cols; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      let value = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
        value = Math.min(value, d[i - 2][j - 2] + 1);
      }
      d[i][j] = value;
      rowMin = Math.min(rowMin, value);
    }
    if (rowMin > limit) return limit + 1;
  }
  return d[a.length][b.length];
}

/**
 * Corrige cada palabra de `normalized` (ya normalizado) que no esté en
 * `vocabulary` ni en `ignore`, si hay una sola palabra del vocabulario a la
 * menor distancia permitida. Devuelve el texto con las palabras corregidas.
 */
export function correctSpelling(
  normalized: string,
  vocabulary: ReadonlySet<string>,
  ignore: ReadonlySet<string>,
  options: SpellingOptions,
): string {
  if (options.maxEdits <= 0) return normalized;
  return normalized
    .split(' ')
    .map((term) => {
      if (term.length < options.minTermLength || vocabulary.has(term) || ignore.has(term)) {
        return term;
      }
      let best: string | undefined;
      let bestDistance = options.maxEdits + 1;
      let tie = false;
      for (const word of vocabulary) {
        const distance = editDistance(term, word, options.maxEdits);
        if (distance < bestDistance) {
          best = word;
          bestDistance = distance;
          tie = false;
        } else if (distance === bestDistance) {
          tie = true;
        }
      }
      return best !== undefined && !tie ? best : term;
    })
    .join(' ');
}
