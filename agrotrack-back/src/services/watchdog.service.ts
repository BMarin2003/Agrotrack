import { execProcedure } from '@core/db/connection';
import { broadcastToAll } from '@modules/iot/telemetry.ws';

const OFFLINE_THRESHOLD_MS = 30_000; // 30 segundos
const CHECK_INTERVAL_MS = 10_000;    // revisar cada 10 segundos

// sensor_id -> { gateway_id, last_seen }
const heartbeats = new Map<number, { gateway_id: number; last_seen: number }>();

// Sensores ya marcados como offline (para no disparar alertas repetidas)
const offlineSensors = new Set<number>();

class WatchdogService {
  private timer: Timer | null = null;

  start() {
    this.timer = setInterval(() => this.check(), CHECK_INTERVAL_MS);
    console.log('[Watchdog] Iniciado');
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
  }

  heartbeat(sensorId: number, gatewayId?: number) {
    const existing = heartbeats.get(sensorId);
    heartbeats.set(sensorId, {
      gateway_id: gatewayId ?? existing?.gateway_id ?? 0,
      last_seen: Date.now(),
    });

    // Si el sensor vuelve a transmitir, sacarlo de offline
    if (offlineSensors.has(sensorId)) {
      offlineSensors.delete(sensorId);
      console.log(`[Watchdog] Sensor ${sensorId} volvió a transmitir`);
    }
  }

  private async check() {
    const now = Date.now();

    for (const [sensorId, data] of heartbeats) {
      const silentMs = now - data.last_seen;

      if (silentMs > OFFLINE_THRESHOLD_MS && !offlineSensors.has(sensorId)) {
        offlineSensors.add(sensorId);
        console.warn(`[Watchdog] Sensor ${sensorId} sin transmitir por ${Math.round(silentMs / 1000)}s`);

        await execProcedure('iot.save_alert', [{
          sensor_id: sensorId,
          gateway_id: data.gateway_id,
          type: 'sensor_offline',
          metric: null,
          value: null,
          threshold: null,
          message: `Sensor ${sensorId} sin transmitir por más de ${OFFLINE_THRESHOLD_MS / 1000} segundos`,
        }]);

        broadcastToAll({
          type: 'alert',
          data: {
            sensor_id: sensorId,
            gateway_id: data.gateway_id,
            type: 'sensor_offline',
            message: `Sensor ${sensorId} offline`,
            silent_seconds: Math.round(silentMs / 1000),
          },
        });
      }
    }
  }
}

export const watchdogService = new WatchdogService();
