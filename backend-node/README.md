# BusAlert Backend

API REST que actúa como cerebro del sistema BusAlert. Procesa comandos de voz transcritos y retorna instrucciones de navegación en transporte público para la ciudad de **Pasto, Nariño, Colombia**.

## Stack

- **Node.js** + **TypeScript**
- **Express 4** — servidor HTTP
- **Google Gemini 2.0 Flash** — motor de inteligencia artificial
- **dotenv** — manejo de variables de entorno

## Rutas cubiertas

| Ruta | Descripción |
|------|-------------|
| C16  | Circular — Centro ↔ Sur (Av. Boyacá) |
| E1   | Expreso Norte-Sur — Villanueva ↔ Universidades ↔ Mijitayo |
| E2   | Expreso Oriente-Occidente — El Tejar ↔ Centro ↔ Jongovito |
| E4   | Expreso Sur-Norte — Chambú ↔ Terminal ↔ Lorenzo |
| P1   | Popular — Madrigal ↔ Centro |
| T1   | Transversal — Bolivariano ↔ Universidades |

## Instalación

```bash
npm install
cp .env.example .env
# Editar .env y agregar tu GEMINI_API_KEY
```

## Uso

```bash
# Desarrollo (hot-reload)
npm run dev

# Producción
npm run build
npm start
```

## Endpoints

### POST /api/voice

Procesa un comando de voz y retorna la instrucción de navegación.

**Body:**
```json
{
  "voiceText": "quiero ir a la Universidad Cooperativa",
  "currentLocationName": "Torres de Fátima"
}
```

**Response:**
```json
{
  "spokenText": "Toma la ruta E1 desde la Avenida Boyacá...",
  "displayTitle": "Ruta E1",
  "displaySubtitle": "Av. Boyacá Cll 22 • 8 min",
  "estimatedMinutes": 8,
  "recommendedRouteId": "E1"
}
```

### GET /health

Verifica que el servidor esté activo.

## Arquitectura

```
src/
├── index.ts                  # Punto de entrada Express
├── controllers/
│   └── voiceController.ts    # Validación y routing HTTP
└── services/
    └── aiService.ts          # Lógica de IA con Gemini + rutas de Pasto
```
