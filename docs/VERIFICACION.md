# Registro de verificación

Salidas resumidas de los comandos ejecutados para cerrar cada fase.
Entorno: Windows 11, Node 24.19.0, JDK 17 (JBR 17.0.14), Android SDK 34.

## Fase 1 — Seguridad y arquitectura (2026-10-22)

| Verificación | Resultado |
|---|---|
| Búsqueda `AIza…`, `gemini`, `generativelanguage` en el código del cliente (`*.kt, *.kts, *.xml, *.properties`) | 0 coincidencias (solo la clave ficticia de las pruebas) |
| Misma búsqueda dentro de `wear-debug.apk` y `mobile-debug.apk` (build **limpio**) | 0 coincidencias |
| Historial de git (`git log -p --all`) con el patrón `AIza[0-9A-Za-z_-]{20,}` | 0 coincidencias |
| `curl /api/routes` sin `X-API-Key` | `401 UNAUTHORIZED` |
| Exceso de tasa (prueba `RATE_LIMIT_MAX=2`) | `429 RATE_LIMITED` |
| Reloj ↔ backend | Contratos `contracts/voice/*.json` generados por el backend y parseados por `shared` (9 pruebas OK) |
| `npm test` (backend) | 69 pruebas OK |
| `./gradlew clean assembleDebug testDebugUnitTest` | BUILD SUCCESSFUL; 20 pruebas OK |
| `./gradlew :wear:preReleaseBuild` sin URL release | Falla con "Define busalert.release.backendUrl (https://...)" |

> Nota: un build incremental dejó clases borradas dentro del APK. Por eso las verificaciones de APK y de lint se hacen siempre con `clean`.

## Fase 2 — Tooling y pruebas (2026-10-22), en un clon limpio

Clon nuevo de la rama `fase-2-tooling` en una carpeta temporal, siguiendo los comandos del README:

| Comando | Resultado |
|---|---|
| `npm ci` | OK, 0 vulnerabilidades |
| `cp .env.example .env` + `API_KEYS` + `npm run seed` | 1 ruta, 86 paradas, 87 entradas de recorrido; 0 errores |
| `npm run lint` · `format:check` · `typecheck` | OK |
| `npm test` | 12 archivos, **99 pruebas OK** (cobertura de líneas 92,97%) |
| `npm run build` + `node dist/index.js` + `POST /api/voice` | Responde con plan E1 (planificador) |
| `./gradlew assembleDebug testDebugUnitTest lintDebug` | BUILD SUCCESSFUL; **28 pruebas OK**; lint: "No issues found" en los 3 módulos |

## Fase 3 — Datos y GPS (2026-10-22)

| Verificación | Resultado |
|---|---|
| Prueba de aceptación `src/gps.acceptance.test.ts`: "¿Cómo voy a UDENAR?" desde (1.2143, -77.2787) | Origen = parada más cercana "Parque Nariño" (40 m); plan FX1 sentido "Centro → Udenar" desde la BD; `isEstimate: true`; la IA no recibe las coordenadas |
| `npm test` (backend) | **120 pruebas OK** (transbordos, caminata, estimaciones, horario por zona horaria, nearby, demo) |
| `npm run data:validate` (datos reales) y `-- --data-dir ./data/sample` | Ambos válidos, 0 errores |
| Contratos `ok_gps` y `ok_transfer` | Generados por el backend y parseados por Android |
| `./gradlew clean assembleDebug testDebugUnitTest lintDebug` | BUILD SUCCESSFUL; **35 pruebas OK**; lint "No issues found" en los 3 módulos |

## Fase 4 — Interfaz del reloj (2026-10-22)

| Verificación | Resultado |
|---|---|
| Búsqueda de colores literales (`Color(0x…)`), `N.dp`/`N.sp` y cadenas literales en `ui/components`, `ui/screens`, `ui/WearApp.kt` | 0 coincidencias (solo quedan los `label` internos de animación y la clave del RemoteInput, que no son texto visible) |
| Archivos de UI | Ninguno pasa de 300 líneas (el mayor, `WearApp.kt`, tiene 254) |
| Contraste WCAG (`ThemeTest`) | Todos los textos ≥ 4,5:1 en tema oscuro y claro |
| `./gradlew clean assembleDebug testDebugUnitTest lintDebug` | BUILD SUCCESSFUL; **52 pruebas OK** (ViewModel 14, UI 15, tema 2, shared 21); lint sin hallazgos |
| Previews | Compilan: 3 formas de reloj (redondo pequeño y grande, cuadrado) en oscuro/español, y variantes claro/inglés |

## Fase 5 — App móvil y sincronización (2026-10-22)

| Verificación | Resultado |
|---|---|
| Contratos `contracts/transit/*.json` (backend) parseados por `RemoteTransitRepositoryTest` | 4 pruebas OK |
| Sincronización (`SyncTest`, `DataStorePreferencesRepositoryTest`) | Formato ida y vuelta, versiones, campos desconocidos, publicación en cada cambio local, sin republicar lo recibido, "gana el más reciente" |
| App móvil (`MobileViewModelsTest`, `MobileUiTest`) | 12 pruebas OK: filtro sin tildes, favoritos primero, espera entre teclas, reintentos, navegación lista→detalle→volver, búsqueda de paradas, historial sincronizado, ajustes es/en |
| `./gradlew clean assembleDebug testDebugUnitTest lintDebug` | BUILD SUCCESSFUL; **75 pruebas Android OK**; lint sin hallazgos en los 3 módulos |
| `npm test` (backend) | 127 pruebas OK |
| **No verificable aquí** | Envío real por la Data Layer entre dispositivos emparejados y el mapa con una clave real (ver PENDIENTES) |

## Fase 6 — Cierre (2026-10-22)

| Verificación | Resultado |
|---|---|
| Modelos de Gemini en https://ai.google.dev/gemini-api/docs/models | `gemini-3.8-flash` y `gemini-3.5-flash-lite` listados como estables |
| `npm run lint` · `format:check` · `typecheck` · `build` | OK |
| `npm test` (backend) | 14 archivos, **130 pruebas OK** (+ `loadSeedConfig`, contratos `stops_nearby*`) |
| `npm run data:validate` y `-- --data-dir ./data/sample` | Ambos válidos, sin `.env` |
| `node dist/db/seed.js` dos veces sobre la misma BD | Mismo resultado (idempotente) |
| `docker build` + `docker run` | `/health` → `{"status":"ok"}`; `/api/routes` sin clave → 401, con clave → ruta E1; `/api/stops/nearby` → 0 paradas, `stopsWithoutCoordinates: 86` |
| `./gradlew testDebugUnitTest` | **80 pruebas Android OK** (wear 35, mobile 12, shared 33) |
| `./gradlew clean assembleDebug lintDebug` | BUILD SUCCESSFUL; lint "No issues found" en los 3 módulos |
| Búsqueda `AIza…`/`generativelanguage` dentro de `wear-debug.apk` y `mobile-debug.apk` | 0 |
| Auditoría "nada quemado" | ver `docs/AUDITORIA.md` |
| **No verificable aquí** | Envío real por la Data Layer entre dispositivos emparejados, mapa con clave real, GPS en un reloj físico |

## Fase 7 — APK del reloj (2026-10-24)

| Comando | Resultado |
|---|---|
| `./gradlew :wear:assembleDebug :wear:testDebugUnitTest` | OK (sin errores); `wear-debug.apk` apunta a `busalert.debug.backendUrl` (verificado en el dex) |
| `BUSALERT_RELEASE_BACKEND_URL=https://verificacion.invalid/ ./gradlew :wear:assembleRelease` | OK; `apksigner verify` → `CN=BusAlert`, SHA-256 `367a1ce8…`. El APK de prueba se borró |
| Búsqueda `AIza…` dentro de los APK | 0 (la clave de Gemini solo está en el `.env` del backend) |

## Fase 8 — Rutas de la guía y frase única (2026-10-24)

| Comando | Resultado |
|---|---|
| `python scripts/import_guide.py data` + `npm run data:validate` | 23 rutas, 45 sentidos, 307 paradas, 899 paradas en recorridos: datos válidos |
| `npm run lint` / `typecheck` / `format:check` / `build` | OK |
| `npx vitest run` | 136 pruebas OK (planificador: homónimas, preferir directas) |
| `./gradlew :wear:testDebugUnitTest :wear:lintDebug :wear:assembleRelease` | BUILD SUCCESSFUL; 45 pruebas OK (7 del separador de frases, 3 del ViewModel); lint sin hallazgos |
| `POST /api/voice` con datos reales (servidor local) | Fátima → Universidad Cooperativa: C5 directa; Parque Bolívar → Udenar: E1 directa; Obonuco → Terminal: C1 directa; Catambuco → Unicentro: C6 + E6 con transbordo en San Andresito; respuestas redactadas por la IA (`source: ai`) |
