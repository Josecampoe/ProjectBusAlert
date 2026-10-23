# Estado del plan de mejoras (diagnóstico del 2026-10-22)

Diagnóstico hecho al retomar el trabajo en la rama `fase-6-cierre`. Cada fila cita evidencia (archivo:línea o comando). Estados: **hecha** · **parcial** · **pendiente**. Las filas marcadas *(esta sesión)* se completaron o corrigieron durante el cierre.

## Punto de partida

- Ramas `fase-1-seguridad` … `fase-5-mobile` ya integradas en `mejoras-profesionales` (`git log --graph`: merges `cff69b5`, `e9dad80`, `da4778d`, `2c3ad08`, `08c5a19`).
- `fase-6-cierre` tenía cambios sin commitear (código muerto `isWithinRadius` + `Dockerfile`). Compilaban y pasaban las pruebas: commit `63ef2c4`.
- Verificación inicial: backend `lint`/`typecheck`/`format:check` OK, **126 pruebas OK**, `build` OK. Android `clean assembleDebug testDebugUnitTest lintDebug` → BUILD SUCCESSFUL, **75 pruebas OK** (wear 32, mobile 12, shared 31), lint sin hallazgos.

## Backend

| # | Requisito | Estado | Evidencia |
|---|---|---|---|
| 1 | Un solo SDK de Gemini, modelos en env y verificados | hecha | `package.json` solo `@google/genai`; `src/config/env.ts:72-73` (`GEMINI_MODEL_PRIMARY`/`FALLBACK`); modelos `gemini-3.8-flash` y `gemini-3.5-flash-lite` confirmados como estables en ai.google.dev/gemini-api/docs/models *(re-verificado esta sesión)* |
| 1 | Clave de aplicación, rate limit, CORS, helmet, límite de body | hecha | `src/app.ts:31,35,38,46,47`; `src/middleware/security.ts` (comparación en tiempo constante) |
| 1 | Validación con esquemas en todos los endpoints | hecha | `src/routes/schemas.ts`; `validate(...)` en cada ruta de `transitRoutes.ts` y `assistantRoutes.ts` |
| 1 | Errores centralizados, códigos estables, sin detalles internos | hecha | `src/middleware/errorHandler.ts:23`; `src/errors/AppError.ts` |
| 2 | Prompt injection: sistema separado, dato delimitado, saneo, salida validada, pruebas | hecha | `src/services/ai/prompts.ts`; `src/lib/text.ts:29`; `src/services/assistantService.test.ts:106` (3 pruebas de inyección) |
| 3 | BD como fuente única; contexto dinámico; sin rutas en el prompt | hecha | `assistantService.ts:247` (solo las opciones del planificador); `git grep` de códigos de ruta en `prompts.ts` → 0 |
| 4 | Fallback sin IA con planificador y búsqueda real | hecha | `assistantService.ts` (`source: 'planner'`); prueba "si la IA obedece al ataque… se descarta" |
| 5 | Tooling: Vitest + supertest, ESLint + Prettier, scripts, README coherente | hecha | `package.json` scripts `test`, `test:watch`, `lint`, `typecheck`, `build`, `seed`; `eslint.config.mjs` |
| 6 | Pruebas reales con datos de Pasto y BD en memoria desde fixtures | hecha | `src/test/helpers.ts`, `src/test/fixtures/data/`; planner (directas, sin ruta, origen = destino `planner.test.ts:56`), Haversine, búsqueda, validación, auth/429 (`app.security.test.ts:54`), parser IA con cliente simulado |
| 7 | SQLite con migraciones versionadas; seed idempotente y validado | hecha | `src/db/migrations.ts:11` (v1, v2); `importer.ts:308` (`ON CONFLICT … DO UPDATE`); seed ejecutado dos veces sobre la misma BD sin cambios *(esta sesión)* |
| 7 | La configuración del seed se valida en `config/` | **corregida** *(esta sesión)* | `seed.ts` leía `process.env` directamente → `loadSeedConfig` en `src/config/env.ts`, con pruebas |
| 7 | `npm run data:validate` funciona sin `.env` | **corregida** *(esta sesión)* | Fallaba con el `.env` local (sin `DATA_DIR`); ahora apunta a `./data` y acepta `-- --data-dir` |
| 8 | Planificador con transbordo, costos configurables, alternativas ordenadas | hecha | `src/services/planner.ts:181`; `PLANNER_*` en `env.ts` |
| 9 | ETA determinista etiquetada + `ArrivalTimeProvider` | hecha | `src/services/arrivalTime.ts:32,35` |
| 10 | `GET /api/stops/nearby` | hecha | `src/routes/transitRoutes.ts`; contratos `stops_nearby*.json` *(esta sesión)* |
| 11 | Caché en memoria con TTL e invalidación | hecha | `src/services/cacheService.ts:8`; `transitRepository.ts` (`getNetwork`, `invalidateCache`) |
| 12 | CI de GitHub Actions del backend | hecha | `.github/workflows/ci.yml` job `backend` (ci, lint, format, typecheck, test, build, seed) |
| — | Imagen Docker de producción | hecha *(esta sesión)* | `docker build` + `docker run`: `/health` 200, `/api/routes` sin clave 401, con clave 200 |

## Frontend / Android

| # | Requisito | Estado | Evidencia |
|---|---|---|---|
| 1 | Sin `GeminiConfig.API_KEY` ni rastro de key; Remote por defecto, demo solo por flag en debug | hecha | `git grep GeminiConfig\|AIza` → 0; `SharedDependencies.kt:25-40`; `wear/build.gradle.kts:28,33`; `SharedDependenciesTest` |
| 1 | ¿Hubo key real en el historial? | hecha | `git log -p --all \| grep -cE "AIza[0-9A-Za-z_-]{20,}"` → 0 |
| 2 | Permiso en runtime con rationale y denegación; FusedLocationProvider | hecha | `MainActivity.kt:44`; `ui/WearApp.kt` (`LocationRationaleScreen`, `locationLauncher`) |
| 2 | Parada más cercana vía `/api/stops/nearby` | **corregida** *(esta sesión)* | Antes el reloj solo mandaba lat/lng a `/api/voice`. Ahora `TransitRepository.nearbyStops` + `MainWearViewModel.startQuery`; sin paradas ubicables pide el lugar por voz/texto |
| 2 | Alternativa por voz/texto sin permiso | hecha | `MainWearViewModel` (`ASKING_LOCATION`/`TYPING_LOCATION`) |
| 3 | Tests: ViewModel (Turbine, coroutines-test, fakes), Remote con MockWebServer, UI Compose | hecha | `MainWearViewModelTest` (18), `RemoteTripAssistantRepositoryTest`, `RemoteTransitRepositoryTest`, `ScreensUiTest` (Robolectric) |
| 4 | Pantallas en archivos separados (< 300 líneas) | hecha | `wear/.../ui/screens/` 7 archivos; el mayor de UI es `WearApp.kt` (~265) |
| 4 | ScalingLazyColumn, TimeText, PositionIndicator | hecha | `ui/components/Scaffolds.kt:46,48,63` |
| 4 | Tema por tokens, color de ruta desde datos, contraste | hecha | `theme/Palette.kt`, `Tokens.kt`; `ThemeTest` (WCAG ≥ 4,5) |
| 4 | Estados completos, accesibilidad, i18n es/en, códigos de error traducidos | hecha | `MessageScreens.kt`, `ProgressScreens.kt`; `values/` y `values-en/` con las mismas claves; `ui/Localization.kt` |
| 4 | Preferencias con DataStore, previews | hecha | `shared/.../preferences/`; `ui/preview/WearPreviews.kt` (nombres demo *(esta sesión)*) |
| 5 | App móvil: rutas, paradas, mapa (clave fuera del código), historial, ajustes, sync Data Layer con contratos en `shared`, tests | hecha | `mobile/.../ui/`; `RouteMap.kt:47`; `shared/.../sync/`; `MobileViewModelsTest`, `MobileUiTest`, `SyncTest` |
| 6 | CI de Android | hecha | `.github/workflows/ci.yml` job `android` (assemble, tests, lint) |
| 7 | Carpeta `BusAlertFrontend` | hecha | renombrada en `49ad0ac` |

## Fase 6 — Cierre

| Tarea | Estado | Evidencia |
|---|---|---|
| README raíz y por módulo con Mermaid, env, ejecución desde cero, despliegue | hecha *(esta sesión)* | `README.md`, `backend-node/README.md` (flujo de `/api/voice` y "Despliegue"), `BusAlertFrontend/README.md` |
| Auditoría "nada quemado" | hecha *(esta sesión)* | `docs/AUDITORIA.md` |
| Suite completa | hecha *(esta sesión)* | `docs/VERIFICACION.md` (sección Fase 6) |
| Revisión de código muerto, TODOs, dependencias, huérfanos | hecha *(esta sesión)* | ver `docs/AUDITORIA.md` § Revisión final |
| `PENDIENTES.md` solo con lo que depende del dueño | hecha | `docs/PENDIENTES.md` |
