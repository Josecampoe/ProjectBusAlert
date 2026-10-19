/**
 * schema.ts
 * Definición completa del esquema SQLite para BusAlert SETP Pasto.
 * Ejecuta cada sentencia en orden al inicializar la base de datos.
 */

export const SCHEMA_SQL = `

-- ─────────────────────────────────────────────────────────────────
-- FUENTES DE INFORMACIÓN
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sources (
    id              TEXT PRIMARY KEY,          -- ej: "ruti_setp"
    name            TEXT NOT NULL,
    url             TEXT,
    source_type     TEXT NOT NULL,             -- official_app | official_website | verified_user_input
    last_checked    TEXT,                      -- ISO date string
    reliability     TEXT NOT NULL DEFAULT 'unknown', -- high | medium | low | unknown
    notes           TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ─────────────────────────────────────────────────────────────────
-- RUTAS
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS routes (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    code            TEXT NOT NULL UNIQUE,      -- ej: "E1", "C4"
    name            TEXT NOT NULL,
    type            TEXT NOT NULL,             -- estrategica | complementaria
    description     TEXT,
    origin          TEXT,
    destination     TEXT,
    active          INTEGER NOT NULL DEFAULT 1,  -- 0/1
    source          TEXT REFERENCES sources(id),
    verified        INTEGER NOT NULL DEFAULT 0,
    data_quality    TEXT DEFAULT 'unknown',    -- complete | partial | minimal | unknown
    notes           TEXT,
    last_verified_at TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ─────────────────────────────────────────────────────────────────
-- PARADAS
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stops (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id     TEXT,                      -- código Ruti SETP (ej: "1265"); NULL si no existe
    name            TEXT NOT NULL,
    latitude        REAL,                      -- NULL hasta verificar
    longitude       REAL,                      -- NULL hasta verificar
    address         TEXT,
    neighborhood    TEXT,
    active          INTEGER NOT NULL DEFAULT 1,
    source          TEXT REFERENCES sources(id),
    verified        INTEGER NOT NULL DEFAULT 0,
    notes           TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Índices para búsqueda rápida
CREATE INDEX IF NOT EXISTS idx_stops_external_id ON stops(external_id);
CREATE INDEX IF NOT EXISTS idx_stops_name        ON stops(name);

-- ─────────────────────────────────────────────────────────────────
-- ALIASES DE PARADAS (búsqueda flexible)
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stop_aliases (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    stop_id         INTEGER NOT NULL REFERENCES stops(id) ON DELETE CASCADE,
    alias           TEXT NOT NULL,
    source          TEXT REFERENCES sources(id),
    created_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_stop_aliases_alias   ON stop_aliases(alias);
CREATE INDEX IF NOT EXISTS idx_stop_aliases_stop_id ON stop_aliases(stop_id);

-- ─────────────────────────────────────────────────────────────────
-- DIRECCIONES / SENTIDOS DE RUTA
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS route_directions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    route_id        INTEGER NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    direction_key   TEXT NOT NULL UNIQUE,      -- ej: "E1_D1", "E1_D2"
    name            TEXT NOT NULL,             -- ej: "Udenar → Dolores"
    origin          TEXT,
    destination     TEXT,
    description     TEXT,
    stop_count      INTEGER,
    verified        INTEGER NOT NULL DEFAULT 0,
    source          TEXT REFERENCES sources(id),
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_route_directions_route_id ON route_directions(route_id);

-- ─────────────────────────────────────────────────────────────────
-- PARADAS POR DIRECCIÓN (relación muchos-a-muchos con orden)
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS route_stops (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    route_direction_id          INTEGER NOT NULL REFERENCES route_directions(id) ON DELETE CASCADE,
    stop_id                     INTEGER NOT NULL REFERENCES stops(id),
    stop_order                  INTEGER NOT NULL,
    distance_from_start_m       REAL,          -- metros; NULL hasta tener coordenadas
    estimated_minutes_from_start INTEGER,      -- NULL hasta verificar
    created_at                  TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(route_direction_id, stop_order)
);

CREATE INDEX IF NOT EXISTS idx_route_stops_direction ON route_stops(route_direction_id);
CREATE INDEX IF NOT EXISTS idx_route_stops_stop      ON route_stops(stop_id);

-- ─────────────────────────────────────────────────────────────────
-- HORARIOS
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS schedules (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    route_id            INTEGER NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    day_type            TEXT NOT NULL,         -- LV | LS | S | DF | LD
    day_type_label      TEXT,                  -- "Lunes a Viernes", etc.
    start_time          TEXT,                  -- "HH:MM" ; NULL si no verificado
    end_time            TEXT,                  -- "HH:MM" ; NULL si no verificado
    frequency_minutes   INTEGER,               -- NULL si no verificado
    source              TEXT REFERENCES sources(id),
    verified            INTEGER NOT NULL DEFAULT 0,
    notes               TEXT,
    created_at          TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ─────────────────────────────────────────────────────────────────
-- HISTORIAL DE CAMBIOS EN RUTAS
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS route_changes (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    route_id    INTEGER NOT NULL REFERENCES routes(id),
    change_type TEXT NOT NULL,                 -- add_stop | remove_stop | update_schedule | etc.
    old_value   TEXT,
    new_value   TEXT,
    source      TEXT,
    change_date TEXT NOT NULL DEFAULT (datetime('now')),
    notes       TEXT
);

`;
