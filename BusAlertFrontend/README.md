# BusAlert — Wear OS

Asistente de transporte público por voz para smartwatch, diseñado para la ciudad de **Pasto, Nariño, Colombia**.

El usuario habla al reloj ("¿Cómo llego a la Universidad Cooperativa desde Torres de Fátima?") y el reloj responde con la ruta de bus más cercana, la parada y el tiempo estimado.

## Arquitectura

Proyecto Android multi-módulo:

```
BusAlertFrontend/
├── shared/     # Modelos de dominio, repositorios, API Retrofit
├── wear/       # App principal para Wear OS (Jetpack Compose)
└── mobile/     # Companion app para teléfono Android
```

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Wear OS Material)
- **MVVM** con `StateFlow` y `viewModelScope`
- **Retrofit 2** + **Gson** — comunicación con el backend
- **Android RecognizerIntent** — reconocimiento de voz nativo
- **Android TextToSpeech** — respuesta hablada en español
- **Gradle 8.4** + **AGP 8.3**

## Flujo de la app

```
[HomeScreen] → toca micrófono
     ↓
[ListeningScreen] → Google STT escucha
     ↓
[ProcessingScreen] → Retrofit POST al backend
     ↓
[ResultScreen] → muestra ruta + TTS habla la respuesta
```

Si el emulador no tiene STT disponible, la app cae en **TypingScreen** donde el usuario escribe la consulta — misma respuesta de IA.

## Paleta de colores

| Color | Hex | Uso |
|-------|-----|-----|
| Vinotinto | `#800020` | Color primario, botones, títulos |
| VinotintoDark | `#4A0012` | Fondo oscuro de pantallas activas |
| VinotintoLight | `#B0003A` | Animaciones, indicadores |
| GradientEnd | `#F5C8D0` | Fondo degradado suave |

## Requisitos

- Android Studio Hedgehog o superior
- Emulador Wear OS API 30+ (API 33 recomendado para micrófono)
- Conexión a Internet (Wi-Fi en el reloj o emulador)

## Integración con IA (Gemini Directo)

La aplicación ya no depende de un servidor local. Se conecta directamente a la API de Google Gemini en la nube:

- Configuración y API Key: `shared/src/main/java/com/busalert/shared/data/api/gemini/GeminiConfig.kt`
- Repositorio directo: `shared/src/main/java/com/busalert/shared/data/GeminiDirectAssistantRepositoryImpl.kt`
- Fallback local: Si el reloj no tiene conexión, cuenta con respuestas precargadas para las principales rutas de Pasto (UCC, Udenar, Terminal, Centro, etc.).
