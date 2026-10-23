# Imagen de producción del backend BusAlert.
# Construir:  docker build -t busalert-backend .
# Ejecutar:   docker run -p 3000:3000 -e API_KEYS=<clave> [-e GEMINI_API_KEY=<clave>] busalert-backend

# ── Compilación ─────────────────────────────────────────────────────────────
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY tsconfig.json tsconfig.build.json ./
COPY src ./src
RUN npm run build

# ── Ejecución ───────────────────────────────────────────────────────────────
FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    DB_PATH=/app/var/busalert.sqlite \
    DATA_DIR=/app/data
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force
COPY --from=build /app/dist ./dist
COPY data ./data
RUN mkdir -p /app/var && chown -R node:node /app/var
USER node
EXPOSE 3000
VOLUME ["/app/var"]
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- "http://127.0.0.1:${PORT}/health" || exit 1
# El seed es idempotente: al arrancar actualiza la BD con los datos de la imagen.
CMD ["sh", "-c", "node dist/db/seed.js && node dist/index.js"]
