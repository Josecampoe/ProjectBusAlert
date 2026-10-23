# Auditoría "nada quemado" (2026-10-22)

Búsquedas sistemáticas sobre el código versionado (`git grep`), excluyendo pruebas salvo donde se indica. Resultado final: **limpia**; las excepciones aceptadas están justificadas abajo.

## Secretos

| Búsqueda | Alcance | Resultado |
|---|---|---|
| `AIza[0-9A-Za-z_-]{20,}`, `sk-[A-Za-z0-9]{20,}`, `BEGIN … PRIVATE` | todo el repo | 0 |
| `AIza[0-9A-Za-z_-]{20,}` en `git log -p --all` | historial completo | 0 → no hace falta rotar ninguna key |
| `GeminiConfig`, `gemini-` en Android | `*.kt`, `*.kts`, `*.xml` | 0 |
| Claves en `local.properties` / `.env` | — | ambos en `.gitignore`; las claves llegan por `BuildConfig`/`manifestPlaceholders` y por variables de entorno |

## Backend (`backend-node/src`, sin pruebas)

| Búsqueda | Resultado | Acción |
|---|---|---|
| `process.env` fuera de `config/env.ts` | 2 en `db/seed.ts` | **Corregido:** `loadSeedConfig()` en `config/env.ts` valida `DB_PATH`/`DATA_DIR` con zod |
| URLs `https?://` | 0 | — |
| Nombres de modelo `gemini-` fuera de `config/env.ts` | 0 | — |
| Coordenadas literales (`1.xxx`, `-77.xxx`) | 0 | — |
| Códigos/nombres de ruta en prompts | 0 | el contexto sale de la BD |
| Números sueltos en la lógica | `* 1000` (caché), `6371e3` y `/ 180` (Haversine) | **Corregido:** `MS_PER_SECOND`, `EARTH_RADIUS_METERS`, `DEGREES_IN_HALF_TURN` |

Excepciones aceptadas (constantes con nombre, no configuración de despliegue):

- `HttpStatus`, códigos HTTP de `geminiClient.ts` y niveles del logger: son parte del protocolo.
- `AI_OUTPUT_LIMITS` (`prompts.ts`): largo máximo de los textos de la IA para que quepan en el reloj; es un contrato con la UI, no un ajuste del servidor.
- Rangos de latitud/longitud (±90/±180) y `MAX_CODE_LENGTH` en los esquemas: validación de formato.
- `METERS_PER_KM`, `MINUTES_PER_HOUR`, `AVERAGE_WAIT_FRACTION` (`arrivalTime.ts`): conversiones y la definición de espera media (frecuencia / 2), documentada en D-023.
- Valores por defecto de `EnvSchema`: todos sobrescribibles por variable de entorno y documentados en `.env.example`.

## Android (`*/src/main`)

| Búsqueda | Resultado | Acción |
|---|---|---|
| `Color(0x…)` fuera de los archivos de tema | 0 (solo `wear/theme/Palette.kt` y `mobile/theme/Theme.kt`) | — |
| `N.dp` / `N.sp` fuera de `theme/` | 0 | — |
| `Text("…")`, `text = "…"`, `contentDescription = "…"` | 0 | — |
| Cadenas con espacios (posible texto de UI) | solo mensajes de excepción internos (`MalformedResponseException`), que nunca se muestran | — |
| Nombres de paradas reales en código de producción | `ui/preview/WearPreviews.kt` usaba "Udenar Torobajo", "Parque Nariño"… | **Corregido:** usa el set demo (`DEMO-1`, "Demo Torobajo"…) igual que `backend-node/data/sample/` |
| URLs | solo el valor por defecto de debug `http://10.0.2.2:3000/` en `build.gradle.kts` (dirección del emulador), sobrescribible por `local.properties`/entorno; release exige `https://` configurado | aceptado |
| Timeouts / límites | `SharedConfig.kt` (`CONNECT_TIMEOUT_MS`, `READ_TIMEOUT_MS`), `LocationDefaults.TIMEOUT_MS`, `StopsViewModel.DEBOUNCE_MS`: constantes con nombre en objetos | aceptado |
| `strings.xml` es / en | wear 82 = 82, mobile 55 = 55 claves; 0 recursos sin uso | — |

Los datos de prueba (`wear/src/test/.../TestData.kt`, `mobile/src/test/...`) usan nombres de Pasto con códigos ficticios `FX*`, igual que los fixtures del backend: es intencional (las pruebas deben parecerse a la ciudad real sin ser datos de transporte).

## Revisión final

| Revisión | Resultado |
|---|---|
| `TODO`/`FIXME`/`HACK` en backend y Android | 0 |
| Código muerto | `isWithinRadius` (sin uso) eliminado en `63ef2c4`; carpeta vacía `backend-node/src/controllers/` eliminada |
| Dependencias de producción del backend | las 7 se importan en `src/` |
| Recursos de texto Android sin uso | 0 |
| Archivo de datos viejo `backend-node/data/busalert.db` | ignorado por git, sin uso; se deja para que el dueño decida (ver PENDIENTES) |
| Coherencia README ↔ scripts | `data:validate` corregido en el README; despliegue documentado y probado con Docker |
