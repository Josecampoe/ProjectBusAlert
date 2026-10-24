/**
 * Servicio del asistente de viaje.
 *
 * Flujo:
 *   1. Resuelve origen y destino a paradas reales (sin IA).
 *   2. Calcula alternativas con el planificador.
 *   3. Si hay IA configurada, le pide redactar la respuesta eligiendo entre
 *      esas alternativas. Si la IA falla o responde algo inválido, se
 *      devuelve el resultado del planificador sin texto generado.
 */
import type { AppConfig } from '../config/env';
import type { Logger } from '../lib/logger';
import { normalizeForSearch, sanitizeUserText } from '../lib/text';
import type {
  NetworkStop,
  TransitNetwork,
  TransitRepository,
} from '../repositories/transitRepository';
import type { AiClient } from './ai/aiClient';
import {
  buildUserContent,
  parseAiJson,
  toResponseSchema,
  TransportAnswerSchema,
  transportSystemInstruction,
  TripAnswerSchema,
  tripSystemInstruction,
  type Language,
} from './ai/prompts';
import type { ArrivalEstimate, ArrivalTimeProvider } from './arrivalTime';
import { findNearbyStops, type GeoPoint } from './nearby';
import { planTrip, toStopRef, type PlanOption, type StopRef, type TransferStep } from './planner';
import { resolvePlace } from './placeResolver';

export type TripStatus =
  | 'ok'
  | 'same_place'
  | 'no_route'
  | 'origin_not_found'
  | 'destination_not_found'
  | 'no_nearby_stops';

export type AnswerSource = 'ai' | 'planner';

/** Origen del viaje: texto dicho/escrito o coordenadas del GPS. */
export type TripOrigin = { kind: 'text'; text: string } | { kind: 'coordinates'; point: GeoPoint };

export interface TripRequest {
  destination: string;
  origin: TripOrigin;
  language: Language;
}

export interface TripMessage {
  spokenText: string;
  displayTitle: string;
  displaySubtitle: string;
}

export interface LegDto {
  routeCode: string;
  routeName: string;
  routeColor: string | null;
  directionKey: string;
  directionName: string;
  board: StopRef;
  alight: StopRef;
  stopsCount: number;
  estimate: ArrivalEstimate;
}

export interface OptionDto {
  legs: LegDto[];
  transfers: number;
  transferSteps: TransferStep[];
  totalStops: number;
  estimatedMinutes: number;
  isEstimate: true;
  isDemo: boolean;
}

export interface OriginCandidate extends StopRef {
  /** Distancia desde la ubicación del usuario (solo si el origen es GPS). */
  distanceMeters: number | null;
}

export interface TripResponse {
  status: TripStatus;
  source: AnswerSource;
  message: TripMessage | null;
  recommended: OptionDto | null;
  alternatives: OptionDto[];
  originCandidates: OriginCandidate[];
  destinationCandidates: StopRef[];
  isDemoData: boolean;
}

export type TransportStatus = 'ok' | 'insufficient_data';

export interface TransportResponse {
  status: TransportStatus;
  answer: string | null;
  routeCodes: string[];
  stops: StopRef[];
}

export function toOptionDto(option: PlanOption): OptionDto {
  return {
    legs: option.legs.map(({ stops: _segment, ...leg }) => leg),
    transfers: option.transfers,
    transferSteps: option.transferSteps,
    totalStops: option.totalStops,
    estimatedMinutes: option.estimatedMinutes,
    isEstimate: true,
    isDemo: option.isDemo,
  };
}

export class AssistantService {
  constructor(
    private readonly repo: TransitRepository,
    private readonly ai: AiClient | null,
    private readonly arrival: ArrivalTimeProvider,
    private readonly config: AppConfig,
    private readonly logger: Logger,
    private readonly now: () => Date = () => new Date(),
  ) {}

  get aiEnabled(): boolean {
    return this.ai !== null;
  }

  private sanitize(text: string): string {
    return sanitizeUserText(text, this.config.input.maxQueryLength);
  }

  private resolve(network: TransitNetwork, text: string) {
    return resolvePlace(text, network, {
      minTermLength: this.config.input.searchMinTermLength,
      maxResults: this.config.input.searchMaxResults,
    });
  }

  private resolveOrigin(
    network: TransitNetwork,
    origin: TripOrigin,
  ): { stops: NetworkStop[]; candidates: OriginCandidate[]; textForAi: string } {
    if (origin.kind === 'text') {
      const text = this.sanitize(origin.text);
      const stops = this.resolve(network, text).stops;
      return {
        stops,
        candidates: stops.map((s) => ({ ...toStopRef(s), distanceMeters: null })),
        textForAi: text,
      };
    }
    const nearby = findNearbyStops(
      network,
      origin.point,
      this.config.nearby.defaultRadiusM,
      this.config.nearby.maxResults,
    );
    return {
      stops: nearby.map((n) => n.stop),
      candidates: nearby.map((n) => ({
        ...toStopRef(n.stop),
        distanceMeters: Math.round(n.distanceMeters),
      })),
      // A la IA no se le envían las coordenadas del usuario: solo la parada más cercana.
      textForAi: nearby[0]?.stop.name ?? '',
    };
  }

  /** Planificación con los parámetros configurados (también la usa /api/route-planner). */
  plan(network: TransitNetwork, originIds: number[], destinationIds: number[]) {
    return planTrip(
      network,
      originIds,
      destinationIds,
      {
        maxAlternatives: this.config.planner.maxAlternatives,
        maxTransfers: this.config.planner.maxTransfers,
        transferPenaltyMin: this.config.planner.transferPenaltyMin,
        transferWalkRadiusM: this.config.planner.transferWalkRadiusM,
        preferDirect: this.config.planner.preferDirect,
      },
      this.arrival,
      this.now(),
    );
  }

  async planTrip(request: TripRequest): Promise<TripResponse> {
    const network = this.repo.getNetwork();
    const destinationText = this.sanitize(request.destination);
    const origin = this.resolveOrigin(network, request.origin);
    const destination = this.resolve(network, destinationText);
    const base = {
      source: 'planner' as const,
      message: null,
      recommended: null,
      alternatives: [],
      originCandidates: origin.candidates,
      destinationCandidates: destination.stops.map(toStopRef),
      isDemoData: [...origin.stops, ...destination.stops].some((s) => s.isDemo),
    };

    if (origin.stops.length === 0) {
      const status = request.origin.kind === 'coordinates' ? 'no_nearby_stops' : 'origin_not_found';
      return { ...base, status };
    }
    if (destination.stops.length === 0) return { ...base, status: 'destination_not_found' };

    const plan = this.plan(
      network,
      origin.stops.map((s) => s.id),
      destination.stops.map((s) => s.id),
    );
    if (plan.status !== 'ok') return { ...base, status: plan.status };

    const options = plan.options.map(toOptionDto);
    const isDemoData = base.isDemoData || options.some((o) => o.isDemo);
    const aiResult = await this.composeWithAi(
      options,
      origin.textForAi,
      destinationText,
      request.language,
    );
    const recommendedIndex = aiResult?.index ?? 0;

    return {
      ...base,
      status: 'ok',
      source: aiResult ? 'ai' : 'planner',
      message: aiResult?.message ?? null,
      recommended: options[recommendedIndex],
      alternatives: options.filter((_, i) => i !== recommendedIndex),
      isDemoData,
    };
  }

  private async composeWithAi(
    options: OptionDto[],
    originText: string,
    destinationText: string,
    language: Language,
  ): Promise<{ index: number; message: TripMessage } | undefined> {
    if (!this.ai) return undefined;
    const offered = options.slice(0, this.config.ai.maxPlanOptions);
    const context = {
      options: offered.map((o, index) => ({
        index,
        transfers: o.transfers,
        legs: o.legs.map((l) => ({
          route: l.routeCode,
          direction: l.directionName,
          boardAt: l.board.name,
          alightAt: l.alight.name,
          stopsCount: l.stopsCount,
        })),
      })),
    };
    try {
      const raw = await this.ai.generateJson({
        systemInstruction: tripSystemInstruction(language, this.config.data.cityName),
        userContent: buildUserContent(context, {
          origin: originText,
          destination: destinationText,
        }),
        responseSchema: toResponseSchema(TripAnswerSchema),
      });
      const parsed = parseAiJson(raw, TripAnswerSchema);
      if (!parsed || parsed.chosenOptionIndex >= offered.length) {
        this.logger.warn('ai_output_rejected', {
          reason: parsed ? 'option_out_of_range' : 'schema',
        });
        return undefined;
      }
      const { chosenOptionIndex, ...message } = parsed;
      return { index: chosenOptionIndex, message };
    } catch (err) {
      this.logger.warn('ai_compose_failed', { error: err instanceof Error ? err.name : 'unknown' });
      return undefined;
    }
  }

  /**
   * Pregunta libre sobre el transporte. La IA solo ve datos de la BD
   * relacionados con la pregunta. Lanza si la IA no está disponible.
   */
  async answerQuestion(question: string, language: Language): Promise<TransportResponse | null> {
    if (!this.ai) return null;
    const network = this.repo.getNetwork();
    const text = this.sanitize(question);
    const place = this.resolve(network, text);

    const normalized = ` ${normalizeForSearch(text)} `;
    const mentionedRoutes = [...network.routes.values()].filter((r) =>
      normalized.includes(` ${normalizeForSearch(r.code)} `),
    );
    const routesAtStops = new Map<string, { code: string; directions: Set<string> }>();
    for (const stop of place.stops) {
      for (const dir of network.directionsByStop.get(stop.id) ?? []) {
        const route = network.routes.get(dir.routeId)!;
        const entry = routesAtStops.get(route.code) ?? {
          code: route.code,
          directions: new Set<string>(),
        };
        entry.directions.add(dir.name);
        routesAtStops.set(route.code, entry);
      }
    }
    for (const route of mentionedRoutes) {
      if (!routesAtStops.has(route.code)) {
        const dirs = network.directions.filter((d) => d.routeId === route.id).map((d) => d.name);
        routesAtStops.set(route.code, { code: route.code, directions: new Set(dirs) });
      }
    }

    const stops = place.stops.map(toStopRef);
    const allowedCodes = new Set(routesAtStops.keys());
    const context = {
      stops: stops.map((s) => ({ name: s.name, code: s.externalId })),
      routes: [...routesAtStops.values()].map((r) => ({
        code: r.code,
        directions: [...r.directions],
      })),
    };
    if (allowedCodes.size === 0 && stops.length === 0) {
      return { status: 'insufficient_data', answer: null, routeCodes: [], stops: [] };
    }

    const raw = await this.ai.generateJson({
      systemInstruction: transportSystemInstruction(language, this.config.data.cityName),
      userContent: buildUserContent(context, { question: text }),
      responseSchema: toResponseSchema(TransportAnswerSchema),
    });
    const parsed = parseAiJson(raw, TransportAnswerSchema);
    const invented = parsed?.routeCodes.some((code) => !allowedCodes.has(code)) ?? false;
    if (!parsed || invented) {
      this.logger.warn('ai_output_rejected', { reason: parsed ? 'unknown_route_code' : 'schema' });
      return { status: 'insufficient_data', answer: null, routeCodes: [...allowedCodes], stops };
    }
    return {
      status: parsed.hasEnoughInformation ? 'ok' : 'insufficient_data',
      answer: parsed.answer,
      routeCodes: parsed.routeCodes,
      stops,
    };
  }
}
