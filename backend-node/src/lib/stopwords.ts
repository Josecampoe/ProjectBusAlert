/**
 * Palabras vacías (es/en) que se ignoran al buscar lugares en una frase
 * libre como "quiero ir a la universidad". Ya están normalizadas
 * (minúsculas y sin tildes).
 */
export const SEARCH_STOPWORDS: ReadonlySet<string> = new Set([
    // español
    'a', 'al', 'algo', 'como', 'con', 'cual', 'de', 'del', 'desde', 'donde', 'el', 'en', 'esta',
    'estoy', 'hacia', 'hasta', 'ir', 'la', 'las', 'lo', 'los', 'me', 'mi', 'para', 'por', 'que',
    'quiero', 'ruta', 'se', 'un', 'una', 'voy', 'y', 'bus', 'buses', 'tomo', 'tomar', 'llegar',
    'llego', 'cerca', 'parada', 'paradero', 'necesito', 'puedo', 'cuando', 'pasa',
    // inglés
    'the', 'to', 'from', 'i', 'want', 'go', 'how', 'do', 'get', 'where', 'is', 'near', 'take',
    'stop', 'route', 'what', 'which', 'need', 'am', 'at', 'in', 'of', 'my',
]);
