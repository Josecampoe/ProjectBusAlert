# BusAlert - Smart Transit Alert & Navigation System

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Wear OS](https://img.shields.io/badge/Wear%20OS-Compose-brightgreen.svg)](https://developer.android.com/wear)
[![Node.js](https://img.shields.io/badge/Node.js-Express%20%7C%20TypeScript-green.svg)](https://nodejs.org)
[![SQLite](https://img.shields.io/badge/SQLite-Spatial-lightgrey.svg)](https://sqlite.org)
[![Google Gemini](https://img.shields.io/badge/Google%20Gemini-Direct%20API-orange.svg)](https://ai.google.dev)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

BusAlert es una solución integral de asistencia al viajero y alertas inteligentes de transporte público diseñada especialmente para relojes inteligentes con **Wear OS** y dispositivos móviles Android, sincronizada con un backend de alto rendimiento en **Node.js/TypeScript** y potenciada por **Google Gemini AI**.

---

## 🌟 Características Principales

### ⌚ Wear OS App (`BusAlertFrontend/wear`)
- **Diseño Ergonómico Circular**: Interfaz Jetpack Compose Wear OS adaptada con curvaturas y ergonomía táctil para pantallas redondas de 390px, 450px y 454px.
- **Asistente por Voz In-App**: Reconocimiento de voz continuo con `SpeechRecognizer`, transcripción en vivo en tiempo real, ondas de audio animadas y botones circulares de cancelación rápida (`✕`) y reintento (`🔄`).
- **Teclado Táctil Integrado**: Teclado numérico/alfanumérico circular con soporte adaptativo para **Modo Claro** y **Modo Oscuro**, garantizando contraste óptimo y alternancia instantánea entre voz y teclado.
- **Sintetizador de Voz (TTS)**: Respuestas leídas en voz alta para accesibilidad mientras el usuario camina o viaja en autobús.
- **Tarjetas de Tránsito Inteligentes**: Indicación de ruta recomendada, parada de transbordo, tiempo estimado y alerta visual de llegada.

### 🌐 Backend Node.js / TypeScript (`backend-node`)
- **API REST de Transporte**: Endpoints de consulta de rutas, paradas, direcciones y frecuencias calculadas.
- **Búsqueda Geoespacial**: Algoritmo Haversine para identificación de paradas cercanas por radio en metros.
- **Integración con Google Gemini**: Asistente conversacional con inyección de contexto geográfico y de transporte en tiempo real.
- **Base de Datos SQLite**: Modelado relacional optimizado con índices compuestos en latitud/longitud y secuencias de parada.

### 🔄 Módulo Compartido (`BusAlertFrontend/shared`)
- Arquitectura limpia con separación de capas (`domain`, `data`, `di`).
- Conexión directa con Google Gemini REST API o mediante backend Node.js.
- Conmutador dinámico entre modo de producción y modo simulado (`MockTransitRepositoryImpl`, `MockAIAssistantRepositoryImpl`).

---

## 📂 Estructura del Monorepo

```
c:/ProyectoBusAlert/
├── README.md                      # Documentación global del sistema
├── LICENSE                        # Licencia de código abierto MIT
├── .gitignore                     # Reglas de exclusión de compilación y secretos
├── backend-node/                  # Backend Node.js / TypeScript
│   ├── package.json               # Dependencias y scripts
│   ├── tsconfig.json              # Configuración del compilador TypeScript
│   ├── .env.example               # Plantilla de variables de entorno
│   ├── data/                      # Datasets JSON de rutas, paradas y horarios
│   └── src/
│       ├── index.ts               # Servidor Express y middleware
│       ├── controllers/           # Controladores de rutas, paradas y voz
│       ├── db/                    # Conexión, esquemas y migración seed.ts
│       ├── services/              # Servicio de Google Gemini AI y caché
│       └── utils/                 # Cálculo haversine y geofencing
└── BusAlertFrontend/               # Proyecto Android multi-módulo
    ├── settings.gradle.kts        # Definición de módulos compartidos
    ├── build.gradle.kts           # Configuración raíz de Gradle
    ├── setup.ps1                  # Automatización de emuladores Wear OS
    ├── shared/                    # Módulo Kotlin multi-plataforma
    │   └── src/main/java/...      # Modelos de dominio y clientes Gemini
    ├── wear/                      # Aplicación Wear OS Jetpack Compose
    │   └── src/main/java/...      # Pantallas circulares, voz y tema visual
    └── mobile/                    # Aplicación Android Mobile complementaria
```

---

## 🚀 Puesta en Marcha

### 1. Requisitos Previos
- **Node.js**: v18.0.0 o superior y npm.
- **Android Studio**: Ladybug / Meerkat o superior con SDK Android 34 (Wear OS 4 / 5).
- **Gemini API Key**: Obtenida en [Google AI Studio](https://aistudio.google.com).

### 2. Backend Node.js
```bash
cd backend-node
npm install
cp .env.example .env
# Configura tu GEMINI_API_KEY en .env
npm run seed     # Poblar base de datos SQLite
npm run dev      # Iniciar servidor en http://localhost:3000
```

### 3. Frontend Android Wear OS
```bash
cd BusAlertFrontend
# Compilar e instalar en emulador o reloj conectado
./gradlew :wear:installDebug
```

---

## 👨‍💻 Autor
Desarrollado por **Jose Campoe** y el equipo de BusAlert.
