# BusAlert — Backend

API REST del asistente de transporte público BusAlert (SETP de San Juan de Pasto). Resuelve lugares y planifica viajes **solo con datos de su base de datos**, y si hay una clave de Gemini, usa la IA para redactar la respuesta.

## Requisitos

- Node.js **22.13 o superior** (se usa el SQLite integrado `node:sqlite`; no hace falta compilar nada).
- npm 10+.

## Puesta en marcha

```bash
npm ci
cp .env.example .env      # completa al menos API_KEYS (ver abajo)
npm run seed              # crea la BD e importa ./data
npm run dev               # http://localhost:3000
```

Para generar una clave de aplicación:

```bash
node -e "console.log(require('crypto').randomBytes(24).toString('base64url'))"
```

## Scripts

| Script                                    | Qué hace                                                                                                                                             |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `npm run dev`                             | Servidor con recarga automática (`tsx watch`).                                                                                                       |
| `npm run build`                           | Compila a `dist/` (sin pruebas).                                                                                                                     |
| `npm start`                               | Ejecuta `dist/index.js` (producción).                                                                                                                |
| `npm run seed`                            | Importa `DATA_DIR` en `DB_PATH`. Es idempotente y valida los datos; si hay errores no escribe nada. Con `-- --data-dir <ruta>` importa otra carpeta. |
| `npm run seed:prod`                       | Igual que `seed`, pero desde `dist/`.                                                                                                                |
| `npm test` / `npm run test:watch`         | Pruebas (Vitest + supertest).                                                                                                                        |
| `npm run coverage`                        | Pruebas con reporte de cobertura.                                                                                                                    |
| `npm run lint` / `npm run lint:fix`       | ESLint (reglas estrictas con tipos).                                                                                                                 |
| `npm run format` / `npm run format:check` | Prettier.                                                                                                                                            |
| `npm run typecheck`                       | Chequeo de tipos de TypeScript.                                                                                                                      |

## Configuración

Todas las variables están documentadas en [`.env.example`](.env.example) y se validan al arrancar en `src/config/env.ts`. Si falta una obligatoria, el servidor no arranca y dice cuál falta.

| Obligatorias |                                                                                                          |
| ------------ | -------------------------------------------------------------------------------------------------------- |
| `PORT`       | Puerto HTTP.                                                                                             |
| `API_KEYS`   | Claves de aplicación aceptadas en la cabecera `X-API-Key` (separadas por coma, 16+ caracteres cada una). |
| `DB_PATH`    | Archivo SQLite.                                                                                          |
| `DATA_DIR`   | Carpeta de datos que importa `npm run seed`.                                                             |

`GEMINI_API_KEY` es opcional: sin ella, `POST /api/voice` responde con el planificador (sin texto generado) y `POST /api/ai/transport-query` devuelve `503 AI_UNAVAILABLE`.

## API

Todas las rutas bajo `/api` exigen la cabecera `X-API-Key` y tienen límite de tasa. `GET /health` es pública.

| Método | Ruta                                                       | Descripción                                                                           |
| ------ | ---------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| GET    | `/health`                                                  | Estado del servicio y si la IA está activa.                                           |
| POST   | `/api/voice`                                               | Plan de viaje para el reloj. Cuerpo: `{ voiceText, currentLocationName, language? }`. |
| POST   | `/api/ai/transport-query`                                  | Pregunta libre respondida con datos de la BD. Cuerpo: `{ question, language? }`.      |
| GET    | `/api/routes` · `/api/routes/search?q=`                    | Rutas activas / búsqueda.                                                             |
| GET    | `/api/routes/:code`                                        | Detalle con sentidos y horarios.                                                      |
| GET    | `/api/routes/:code/directions` · `/api/routes/:code/stops` | Sentidos y paradas en orden.                                                          |
| GET    | `/api/stops` · `/api/stops/search?q=`                      | Paradas / búsqueda por nombre o alias (acepta frases libres).                         |
| GET    | `/api/stops/:id` · `/api/stops/:id/routes`                 | Parada por código Ruti o id interno, y rutas que pasan.                               |
| GET    | `/api/route-planner?origin=&destination=`                  | Alternativas entre dos lugares.                                                       |

### Respuesta de `/api/voice`

```jsonc
{
  "status": "ok",              // ok | same_place | no_route | origin_not_found | destination_not_found
  "source": "ai",              // ai | planner
  "message": { "spokenText": "...", "displayTitle": "...", "displaySubtitle": "..." }, // null si no hay IA
  "recommended": { "legs": [{ "routeCode": "E1", "board": {...}, "alight": {...}, "stopsCount": 5 }], ... },
  "alternatives": [],
  "originCandidates": [], "destinationCandidates": [],
  "isDemoData": false
}
```

Ejemplos reales generados por las pruebas: [`../contracts/voice/`](../contracts/voice/).

### Errores

Siempre con la forma `{ "error": { "code", "message", "details?" } }`. El cliente debe usar el **código** (estable) y no el mensaje.

| Código                                           | HTTP |
| ------------------------------------------------ | ---- |
| `VALIDATION_ERROR`, `INVALID_JSON`               | 400  |
| `UNAUTHORIZED`                                   | 401  |
| `NOT_FOUND`, `ROUTE_NOT_FOUND`, `STOP_NOT_FOUND` | 404  |
| `PAYLOAD_TOO_LARGE`                              | 413  |
| `RATE_LIMITED`                                   | 429  |
| `INTERNAL_ERROR`                                 | 500  |
| `AI_UNAVAILABLE`                                 | 503  |

## Arquitectura

```
src/
├── index.ts               # Arranque: valida config, arma dependencias, escucha
├── app.ts                 # Express: helmet, CORS, límite de body, rate limit, auth, rutas, errores
├── container.ts           # Construcción de dependencias reales
├── config/env.ts          # Única lectura de process.env (zod)
├── errors/AppError.ts     # Códigos de error estables
├── middleware/            # Autenticación, rate limit, validación, manejo de errores
├── routes/                # Endpoints y esquemas de entrada
├── db/                    # node:sqlite, migraciones versionadas, importador y CLI de seed
├── repositories/          # Acceso a datos y red de transporte en memoria (con caché TTL)
├── services/
│   ├── placeResolver.ts   # Texto libre → paradas (sin IA)
│   ├── planner.ts         # Alternativas de viaje
│   ├── assistantService.ts# Orquesta resolver + planner + IA
│   └── ai/                # Cliente Gemini, prompts y esquemas de salida
└── lib/, utils/           # Texto, logger, geodesia
```

### Seguridad frente a la IA

- Las instrucciones van en `systemInstruction`; el texto del usuario va saneado y serializado como JSON dentro de `<user_data>`.
- La IA solo recibe datos de la BD relacionados con la consulta y solo puede **elegir un índice** entre las alternativas del planificador.
- Toda salida se valida con un esquema. Si no cumple, se descarta y se responde con el planificador.

## Datos

- `data/`: datos reales verificados (hoy solo la ruta E1, sin coordenadas). Ver `docs/PENDIENTES.md` en la raíz.
- `src/test/fixtures/data/`: datos **ficticios** para pruebas (rutas `FX*`).
