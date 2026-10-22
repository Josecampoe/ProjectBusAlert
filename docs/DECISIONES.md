# Registro de decisiones

Decisiones tomadas durante las mejoras sin consultar al dueño del proyecto. Formato: decisión · alternativas · motivo.

## D-001 · Usar `node:sqlite` en lugar de `better-sqlite3`
- **Alternativas:** instalar las herramientas de compilación de Visual Studio; fijar otra versión de Node; usar `sql.js` (WASM).
- **Motivo:** `better-sqlite3@13` no tiene binario para Node 24 en Windows y su instalación falla. Node ≥ 22.13 trae SQLite integrado y síncrono con una API casi igual (`prepare/all/get/run/exec`). Así desaparece la dependencia nativa y cualquier clon nuevo funciona sin compilar nada. Requisito nuevo: `engines.node >= 22.13`.

## D-002 · JDK 17 para Gradle; sin actualizar AGP/Gradle
- **Alternativas:** migrar a Gradle 9.1+ y AGP 9 para soportar el JBR 25 de Android Studio.
- **Motivo:** el proyecto compila con AGP 8.3 + Gradle 8.4 + Kotlin 1.9.22 usando JDK 17. Una migración mayor de la toolchain se sale del alcance y pone en riesgo todo lo demás. Se documenta en el README que se necesita JDK 17 (con `JAVA_HOME` o `org.gradle.java.home`) y la CI usa `setup-java@17`.

## D-003 · Modelos de Gemini
- **Principal:** `gemini-3.8-flash` (estable). **Respaldo:** `gemini-3.5-flash-lite` (estable). Ambos se configuran con `GEMINI_MODEL_PRIMARY` / `GEMINI_MODEL_FALLBACK`.
- **Alternativas:** alias `gemini-flash-latest` (cambia solo con cada versión y rompe la reproducibilidad); `gemini-3-flash-preview` (es preview).
- **Motivo:** verificado en https://ai.google.dev/gemini-api/docs/models el 2026-10-22.

## D-004 · Un solo SDK: `@google/genai`
- **Motivo:** es el SDK vigente y soportado por Google. `@google/generative-ai` es el SDK anterior y en el código no se usaba.

## D-005 · Autenticación por clave de aplicación (`X-API-Key`)
- **Alternativas:** OAuth/JWT por usuario; firma HMAC por petición.
- **Motivo:** la app no tiene cuentas de usuario. La clave de aplicación permite revocar y rotar el acceso y aplicar límites de tasa. **No es un secreto fuerte**: si se compila dentro del APK puede extraerse. Por eso nunca se escribe en el código: se inyecta en el build desde `local.properties` o una variable de entorno, y el servidor acepta varias claves (`API_KEYS`) para poder rotarlas. La autenticación por usuario queda como trabajo futuro (ver PENDIENTES).

## D-006 · Respuestas estructuradas; los textos los pone el cliente
- **Motivo:** el backend devuelve datos (ruta, paradas, estimación, códigos de error) y el cliente los formatea con `strings.xml`. Solo el `spokenText` generado por la IA llega como texto libre. Así no hay textos de UI en el servidor y la traducción vive en un solo lugar.

## D-007 · Primero el renombrado de la carpeta
- **Motivo:** el paso 8 de la Fase 1 (`BusAlertfrotend` → `BusAlertFrontend`) se hace al principio para no tocar dos veces las rutas de CI, README y scripts.

## D-008 · Vitest en lugar de Jest
- **Alternativas:** Jest + ts-jest.
- **Motivo:** Vitest carga `node:sqlite` y TypeScript sin configuración extra (se probó antes de elegir), es más rápido y su API (`describe/it/expect`) es la misma que usaban los tests anteriores. Se combina con `supertest` para las pruebas HTTP.

## D-009 · `tsx` en lugar de `ts-node-dev`
- **Motivo:** `ts-node-dev` está sin mantenimiento. `tsx` ejecuta TypeScript en Node 24 sin compilar y trae modo `watch`. En producción se usa `tsc` + `node dist/`.

## D-010 · Resolución de lugares determinista; la IA solo redacta
- **Alternativas:** que la IA interprete el texto libre y decida la ruta.
- **Motivo:** la regla es no inventar datos. El backend resuelve origen y destino contra la BD (nombre, alias, palabras clave, sin tildes) y el planificador calcula las alternativas. La IA solo elige **un índice** entre esas alternativas y redacta el texto; si responde algo fuera de esquema o un índice inexistente, se descarta y se usa el planificador. Así una IA que "obedece" un intento de inyección no puede introducir una ruta inventada.

## D-011 · Contratos JSON compartidos (`contracts/`)
- **Motivo:** las pruebas del backend guardan respuestas reales en `contracts/voice/*.json` y las pruebas de Android parsean esos mismos archivos. Un cambio de formato en cualquiera de los dos lados rompe una prueba. Se regeneran con `UPDATE_CONTRACTS=1 npm test -- contracts`.

## D-012 · DTOs anulables + mapeo validado en Android
- **Motivo:** Gson ignora la nulidad de Kotlin. Los DTOs son anulables y `DtoMappers.kt` valida al convertir al dominio. Si la respuesta no cumple el contrato, el resultado es `ErrorCode.SERVER` y la app no se cae.

## D-013 · Retrofit 2.11 + OkHttp 4.12
- **Motivo:** Retrofit 2.9 arrastraba OkHttp 3. Con OkHttp 4 se puede usar `mockwebserver` 4.12 en las pruebas y el interceptor de la cabecera `X-API-Key`.

## D-014 · Demo solo en debug y con datos marcados
- **Motivo:** `busalert.useMock=true` activa `DemoTripAssistantRepository` **solo en debug** (en release el flag queda en `false` a la fuerza). La demo usa la ruta ficticia "DEMO" y marca todo con `isDemoData`. Se eliminaron los mocks anteriores, que tenían tiempos y rutas inventados.

## D-015 · Configuración Android por `local.properties` / variables de entorno
- **Motivo:** el `build.gradle.kts` raíz lee `busalert.*` (o `BUSALERT_*`) y los módulos los pasan a `BuildConfig`. El build release se detiene si `busalert.release.backendUrl` no es HTTPS. Debug permite HTTP (`network_security_config` de debug); release solo HTTPS.

## D-016 · Nueva ruta por defecto de la BD (`data/db/busalert.sqlite`)
- **Motivo:** el archivo anterior `data/busalert.db` tiene el esquema viejo, sin restricciones `UNIQUE`, y no se puede migrar con seguridad. No se borró: queda en disco (está ignorado por git) y la BD nueva se crea en otra ruta.
