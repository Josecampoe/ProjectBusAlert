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
| `raw/guia_rutas_setp.md` | Texto original de la guía de rutas entregada por el dueño (fuente de E2–E7 y C1–C16). |

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
- **Ninguna parada tiene coordenadas** todavía, así que la búsqueda por cercanía (GPS) solo funciona con los datos de `sample/`. El reloj pide el lugar por voz o texto.
- Nombres genéricos de la guía (`Calle 17`, `Calle 18`, `Panamericana`…) son **una sola parada** compartida por varias rutas hasta tener coordenadas; los transbordos ahí son aproximados.
- Los horarios de E1 están sin verificar (sin frecuencia), así que las estimaciones no suman tiempo de espera. Las demás rutas no tienen horario.

Lo que falta está detallado en `docs/PENDIENTES.md` (en la raíz del repo).
