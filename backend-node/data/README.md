# Datos de transporte

La base de datos se arma **solo** desde estos archivos con `npm run seed`. Ni el código ni el prompt de la IA tienen rutas o paradas escritas a mano.

| Carpeta / archivo | Contenido |
|---|---|
| `sources/*.json` | Fuentes de información (app Ruti, sitio de Avante, etc.). |
| `routes/routes.json` | Rutas. |
| `routes/route_directions.json` | Sentidos de cada ruta. |
| `routes/route_stops.json` | Orden de paradas por sentido. |
| `stops/*.json` | Paradas (con alias y, si se tienen, coordenadas). |
| `schedules/*.json` | Horarios y frecuencias por tipo de día. |
| `templates/route.template.json` | Plantilla comentada para agregar una ruta nueva. |
| `sample/` | Datos de **demostración** (no reales), marcados como demo. |

## Agregar o corregir datos

1. Copia los bloques de `templates/route.template.json` a los archivos indicados.
2. Valida sin escribir nada: `npm run data:validate`.
   El validador revisa el formato de cada campo (coordenadas en rango, horas `HH:MM`, colores `#RRGGBB`), las referencias entre archivos (fuente, ruta, sentido, parada), los duplicados y que cada parada de un recorrido exista.
3. Importa: `npm run seed`. Es idempotente: crea lo nuevo y actualiza lo existente; si hay errores no escribe nada.

## Estado actual

- Solo la ruta **E1** tiene datos verificados (2 sentidos, 86 paradas).
- **Ninguna parada tiene coordenadas** todavía, así que la búsqueda por cercanía (GPS) solo funciona con los datos de `sample/`.
- Los horarios de E1 están sin verificar (sin frecuencia), así que las estimaciones no suman tiempo de espera.

Lo que falta está detallado en `docs/PENDIENTES.md` (en la raíz del repo).
