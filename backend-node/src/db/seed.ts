/**
 * CLI de importación de datos.
 *
 *   npm run seed                         importa DATA_DIR en DB_PATH
 *   npm run seed -- --data-dir <ruta>    importa otra carpeta (p. ej. data/sample)
 *   npm run seed -- --dry-run            solo valida (BD en memoria, no escribe nada)
 */
import 'dotenv/config';
import { ConfigError, loadSeedConfig, type SeedConfig } from '../config/env';
import { IN_MEMORY_DB, openDatabase } from './database';
import { importDataset } from './importer';

const DATA_DIR_FLAG = '--data-dir';
const DRY_RUN_FLAG = '--dry-run';

function argValue(flag: string): string | undefined {
  // Gana el último: así `npm run data:validate -- --data-dir <ruta>` sustituye la carpeta por defecto.
  const index = process.argv.lastIndexOf(flag);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

function main(): void {
  const dryRun = process.argv.includes(DRY_RUN_FLAG);
  const overrides: Partial<Record<'DB_PATH' | 'DATA_DIR', string>> = {};
  if (dryRun) overrides.DB_PATH = IN_MEMORY_DB;
  const dataDirArg = argValue(DATA_DIR_FLAG);
  if (dataDirArg !== undefined) overrides.DATA_DIR = dataDirArg;

  let config: SeedConfig;
  try {
    config = loadSeedConfig(undefined, overrides);
  } catch (err) {
    if (!(err instanceof ConfigError)) throw err;
    console.error(`${err.message}
Revisa tu archivo .env (plantilla: .env.example).`);
    process.exit(1);
  }
  const { dbPath, dataDir } = config;

  const db = openDatabase(dbPath);
  const report = importDataset(db, dataDir);
  db.close();

  console.log(
    `[seed] ${dryRun ? 'Validación (sin escribir)' : 'Importación'}: ${dataDir} → ${dbPath}`,
  );
  for (const [entity, count] of Object.entries(report.counts)) {
    console.log(`  ${entity.padEnd(12)} ${String(count)}`);
  }
  for (const w of report.warnings) console.warn(`  ⚠ ${w.file} ${w.location}: ${w.message}`);
  if (!report.ok) {
    console.error(`[seed] ✖ ${String(report.errors.length)} errores; no se escribió nada:`);
    for (const e of report.errors) console.error(`  - ${e.file} [${e.location}] ${e.message}`);
    process.exit(1);
  }
  console.log(`[seed] ✔ ${dryRun ? 'Datos válidos' : 'Importación completada'}`, report.summary);
}

main();
