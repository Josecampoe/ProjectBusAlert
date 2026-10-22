# Diagnóstico inicial (2026-10-22)

Estado de `main` en el commit `a292d44`, revisado en modo solo lectura antes de empezar las mejoras.

## Entorno

| Herramienta | Resultado |
|---|---|
| Node.js | v24.19.0, npm 11.17.0 |
| JDK | En el `PATH` no hay ninguno. Android Studio trae JBR 25, que **Gradle 8.4 no soporta** (el build falla con el mensaje `25.0.3`). Funciona con JBR 17 (`~/.jdks/jbr-17.0.14`). |
| Android SDK | `platforms/android-34`, `build-tools/34.0.0` |
| `npm install` | **Falla**: `better-sqlite3@13.0.3` no tiene binario precompilado para Node 24 en Windows y faltan las herramientas de compilación de Visual Studio (node-gyp responde `find VS`). |
| `./gradlew assembleDebug` | OK con JDK 17 (las 2 APKs compilan). |
| `./gradlew testDebugUnitTest` | **No compila**: falta la dependencia de JUnit (`Unresolved reference: junit`), y `ModelsTest` usa un constructor de `Route` que no existe. |

## Problemas confirmados (evidencia)

| # | Problema | Evidencia | ¿Coincide? |
|---|---|---|---|
| 1 | El reloj llama directo a Gemini con la key en el cliente | `shared/.../api/gemini/GeminiModels.kt:10` (`API_KEY`), `shared/.../di/SharedDependencies.kt:28` (usa `GeminiDirectAssistantRepositoryImpl`) | Sí. La key es el marcador `YOUR_GEMINI_API_KEY_HERE`. En el historial de git **no hay ninguna key real** (se buscó el patrón `AIza…` en `git log -p --all`: 0 resultados) y `.env` nunca se subió. |
| 1b | `RemoteAIAssistantRepositoryImpl` no se usa y no hay forma de alternar real/mock | `SharedDependencies.kt` no tiene ningún flag | Sí. El commit `c18791d` dice "alternancia dinámica", pero el código no la implementa. |
| 2 | Sin GPS | `wear/src/main/AndroidManifest.xml` no declara permisos de ubicación; la ubicación se dicta o se escribe (`MainWearViewModel.kt:93`, con un valor por defecto `"Pasto, Nariño"` quemado) | Sí |
| 3 | Datos inventados y duplicados | El prompt tiene rutas a mano en `backend-node/src/services/aiService.ts:156` y la copia en `shared/.../GeminiDirectAssistantRepositoryImpl.kt:24`; los fallbacks tienen tiempos fijos (`aiService.ts:357`); la BD solo tiene la E1 (`data/stops/stops_e1.json`: 86 paradas, **0 con coordenadas**) | Sí. Las rutas E2, E4, C16, P1 y T1 solo existen en el prompt y no tienen fuente verificable. |
| 4 | Tests de relleno | `wear/src/test/.../MainWearViewModelTest.kt:9` (`assertTrue(true)`); `backend-node/src/controllers/transit/routesController.test.ts` compara una constante consigo misma; Jest no está en `package.json`; se usan coordenadas de Bucaramanga (`ModelsTest.kt:14-15`, "Estacion UIS", 7.14/-73.12) | Sí |
| 5 | Tooling | `README.md:80` dice `npm run seed`, pero el script no existe; `.eslintrc.json` está sin ESLint instalado; hay dos SDKs (`@google/genai` y `@google/generative-ai`); CORS abierto (`src/index.ts:22`); sin límite de body, sin auth y sin rate limit | Sí |
| 5b | Nombres de modelo | `aiService.ts:22` (`gemini-3.8-flash`, **existe y es estable** según https://ai.google.dev/gemini-api/docs/models consultado el 2026-10-22); `GeminiModels.kt:12` (`gemini-3-flash-preview` es **preview**); `gemini-flash-latest` es un alias que cambia con cada versión | Parcial: el principal es válido, pero hay que dejarlo configurable y elegir un respaldo estable. |
| 6 | App móvil vacía | `mobile/.../MainActivity.kt:12` (`Text("BusAlert Mobile")`) | Sí |
| 7 | Prompt injection | `aiService.ts:258` interpola `voiceText` directo en el prompt, sin delimitar | Sí |
| 8 | Nombre de carpeta `BusAlertfrotend` | Raíz del repo | Sí |

## Otros hallazgos

- `android:usesCleartextTraffic="true"` en los dos manifiestos, también en release (`wear/src/main/AndroidManifest.xml:17`, `mobile/src/main/AndroidManifest.xml:5`).
- El ViewModel detecta errores buscando palabras dentro del título (`MainWearViewModel.kt:106`): es frágil y depende del idioma.
- `memoryCache` (`src/services/cacheService.ts:27`) se exporta pero no se usa en ningún lado.
- `stops_e1.json` tiene dos paradas con el mismo nombre ("Udenar Torobajo", códigos 1275 y 1305), lo que hace ambigua la búsqueda por nombre.
- Los textos de UI y los mensajes de error están en Kotlin (`theme/Strings.kt`, `VoiceRecognitionManager.kt`) y no en `strings.xml`.
