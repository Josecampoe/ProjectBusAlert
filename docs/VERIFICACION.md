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
