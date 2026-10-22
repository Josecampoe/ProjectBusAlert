/**
 * Utilidades de texto: normalización para búsqueda y saneamiento de la
 * entrada de usuario antes de enviarla a la IA.
 */

const DIACRITICS = /[̀-ͯ]/g;
const NON_ALPHANUMERIC = /[^\p{L}\p{N}]+/gu;
const CONTROL_CHARS = /[\p{Cc}\p{Cf}]/gu;
const WHITESPACE_RUN = /\s+/g;
/** Caracteres que podrían cerrar o imitar los delimitadores del prompt. */
const PROMPT_DELIMITER_CHARS = /[<>`{}[\]]/g;

/** Minúsculas, sin tildes, solo letras/números separados por un espacio. */
export function normalizeForSearch(text: string): string {
  return text
    .normalize('NFD')
    .replace(DIACRITICS, '')
    .toLowerCase()
    .replace(NON_ALPHANUMERIC, ' ')
    .trim()
    .replace(WHITESPACE_RUN, ' ');
}

/**
 * Limpia texto libre del usuario: quita caracteres de control y de formato
 * invisibles, los caracteres usados como delimitadores del prompt, colapsa
 * espacios y recorta a `maxLength`.
 */
export function sanitizeUserText(text: string, maxLength: number): string {
  return text
    .normalize('NFC')
    .replace(CONTROL_CHARS, ' ')
    .replace(PROMPT_DELIMITER_CHARS, ' ')
    .replace(WHITESPACE_RUN, ' ')
    .trim()
    .slice(0, maxLength);
}

/** ¿`needle` aparece en `haystack` como palabras completas? (ambos ya normalizados) */
export function containsWholePhrase(haystack: string, needle: string): boolean {
  if (needle.length === 0) return false;
  return ` ${haystack} `.includes(` ${needle} `);
}
