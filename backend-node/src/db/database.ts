/**
 * Apertura de la base de datos SQLite (módulo integrado `node:sqlite`)
 * y ejecución de migraciones versionadas.
 */
import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { MIGRATIONS } from './migrations';

export type Db = DatabaseSync;

/** Ruta especial para bases de datos en memoria (pruebas). */
export const IN_MEMORY_DB = ':memory:';

export function openDatabase(dbPath: string): Db {
  if (dbPath !== IN_MEMORY_DB) {
    fs.mkdirSync(path.dirname(path.resolve(dbPath)), { recursive: true });
  }
  const db = new DatabaseSync(dbPath);
  if (dbPath !== IN_MEMORY_DB) {
    db.exec('PRAGMA journal_mode = WAL');
  }
  db.exec('PRAGMA foreign_keys = ON');
  runMigrations(db);
  return db;
}

/** Aplica, en orden y dentro de una transacción cada una, las migraciones pendientes. */
export function runMigrations(db: Db): number[] {
  db.exec(`
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version    INTEGER PRIMARY KEY,
            name       TEXT NOT NULL,
            applied_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
    `);
  const appliedRows = db.prepare('SELECT version FROM schema_migrations').all() as Array<{
    version: number;
  }>;
  const applied = new Set(appliedRows.map((row) => row.version));

  const newlyApplied: number[] = [];
  for (const migration of [...MIGRATIONS].sort((a, b) => a.version - b.version)) {
    if (applied.has(migration.version)) continue;
    transaction(db, () => {
      db.exec(migration.sql);
      db.prepare('INSERT INTO schema_migrations (version, name) VALUES (?, ?)').run(
        migration.version,
        migration.name,
      );
    });
    newlyApplied.push(migration.version);
  }
  return newlyApplied;
}

/** Ejecuta `fn` dentro de una transacción; revierte si lanza. */
export function transaction<T>(db: Db, fn: () => T): T {
  db.exec('BEGIN');
  try {
    const result = fn();
    db.exec('COMMIT');
    return result;
  } catch (err) {
    db.exec('ROLLBACK');
    throw err;
  }
}
