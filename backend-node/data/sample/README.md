# Datos de DEMOSTRACIÓN (no reales)

Esta carpeta existe para probar la app de punta a punta (búsqueda por GPS, transbordos y estimaciones) mientras no haya datos oficiales con coordenadas.

- **Nada de esto es información real del SETP.** Las rutas `DEMO-1`, `DEMO-2` y sus paradas (todas con prefijo "Demo") son inventadas.
- Las coordenadas son **aproximadas** y solo sirven para ubicar puntos dentro de Pasto.
- La fuente `demo_sample` hace que la API marque las respuestas con `isDemo: true` / `isDemoData: true`, y que el reloj muestre el aviso "Datos de demostración".

Para cargarla (se puede combinar con los datos reales):

```bash
npm run seed:sample
```
