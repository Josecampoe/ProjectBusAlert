/**
 * CLI de importación de datos.
 *
 *   npm run seed                         importa DATA_DIR en DB_PATH
 *   npm run seed -- --data-dir <ruta>    importa otra carpeta (p. ej. data/sample)
 *   npm run seed -- --dry-run            solo valida (BD en memoria, no escribe nada)
 */
import 'dotenv/config';
import { IN_MEMORY_DB, openDatabase } from './database';
import { importDataset } from './importer';

const DATA_DIR_FLAG = '--data-dir';
const DRY_RUN_FLAG = '--dry-run';

function argValue(flag: string): string | undefined {
  const index = process.argv.indexOf(flag);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

function main(): void {
  const dryRun = process.argv.includes(DRY_RUN_FLAG);
  const dbPath = dryRun ? IN_MEMORY_DB : process.env.DB_PATH;
  const dataDir = argValue(DATA_DIR_FLAG) ?? process.env.DATA_DIR;
  if (!dbPath || !dataDir) {
    console.error('Faltan DB_PATH y/o DATA_DIR. Revisa tu archivo .env (plantilla: .env.example).');
    process.exit(1);
  }

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
