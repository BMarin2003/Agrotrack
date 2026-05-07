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
  metric: string;
  min_value: number | null;
  max_value: number | null;
  alert_message: string | null;
}

class RulesEngine {
  async evaluate(reading: Reading) {
    const thresholdsResult = await execProcedure('iot.get_thresholds_for_sensor', [{ sensor_id: reading.sensor_id }]);
    if (thresholdsResult.error || !thresholdsResult.result) return;

    const thresholds: Threshold[] = Array.isArray(thresholdsResult.result)
      ? thresholdsResult.result
      : [thresholdsResult.result];

    for (const threshold of thresholds) {
      const value = reading[threshold.metric as keyof Reading] as number | undefined;
      if (value === undefined || value === null) continue;

      let breached = false;
      let message = '';

      if (threshold.min_value !== null && value < threshold.min_value) {
        breached = true;
        message = threshold.alert_message || `${threshold.metric} bajo umbral mínimo: ${value} < ${threshold.min_value}`;
      } else if (threshold.max_value !== null && value > threshold.max_value) {
        breached = true;
        message = threshold.alert_message || `${threshold.metric} superó umbral máximo: ${value} > ${threshold.max_value}`;
      }

      if (breached) {
        await this.triggerAlert({
          sensor_id: reading.sensor_id,
          gateway_id: reading.gateway_id,
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

  async triggerAlert(alert: {
    sensor_id: number;
    gateway_id: number;
    type: string;
    metric?: string;
    value?: number;
    threshold?: number;
    message: string;
  }) {
    console.warn(`[Rules] ALERTA sensor=${alert.sensor_id} ${alert.message}`);

    const result = await execProcedure('iot.save_alert', [alert]);
    if (result.error) {
      console.error('[Rules] Error al guardar alerta:', result.error);
      return;
    }

    broadcastToGateway(alert.gateway_id, { type: 'alert', data: { ...alert, id: result.result?.id } });
  }
}

export const rulesEngine = new RulesEngine();
