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
