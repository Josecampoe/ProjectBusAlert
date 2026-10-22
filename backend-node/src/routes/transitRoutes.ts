/**
 * Endpoints de consulta de rutas, paradas y planificador.
 *
 * GET /api/routes                 · GET /api/routes/search?q=
 * GET /api/routes/:code           · GET /api/routes/:code/directions
 * GET /api/routes/:code/stops
 * GET /api/stops                  · GET /api/stops/search?q=
 * GET /api/stops/:id              · GET /api/stops/:id/routes
 * GET /api/route-planner?origin=&destination=
 */
import { Router } from 'express';
import type { AppConfig } from '../config/env';
import { AppError, ErrorCode } from '../errors/AppError';
import type { TransitRepository } from '../repositories/transitRepository';
import { resolvePlace } from '../services/placeResolver';
import { planTrip, toStopRef } from '../services/planner';
import { validate } from '../middleware/validate';
import type { Schemas } from './schemas';

export function createTransitRouter(
  repo: TransitRepository,
  schemas: Schemas,
  config: AppConfig,
): Router {
  const router = Router();
  const resolverOptions = {
    minTermLength: config.input.searchMinTermLength,
    maxResults: config.input.searchMaxResults,
  };

  const findRoute = (code: string) => {
    const route = repo.getRouteByCode(code);
    if (!route) throw new AppError(ErrorCode.ROUTE_NOT_FOUND, 'Ruta no encontrada');
    return route;
  };
  const findStop = (id: string) => {
    const stop = repo.getStop(id);
    if (!stop) throw new AppError(ErrorCode.STOP_NOT_FOUND, 'Parada no encontrada');
    return stop;
  };

  // ── Rutas ────────────────────────────────────────────────────────────
  router.get('/routes', (_req, res) => {
    const routes = repo.listRoutes();
    res.json({ count: routes.length, routes });
  });

  router.get('/routes/search', validate('query', schemas.searchQuery), (_req, res) => {
    const { q } = res.locals.query as { q: string };
    const routes = repo.searchRoutes(q);
    res.json({ query: q, count: routes.length, routes });
  });

  router.get('/routes/:code', validate('params', schemas.routeParams), (_req, res) => {
    const route = findRoute((res.locals.params as { code: string }).code);
    res.json({
      route,
      directions: repo.getDirections(route.id),
      schedules: repo.getSchedules(route.id),
    });
  });

  router.get('/routes/:code/directions', validate('params', schemas.routeParams), (_req, res) => {
    const route = findRoute((res.locals.params as { code: string }).code);
    const directions = repo.getDirections(route.id);
    res.json({ routeCode: route.code, count: directions.length, directions });
  });

  router.get('/routes/:code/stops', validate('params', schemas.routeParams), (_req, res) => {
    const route = findRoute((res.locals.params as { code: string }).code);
    const directions = repo.getDirections(route.id).map((dir) => {
      const stops = repo.getStopsForDirection(dir.id);
      return { ...dir, stopCount: stops.length, stops };
    });
    res.json({ routeCode: route.code, directions });
  });

  // ── Paradas ──────────────────────────────────────────────────────────
  router.get('/stops', (_req, res) => {
    const stops = repo.listStops();
    res.json({ count: stops.length, stops });
  });

  router.get('/stops/search', validate('query', schemas.searchQuery), (_req, res) => {
    const { q } = res.locals.query as { q: string };
    const { stops, matchKind } = resolvePlace(q, repo.getNetwork(), resolverOptions);
    const enriched = stops.map(({ searchNames: _names, ...stop }) => ({
      ...stop,
      routes: repo.getRoutesAtStop(stop.id),
    }));
    res.json({ query: q, matchKind, count: enriched.length, stops: enriched });
  });

  router.get('/stops/:id', validate('params', schemas.stopParams), (_req, res) => {
    const stop = findStop((res.locals.params as { id: string }).id);
    res.json({ stop, aliases: repo.getAliases(stop.id) });
  });

  router.get('/stops/:id/routes', validate('params', schemas.stopParams), (_req, res) => {
    const stop = findStop((res.locals.params as { id: string }).id);
    const routes = repo.getRoutesAtStop(stop.id);
    res.json({ stopId: stop.id, stopName: stop.name, count: routes.length, routes });
  });

  // ── Planificador ─────────────────────────────────────────────────────
  router.get('/route-planner', validate('query', schemas.plannerQuery), (_req, res) => {
    const { origin, destination } = res.locals.query as { origin: string; destination: string };
    const network = repo.getNetwork();
    const from = resolvePlace(origin, network, resolverOptions);
    const to = resolvePlace(destination, network, resolverOptions);

    const base = {
      origin,
      destination,
      originCandidates: from.stops.map(toStopRef),
      destinationCandidates: to.stops.map(toStopRef),
    };
    if (from.stops.length === 0) {
      res.json({ ...base, status: 'origin_not_found', plans: [] });
      return;
    }
    if (to.stops.length === 0) {
      res.json({ ...base, status: 'destination_not_found', plans: [] });
      return;
    }
    const plan = planTrip(
      network,
      from.stops.map((s) => s.id),
      to.stops.map((s) => s.id),
      {
        maxAlternatives: config.planner.maxAlternatives,
        minutesPerStop: config.planner.minutesPerStop,
      },
    );
    res.json({ ...base, status: plan.status, plans: plan.options });
  });

  return router;
}
