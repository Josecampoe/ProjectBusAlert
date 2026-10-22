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
