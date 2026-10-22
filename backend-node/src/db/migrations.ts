/**
 * Migraciones versionadas del esquema. Nunca se edita una migración ya
 * publicada: los cambios se agregan como una versión nueva al final.
 */
export interface Migration {
    version: number;
    name: string;
    sql: string;
}

export const MIGRATIONS: Migration[] = [
    {
        version: 1,
        name: 'esquema_inicial',
        sql: `
CREATE TABLE IF NOT EXISTS sources (
    id              TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    url             TEXT,
    source_type     TEXT NOT NULL,             -- official_app | official_website | verified_user_input | demo_sample
    last_checked    TEXT,
    reliability     TEXT NOT NULL DEFAULT 'unknown',
    notes           TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS routes (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    code             TEXT NOT NULL UNIQUE,
    name             TEXT NOT NULL,
    type             TEXT NOT NULL,
    description      TEXT,
    origin           TEXT,
    destination      TEXT,
    active           INTEGER NOT NULL DEFAULT 1,
    source           TEXT REFERENCES sources(id),
    verified         INTEGER NOT NULL DEFAULT 0,
    data_quality     TEXT DEFAULT 'unknown',
    notes            TEXT,
    last_verified_at TEXT,
    created_at       TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS stops (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id     TEXT UNIQUE,
    name            TEXT NOT NULL,
    latitude        REAL,
    longitude       REAL,
    address         TEXT,
    neighborhood    TEXT,
    active          INTEGER NOT NULL DEFAULT 1,
    source          TEXT REFERENCES sources(id),
    verified        INTEGER NOT NULL DEFAULT 0,
    notes           TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_stops_name     ON stops(name);
CREATE INDEX IF NOT EXISTS idx_stops_lat_lng  ON stops(latitude, longitude);

CREATE TABLE IF NOT EXISTS stop_aliases (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    stop_id     INTEGER NOT NULL REFERENCES stops(id) ON DELETE CASCADE,
    alias       TEXT NOT NULL,
    source      TEXT REFERENCES sources(id),
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(stop_id, alias)
);
CREATE INDEX IF NOT EXISTS idx_stop_aliases_stop_id ON stop_aliases(stop_id);

CREATE TABLE IF NOT EXISTS route_directions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    route_id        INTEGER NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    direction_key   TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
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

CREATE TABLE IF NOT EXISTS route_stops (
    id                           INTEGER PRIMARY KEY AUTOINCREMENT,
    route_direction_id           INTEGER NOT NULL REFERENCES route_directions(id) ON DELETE CASCADE,
    stop_id                      INTEGER NOT NULL REFERENCES stops(id),
    stop_order                   INTEGER NOT NULL,
    distance_from_start_m        REAL,
    estimated_minutes_from_start INTEGER,
    created_at                   TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(route_direction_id, stop_order)
);
CREATE INDEX IF NOT EXISTS idx_route_stops_direction ON route_stops(route_direction_id);
CREATE INDEX IF NOT EXISTS idx_route_stops_stop      ON route_stops(stop_id);

CREATE TABLE IF NOT EXISTS schedules (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    route_id          INTEGER NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    day_type          TEXT NOT NULL,
    day_type_label    TEXT,
    start_time        TEXT,
    end_time          TEXT,
    frequency_minutes INTEGER,
    source            TEXT REFERENCES sources(id),
    verified          INTEGER NOT NULL DEFAULT 0,
    notes             TEXT,
    created_at        TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(route_id, day_type)
);

CREATE TABLE IF NOT EXISTS route_changes (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    route_id    INTEGER NOT NULL REFERENCES routes(id),
    change_type TEXT NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    source      TEXT,
    change_date TEXT NOT NULL DEFAULT (datetime('now')),
    notes       TEXT
);
`,
    },
];
