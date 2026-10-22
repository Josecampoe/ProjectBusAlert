# Pendientes que dependen del dueño del proyecto

Cada punto dice qué falta, en qué formato y dónde va.

## Seguridad

- **Rotar la key de Gemini: no hace falta.** En el historial de git no hay ninguna key real (se revisó `git log -p --all` con el patrón `AIza…`, 0 resultados). Si alguna vez pusiste una key real en `GeminiConfig.API_KEY` o en un APK que compartiste, **rótala** en https://aistudio.google.com/apikey.

## Configuración local (tu máquina)

- **`backend-node/.env`**: tu archivo actual solo tiene `PORT` y `GEMINI_API_KEY`. Agrega como mínimo estas variables (ver `.env.example`):
  ```
  API_KEYS=<clave aleatoria de 16+ caracteres>
  DB_PATH=./data/db/busalert.sqlite
  DATA_DIR=./data
  ```
  y luego ejecuta `npm run seed`.
- **`BusAlertFrontend/local.properties`**: agrega la misma clave para que el reloj pueda autenticarse:
  ```
  busalert.apiKey=<la misma clave de API_KEYS>
  # opcional, por defecto http://10.0.2.2:3000/ (emulador)
  busalert.debug.backendUrl=http://<IP-de-tu-PC>:3000/
  # obligatorio solo para builds release
  busalert.release.backendUrl=https://<tu-dominio>/
  ```
- **Archivo viejo `backend-node/data/busalert.db`** (+ `-shm`/`-wal`): tiene el esquema anterior y ya no se usa. Puedes borrarlo cuando quieras; no lo borré.

## Seguridad (decisiones de negocio)

- **Autenticación por usuario**: hoy la API usa una clave de aplicación (`X-API-Key`), que no es un secreto fuerte porque va dentro del APK (ver D-005). Si BusAlert va a tener cuentas o un uso público masivo, hay que decidir el método (por ejemplo Firebase Auth / OAuth) y dónde se gestionan los usuarios.

## Datos de transporte (lo más importante)

Todo va en `backend-node/data/`, siguiendo `backend-node/data/templates/route.template.json`. Se valida con `npm run data:validate` y se carga con `npm run seed`.

1. **Coordenadas de las 86 paradas de la E1** (hoy todas en `null`). Sin ellas no funcionan para datos reales la búsqueda por GPS, los transbordos a pie ni la estimación por distancia.
   - Archivo: `data/stops/stops_e1.json`, campos `latitude` y `longitude` en grados decimales (ej. `1.2141`, `-77.2786`).
   - Fuente sugerida: la app Ruti/Avante o un recorrido con GPS.
2. **Rutas sin datos verificables en el repo**: las rutas que el prompt viejo mencionaba (E2, E4, C16, P1, T1) **no se cargaron** porque no hay fuente verificable (estaban escritas a mano en el prompt de la IA).
   - Para cada una: un bloque en `routes/routes.json`, sus sentidos en `routes/route_directions.json`, el orden de paradas en `routes/route_stops.json` y las paradas nuevas en `stops/stops_<codigo>.json`.
   - Incluye también las demás rutas del SETP que quieras cubrir.
3. **Horarios y frecuencias de la E1** (`data/schedules/schedules.json`): hoy `start_time`, `end_time` y `frequency_minutes` están en `null`. Con la frecuencia, las estimaciones suman el tiempo de espera; con el horario, se avisa si la consulta está fuera de servicio.
4. **Color oficial de cada ruta** (opcional): campo `color` (`#RRGGBB`) en `routes/routes.json`. El reloj y la app móvil lo usan para identificar la ruta.
5. **Calendario de festivos** (decisión): hoy los festivos se tratan como día hábil. Si se necesita, hay que definir la fuente del calendario de festivos de Colombia.
6. **Tiempo real** (decisión): si Avante/Ruti ofrece una API de buses en tiempo real, se conecta con una nueva implementación de `ArrivalTimeProvider` (`backend-node/src/services/arrivalTime.ts`). Hace falta acceso o credenciales del operador.
