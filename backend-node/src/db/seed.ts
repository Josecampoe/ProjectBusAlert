/**
 * CLI: `npm run seed` — importa `DATA_DIR` en la base de datos `DB_PATH`.
 * Opcional: `npm run seed -- --data-dir <ruta>` para importar otra carpeta
 * (por ejemplo `data/sample`).
 */
import 'dotenv/config';
import { openDatabase } from './database';
import { importDataset } from './importer';

const DATA_DIR_FLAG = '--data-dir';

function argValue(flag: string): string | undefined {
    const index = process.argv.indexOf(flag);
    return index >= 0 ? process.argv[index + 1] : undefined;
}

function main(): void {
    const dbPath = process.env.DB_PATH;
    const dataDir = argValue(DATA_DIR_FLAG) ?? process.env.DATA_DIR;
    if (!dbPath || !dataDir) {
        console.error('Faltan DB_PATH y/o DATA_DIR. Revisa tu archivo .env (plantilla: .env.example).');
        process.exit(1);
    }

    const db = openDatabase(dbPath);
    const report = importDataset(db, dataDir);
    db.close();

    console.log(`[seed] Origen: ${dataDir} → ${dbPath}`);
    for (const [entity, count] of Object.entries(report.counts)) console.log(`  ${entity.padEnd(12)} ${count}`);
    for (const w of report.warnings) console.warn(`  ⚠ ${w.file} ${w.location}: ${w.message}`);
    if (!report.ok) {
        console.error(`[seed] ✖ ${report.errors.length} errores; no se escribió nada:`);
        for (const e of report.errors) console.error(`  - ${e.file} [${e.location}] ${e.message}`);
        process.exit(1);
    }
    console.log('[seed] ✔ Importación completada', report.summary);
}

main();
