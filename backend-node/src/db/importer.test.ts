import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { FIXTURES_DIR } from '../test/helpers';
import { IN_MEMORY_DB, openDatabase } from './database';
import { importDataset } from './importer';

const tempDirs: string[] = [];
function copyFixtures(): string {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'busalert-import-'));
    fs.cpSync(FIXTURES_DIR, dir, { recursive: true });
    tempDirs.push(dir);
    return dir;
}
afterEach(() => tempDirs.splice(0).forEach((d) => fs.rmSync(d, { recursive: true, force: true })));

const count = (db: ReturnType<typeof openDatabase>, table: string) =>
    (db.prepare(`SELECT COUNT(*) AS c FROM ${table}`).get() as { c: number }).c;

describe('importDataset', () => {
    it('importa los fixtures y es idempotente', () => {
        const db = openDatabase(IN_MEMORY_DB);
        const first = importDataset(db, FIXTURES_DIR);
        expect(first.ok).toBe(true);
        expect(first.errors).toEqual([]);
        const snapshot = ['routes', 'stops', 'route_directions', 'route_stops', 'stop_aliases', 'schedules'].map((t) =>
            count(db, t),
        );
        expect(importDataset(db, FIXTURES_DIR).ok).toBe(true);
        expect(['routes', 'stops', 'route_directions', 'route_stops', 'stop_aliases', 'schedules'].map((t) => count(db, t))).toEqual(
            snapshot,
        );
        expect(count(db, 'routes')).toBe(2);
        expect(first.summary.stopsWithoutCoordinates).toBe(1);
    });

    it('actualiza datos existentes en vez de duplicarlos', () => {
        const dir = copyFixtures();
        const db = openDatabase(IN_MEMORY_DB);
        importDataset(db, dir);
        const file = path.join(dir, 'routes', 'routes.json');
        const routes = JSON.parse(fs.readFileSync(file, 'utf-8'));
        routes[0].name = 'Nombre actualizado';
        fs.writeFileSync(file, JSON.stringify(routes));
        expect(importDataset(db, dir).ok).toBe(true);
        expect((db.prepare("SELECT name FROM routes WHERE code = 'FX1'").get() as { name: string }).name).toBe(
            'Nombre actualizado',
        );
        expect(count(db, 'routes')).toBe(2);
    });

    it('rechaza archivos que no cumplen el esquema y no escribe nada', () => {
        const dir = copyFixtures();
        const file = path.join(dir, 'stops', 'stops.json');
        const stops = JSON.parse(fs.readFileSync(file, 'utf-8'));
        stops[0].latitude = 999;
        fs.writeFileSync(file, JSON.stringify(stops));
        const db = openDatabase(IN_MEMORY_DB);
        const report = importDataset(db, dir);
        expect(report.ok).toBe(false);
        expect(report.errors[0]).toMatchObject({ location: '0.latitude' });
        expect(count(db, 'stops')).toBe(0);
    });

    it('reporta referencias rotas entre archivos', () => {
        const dir = copyFixtures();
        const file = path.join(dir, 'routes', 'route_directions.json');
        const dirs = JSON.parse(fs.readFileSync(file, 'utf-8'));
        dirs[0].route_code = 'NOEXISTE';
        fs.writeFileSync(file, JSON.stringify(dirs));
        const report = importDataset(openDatabase(IN_MEMORY_DB), dir);
        expect(report.ok).toBe(false);
        expect(report.errors.map((e) => e.message)).toContain('ruta "NOEXISTE" no existe');
    });

    it('revierte todo si una parada del recorrido no existe', () => {
        const dir = copyFixtures();
        const file = path.join(dir, 'routes', 'route_stops.json');
        const data = JSON.parse(fs.readFileSync(file, 'utf-8'));
        data.FX1_D1.stops[1].stop_external_id = 'T99';
        fs.writeFileSync(file, JSON.stringify(data));
        const db = openDatabase(IN_MEMORY_DB);
        const report = importDataset(db, dir);
        expect(report.ok).toBe(false);
        expect(report.errors[0].location).toBe('FX1_D1.order=2');
        expect(count(db, 'routes')).toBe(0);
    });

    it('importa los datos reales del repositorio sin errores', () => {
        const report = importDataset(openDatabase(IN_MEMORY_DB), path.join(__dirname, '..', '..', 'data'));
        expect(report.errors).toEqual([]);
        expect(report.ok).toBe(true);
    });
});
