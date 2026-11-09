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
| `places/*.json` | Lugares que no son parada (colegios, barrios…) y las paradas que les quedan cerca. |
| `templates/route.template.json` | Plantilla comentada para agregar una ruta nueva. |
| `sample/` | Datos de **demostración** (no reales), marcados como demo. |
| `raw/guia_rutas_setp.md` | Texto original de la guía de rutas entregada por el dueño (fuente de E2–E7 y C1–C16). |
| `raw/osm_pasto.json` | Lugares con nombre y calles de Pasto descargados de OpenStreetMap (`npm run osm:fetch`). © colaboradores de OpenStreetMap, licencia ODbL. |
| `geo/osm_stop_coordinates.json` | **Generado** por `npm run osm:geocode`: coordenadas de paradas ubicadas con OpenStreetMap y lista de las que no se ubicaron (`not_found`). No se edita a mano. |

## Actualizar las coordenadas desde OpenStreetMap

```
npm run data:update   # = osm:fetch + osm:geocode + seed
```

- `npm run osm:fetch` descarga los lugares de Pasto (si el servidor público está saturado, reintenta y cambia de servidor).
- `npm run osm:geocode` cruza los nombres de las paradas sin coordenadas y regenera `geo/osm_stop_coordinates.json`.
- Las coordenadas escritas a mano en `stops/` nunca se reemplazan.

## Agregar o corregir datos

1. Copia los bloques de `templates/route.template.json` a los archivos indicados.
2. Valida sin escribir nada: `npm run data:validate`.
   El validador revisa el formato de cada campo (coordenadas en rango, horas `HH:MM`, colores `#RRGGBB`), las referencias entre archivos (fuente, ruta, sentido, parada), los duplicados y que cada parada de un recorrido exista.
3. Importa: `npm run seed`. Es idempotente: crea lo nuevo y actualiza lo existente; si hay errores no escribe nada.

## Estado actual

- **23 rutas** (E1–E7, C1–C16), 45 sentidos y 307 paradas.
  - **E1**: datos de Ruti con códigos de parada (`ruti_setp`).
  - **E2–E7 y C1–C16**: orden de paradas de la guía del dueño (`guia_rutas_setp`, ver `raw/`). Las paradas que ya existían en E1 (Fátima, Universidad Cooperativa, Villa del Parque…) se reutilizan para permitir transbordos. Abreviaturas expandidas (`Cl` → Calle, `Cra` → Carrera, `U.` → Universidad) con la forma original como alias.
  - En los retornos se agregó la terminal de la ida al inicio y al final cuando la guía no la repite (queda anotado en la descripción del sentido).
- **197 de 307 paradas tienen coordenadas** obtenidas de OpenStreetMap (D-050): por barrio o lugar, por cruce de calles o por lugar sobre una calle. Las de barrio usan el centro del barrio (100–400 m de margen). Las 110 restantes y el motivo están en `geo/osm_stop_coordinates.json` → `not_found`; si escribes la coordenada en `stops/`, esa gana.
- Nombres genéricos de la guía (`Calle 17`, `Calle 18`, `Panamericana`…) son **una sola parada** compartida por varias rutas hasta tener coordenadas; los transbordos ahí son aproximados.
- **1 lugar de referencia**: Cooperativa San Vicente, cerca de Liceo Udenar (indicado por el dueño). Para agregar otro, copia el bloque en `places/places.json` con el nombre del lugar, alias opcionales y `nearby_stops` (código o nombre de la parada).
- Los horarios de E1 están sin verificar (sin frecuencia), así que las estimaciones no suman tiempo de espera. Las demás rutas no tienen horario.

Lo que falta está detallado en `docs/PENDIENTES.md` (en la raíz del repo).
