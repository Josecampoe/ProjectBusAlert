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

## D-017 · ESLint 10 (flat config, `strictTypeChecked`) + Prettier
- **Motivo:** el `.eslintrc.json` anterior no tenía ESLint instalado. Ahora `npm run lint` usa typescript-eslint con reglas estrictas basadas en tipos, y Prettier respeta el `.prettierrc` que ya existía (2 espacios, ancho 100).

## D-018 · Eliminado `BusAlertFrontend/setup.ps1`
- **Motivo:** era un script de andamiaje inicial que **sobrescribía** `build.gradle.kts`, los manifiestos y los `MainActivity.kt` con esqueletos vacíos. Ejecutarlo hoy destruiría el proyecto. Queda en el historial de git.

## D-019 · Pruebas de UI con Robolectric (JVM)
- **Alternativas:** pruebas instrumentadas en emulador.
- **Motivo:** corren en la CI sin emulador y en segundos. Las instrumentadas se pueden sumar después si hace falta probar hardware (micrófono, GPS).

## D-020 · Android Lint como compuerta (`warningsAsErrors`)
- **Motivo:** se corrigieron todos los avisos (ícono, backup, `taskAffinity`, plurales, división de idiomas en el bundle). Solo se desactiva `GradleDependency`, porque las versiones nuevas de AndroidX exigen la actualización de toolchain descartada en D-002.

## D-021 · `.gitattributes` con `eol=lf`
- **Motivo:** el repositorio mezclaba CRLF y LF y `gradlew` no tenía permiso de ejecución, así que fallaría en la CI de Linux. Se normalizó y se marcó `gradlew` como ejecutable.

## D-022 · Datos de demostración separados y marcados (`data/sample/`)
- **Motivo:** las paradas reales no tienen coordenadas, así que la búsqueda por GPS no se podía probar con datos reales. Se creó un set ficticio (rutas `DEMO-*`, paradas "Demo …", coordenadas aproximadas) con la fuente `demo_sample`. La API lo marca con `isDemo`/`isDemoData` y el reloj muestra "Datos de demostración". Se carga aparte con `npm run seed:sample`.

## D-023 · Estimación de tiempos determinista detrás de `ArrivalTimeProvider`
- **Alternativas:** dejar que la IA "adivine" (era lo anterior); no dar tiempos.
- **Motivo:** viaje = distancia (Haversine entre paradas) / `ETA_AVERAGE_BUS_SPEED_KMH`, o paradas × `PLANNER_MINUTES_PER_STOP` si faltan coordenadas. Espera = frecuencia / 2 si la ruta tiene frecuencia para el tipo de día actual (zona horaria `TRANSIT_TIMEZONE`). Todo va con `isEstimate: true`. Para conectar tiempo real basta otra implementación de la interfaz.
- **Limitación:** no se distinguen festivos (los domingos usan `DF`). Ver PENDIENTES.

## D-024 · Un transbordo como máximo, también a pie
- **Motivo:** cubre la mayoría de viajes urbanos sin disparar el costo de búsqueda. El transbordo puede ser en la misma parada o caminando hasta `PLANNER_TRANSFER_WALK_RADIUS_M` (requiere coordenadas). Las alternativas se ordenan por minutos estimados + `PLANNER_TRANSFER_PENALTY_MIN` por transbordo.

## D-025 · Origen por GPS con prioridad y privacidad
- **Motivo:** si llegan `latitude/longitude`, mandan sobre el texto. El origen pasa a ser las paradas dentro de `NEARBY_DEFAULT_RADIUS_M`. **Las coordenadas del usuario no se envían a la IA**: solo el nombre de la parada más cercana.

## D-026 · Flujo de permiso de ubicación en el reloj
- **Motivo:** al iniciar una consulta, si no hay permiso se muestra primero una explicación (cuando Android lo sugiere) y luego el diálogo del sistema. Se conceda o no, la consulta sigue: sin permiso o sin señal en `LocationDefaults.TIMEOUT_MS`, se pide el lugar por voz o teclado. Mientras busca el GPS, el usuario puede elegir "Decir dónde estoy".

## D-027 · Entrada de texto del sistema (RemoteInput) en lugar del teclado QWERTY propio
- **Alternativas:** mantener el teclado dibujado en Compose.
- **Motivo:** las teclas medían 15–20 dp, muy por debajo del mínimo táctil de 48 dp de Wear OS, no tenían soporte de TalkBack y solo servían en pantallas redondas de cierto tamaño. La entrada del sistema (`androidx.wear:wear-input`) ofrece teclado, escritura a mano y dictado, es accesible y funciona en cualquier forma de pantalla. La pantalla de escritura conserva el texto, las sugerencias y los cambios voz↔teclado.

## D-028 · Sugerencias desde el historial, no fijas
- **Motivo:** la lista anterior ("UCC", "Udenar", "Terminal"...) eran datos de transporte quemados en el código. Ahora las sugerencias son los lugares de las últimas consultas exitosas del usuario (DataStore).

## D-029 · Preferencias e historial en `shared` con DataStore 1.1.1
- **Motivo:** la app del reloj y la del teléfono comparten el mismo modelo (`UserPreferences`, `QueryHistoryEntry`), lo que prepara la sincronización de la Fase 5. Se usa la versión 1.1.1 porque la 1.0.0 falla en Windows al renombrar el archivo temporal (en Android no ocurre, pero rompía las pruebas locales).

## D-030 · Sistema de diseño por tokens
- **Motivo:** `Palette.kt` es el único archivo con colores literales. Los composables usan roles semánticos (`BusAlertTheme.colors`), espaciado, dimensiones, movimiento y efectos del tema, y la tipografía escalable de Wear Material. Una prueba verifica contraste WCAG AA (≥ 4,5) de todos los textos en ambos temas. El color de cada ruta viene de los datos (`routes.color`).

## D-031 · `ProvideAppLanguage` con `ContextWrapper`
- **Motivo:** la versión anterior reemplazaba el contexto por uno de configuración y perdía la referencia a la Activity, así que fallaban en ejecución los lanzadores de permisos y del teclado del sistema. Lo detectó una prueba de UI. Ahora se envuelve la Activity y solo se cambian los recursos.

## D-032 · Mismo `applicationId` (`com.busalert`) para el reloj y el teléfono
- **Motivo:** la Data Layer API solo comunica apps con el mismo nombre de paquete y la misma firma. El reloj pasó de `com.busalert.wear` a `com.busalert` (los `namespace` siguen siendo distintos). Si ya había una versión instalada en el reloj, hay que desinstalarla una vez.

## D-033 · Sincronización por estado completo con "gana el último cambio"
- **Alternativas:** mensajes por cada cambio; fusión campo a campo.
- **Motivo:** el estado es pequeño (preferencias, 20 consultas, 30 favoritos). Cada cambio local publica todo en un DataItem (`/busalert/sync`) con la hora del cambio, y el receptor lo aplica solo si es más reciente que lo suyo. Lo recibido no se vuelve a publicar, así no hay bucles. El formato tiene versión (`schema_version`) y es tolerante a campos desconocidos.

## D-034 · Paradas favoritas identificadas por nombre
- **Motivo:** el reloj las ofrece como sugerencias de texto y el backend resuelve por nombre o alias. Así los favoritos sobreviven a cambios de id interno al reimportar datos.

## D-035 · Clave de Google Maps por `manifestPlaceholders`
- **Motivo:** la clave nunca va al código ni a git: sale de `busalert.mapsApiKey` (o `BUSALERT_MAPS_API_KEY`). Sin clave la app muestra "Mapa no disponible" en vez de un mapa en blanco. El mapa solo dibuja paradas con coordenadas y avisa cuántas faltan.

## D-036 · `ProvideAppLanguage` duplicado en reloj y teléfono
- **Alternativas:** moverlo a `shared`.
- **Motivo:** `shared` no usa Compose; agregar el compilador de Compose solo para 30 líneas no compensa. Las dos copias siguen el mismo diseño (D-031).

## D-037 · El reloj consulta `/api/stops/nearby` antes de preguntar el destino
- **Alternativas:** mandar solo las coordenadas a `/api/voice` y dejar que el backend resuelva la parada (lo que se hacía).
- **Motivo:** con los datos reales actuales ninguna parada tiene coordenadas, así que el GPS siempre terminaba en "origen no encontrado" después de que el usuario dijera el destino. Ahora, si no hay paradas ubicables cerca, el reloj pide el lugar por voz o teclado en ese mismo momento; si las hay, muestra "Desde <parada>, ¿a dónde vas?". Si la consulta de cercanía falla por red, se sigue con las coordenadas (el backend vuelve a resolverlas). La demo de `shared` no tiene coordenadas y responde igual que el backend: sin paradas cercanas.

## D-038 · Configuración del seed validada en `config/`
- **Motivo:** el CLI de seed leía `process.env` directamente, contra la regla de un único módulo de configuración. `loadSeedConfig()` valida solo `DB_PATH` y `DATA_DIR` (el seed no necesita `API_KEYS` ni `PORT`). `npm run data:validate` apunta a `./data` para no depender de `.env`, y si `--data-dir` aparece varias veces gana el último.

## D-039 · Imagen Docker con seed al arrancar
- **Motivo:** el seed es idempotente y valida antes de escribir, así que ejecutarlo en cada arranque mantiene la BD del volumen alineada con los datos de la imagen sin un paso manual. La imagen corre como usuario `node` y tiene `HEALTHCHECK` sobre `/health`.

## D-040 · Firma de release del reloj desde `local.properties`
- **Motivo:** sin `signingConfig` el APK release sale sin firmar y no se puede instalar. El keystore y sus contraseñas se leen de `local.properties` o variables de entorno (`BUSALERT_RELEASE_*`), nunca del código; `validateReleaseConfig` detiene el build si falta algo. El keystore vive en `BusAlertFrontend/keystore/` (ignorado por git).

## D-041 · Despliegue de referencia en Render (`render.yaml`)
- **Alternativas:** Railway, Fly.io, un VPS con Caddy; túnel temporal (cloudflared) que cambia de URL en cada arranque.
- **Motivo:** el APK release exige HTTPS y el reloj necesita una URL fija. Render construye el `Dockerfile` existente, da HTTPS gratis y guarda las claves en su panel (`sync: false`). El disco efímero del plan gratis no afecta porque el seed corre al arrancar.

## D-042 · Prioridad: app del reloj
- **Motivo:** el dueño indicó que solo le importa la app del reloj. `mobile` se mantiene compilando y con pruebas, pero la documentación y los APK se centran en `:wear`, que es independiente (`standalone=true`).
