# BusAlert - Sistema Inteligente de Transporte Público y Alertas para Wear OS

BusAlert es una plataforma integral de movilidad urbana y alertas de tránsito en tiempo real para dispositivos Wear OS y Android, respaldada por un backend en Node.js/TypeScript y la inteligencia artificial de Google Gemini.

## Arquitectura del Proyecto
El repositorio está estructurado como un monorepo modular:
- `backend-node/`: Servidor REST en Express, base de datos SQLite espacial y servicio de IA Gemini.
- `BusAlertfrotend/`: Aplicación Android multi-módulo (Wear OS, Mobile y arquitectura Compartida en Kotlin).
