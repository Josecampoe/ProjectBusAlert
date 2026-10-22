# BusAlert

Asistente de transporte público para **San Juan de Pasto (Colombia)**, pensado para relojes **Wear OS**. El usuario dice o escribe dónde está y a dónde va, y el reloj responde qué bus del SETP tomar, dónde subir y dónde bajar, en pantalla y en voz alta.

[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-blue.svg)](.github/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Componentes

| Carpeta | Qué es |
|---|---|
| [`backend-node/`](backend-node/README.md) | API en Node.js + TypeScript + SQLite. Fuente única de rutas y paradas, planificador e integración con Gemini. |
| [`BusAlertFrontend/`](BusAlertFrontend/README.md) | Proyecto Android: app del reloj (`wear`), app del teléfono (`mobile`) y módulo compartido (`shared`). |
| `contracts/` | Respuestas JSON reales del backend, usadas por las pruebas de los dos lados. |
| `docs/` | Diagnóstico, decisiones (`DECISIONES.md`) y pendientes que dependen del dueño (`PENDIENTES.md`). |

## Inicio rápido

```bash
# 1. Backend (Node.js 22.13+)
cd backend-node
npm ci
cp .env.example .env        # define API_KEYS (y GEMINI_API_KEY si quieres IA)
npm run seed
npm run dev                 # http://localhost:3000

# 2. Reloj (JDK 17)
cd ../BusAlertFrontend
# en local.properties: busalert.apiKey=<misma clave de API_KEYS>
./gradlew :wear:installDebug
```

## Calidad

La CI (`.github/workflows/ci.yml`) ejecuta en cada push:

- **Backend:** `npm run lint`, `format:check`, `typecheck`, `test`, `build` e importación de los datos reales.
- **Android:** `assembleDebug`, `testDebugUnitTest` y `lintDebug`.

## Licencia

MIT. Ver [LICENSE](LICENSE).
