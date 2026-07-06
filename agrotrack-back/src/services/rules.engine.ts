import { execProcedure } from '@core/db/connection';
import { broadcastToGateway } from '@modules/iot/telemetry.ws';

interface Reading {
  sensor_id: number;
  gateway_id: number;
  temperature?: number;
  voltage?: number;
  battery?: number;
  [key: string]: any;
}

interface Threshold {
  id: number;
  user_id: number | null;
  metric: string;
  min_value: number | null;
  max_value: number | null;
  alert_message: string | null;
}

const ANOMALY_DELTA_C        = 8;
const ANOMALY_MIN_C          = -20;
const ANOMALY_MAX_C          = 40;
const ANOMALY_WINDOW_MS      = 10 * 60 * 1000;
const ANOMALY_DEGRADED_COUNT = 5;

class RulesEngine {
  private lastTemperatures = new Map<number, number>();
  private anomalyWindows   = new Map<number, number[]>();

  async evaluate(reading: Reading) {
    const thresholdsResult = await execProcedure('iot.get_thresholds_for_sensor', [{ sensor_id: reading.sensor_id }]);

    if (!thresholdsResult.error && thresholdsResult.result) {
      const thresholds: Threshold[] = Array.isArray(thresholdsResult.result)
        ? thresholdsResult.result
        : [thresholdsResult.result];

      for (const threshold of thresholds) {
        const value = reading[threshold.metric as keyof Reading] as number | undefined;
        if (value === undefined || value === null) continue;

        let breached = false;
        let message  = '';

        if (threshold.min_value !== null && value < threshold.min_value) {
          breached = true;
          message  = threshold.alert_message ?? `${threshold.metric} bajo umbral mínimo: ${value} < ${threshold.min_value}`;
        } else if (threshold.max_value !== null && value > threshold.max_value) {
          breached = true;
          message  = threshold.alert_message ?? `${threshold.metric} superó umbral máximo: ${value} > ${threshold.max_value}`;
        }

        if (breached) {
          await this.triggerAlert({
            sensor_id: reading.sensor_id,
            gateway_id: reading.gateway_id,
            user_id: threshold.user_id,
            type: 'threshold_exceeded',
            metric: threshold.metric,
            value,
            threshold: threshold.min_value !== null && value < threshold.min_value
              ? threshold.min_value
              : threshold.max_value!,
            message,
          });
        }
      }
    }

    if (reading.temperature !== undefined && reading.temperature !== null) {
      await this.evaluateAnomaly(reading.sensor_id, reading.gateway_id, reading.temperature);
    }
  }

  private async evaluateAnomaly(sensorId: number, gatewayId: number, temperature: number) {
    const prev         = this.lastTemperatures.get(sensorId);
    const isOutOfRange = temperature < ANOMALY_MIN_C || temperature > ANOMALY_MAX_C;
    const isDeltaSpike = prev !== undefined && Math.abs(temperature - prev) > ANOMALY_DELTA_C;

    if (isOutOfRange || isDeltaSpike) {
      const message = isOutOfRange
        ? `Lectura fuera de rango físico: T=${temperature}°C (rango válido: ${ANOMALY_MIN_C}°C – ${ANOMALY_MAX_C}°C)`
        : `Salto de temperatura anómalo: ${prev}°C → ${temperature}°C (delta: ${Math.abs(temperature - prev!).toFixed(1)}°C)`;

      await this.triggerAlert({
        sensor_id: sensorId,
        gateway_id: gatewayId,
        user_id: null,
        type: 'anomalous_reading',
        metric: 'temperature',
        value: temperature,
        threshold: prev,
        message,
      });

      const now    = Date.now();
      const window = (this.anomalyWindows.get(sensorId) ?? []).filter(t => now - t < ANOMALY_WINDOW_MS);
      window.push(now);
      this.anomalyWindows.set(sensorId, window);

      if (window.length >= ANOMALY_DEGRADED_COUNT) {
        this.anomalyWindows.set(sensorId, []);
        await this.triggerAlert({
          sensor_id: sensorId,
          gateway_id: gatewayId,
          user_id: null,
          type: 'sensor_degraded',
          metric: 'temperature',
          message: `Sensor ${sensorId} posiblemente defectuoso: ${window.length} lecturas anómalas en 10 minutos`,
        });
      }
    } else {
      this.lastTemperatures.set(sensorId, temperature);
    }
  }

  async triggerAlert(alert: {
    sensor_id: number;
    gateway_id: number;
    user_id?: number | null;
    type: string;
    metric?: string;
    value?: number;
    threshold?: number;
    message: string;
  }) {
    console.warn(`[Rules] ALERTA sensor=${alert.sensor_id} tipo=${alert.type} user=${alert.user_id ?? 'sistema'} — ${alert.message}`);

    const result = await execProcedure('iot.save_alert', [alert]);
    if (result.error) {
      console.error('[Rules] Error al guardar alerta:', result.error);
      return;
    }

    broadcastToGateway(alert.gateway_id, { type: 'alert', data: { ...alert, id: result.result?.id } });
  }
}

export const rulesEngine = new RulesEngine();
