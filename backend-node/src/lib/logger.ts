/**
 * Logger mínimo con niveles. Evita depender de una librería externa
 * y permite silenciar la salida en pruebas.
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'silent';

const LEVEL_ORDER: Record<LogLevel, number> = {
    debug: 10,
    info: 20,
    warn: 30,
    error: 40,
    silent: 100,
};

export interface Logger {
    debug(message: string, meta?: Record<string, unknown>): void;
    info(message: string, meta?: Record<string, unknown>): void;
    warn(message: string, meta?: Record<string, unknown>): void;
    error(message: string, meta?: Record<string, unknown>): void;
}

export function createLogger(level: LogLevel): Logger {
    const threshold = LEVEL_ORDER[level];
    const write =
        (lvl: Exclude<LogLevel, 'silent'>, sink: (line: string) => void) =>
        (message: string, meta?: Record<string, unknown>) => {
            if (LEVEL_ORDER[lvl] < threshold) return;
            const line = JSON.stringify({ time: new Date().toISOString(), level: lvl, message, ...meta });
            sink(line);
        };

    return {
        debug: write('debug', (l) => console.debug(l)),
        info: write('info', (l) => console.info(l)),
        warn: write('warn', (l) => console.warn(l)),
        error: write('error', (l) => console.error(l)),
    };
}

export const silentLogger: Logger = createLogger('silent');
