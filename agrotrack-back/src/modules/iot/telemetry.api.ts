import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { authPlugin } from '@core/auth.guard';
import { PERMISSIONS } from '@core/permissions.constants';
import { rulesEngine } from '@services/rules.engine';
import { watchdogService } from '@services/watchdog.service';
import { broadcastToGateway } from '@modules/iot/telemetry.ws';

const path = '/telemetry';

export const TelemetryApi = new Elysia()
  .group(path, app => app
    .use(authPlugin)

    // Ingesta HTTP directa (fallback cuando no hay broker MQTT)
    .post('/ingest', async ({ body, status, headers }) => {
      const apiKey = headers['x-api-key'];
      if (!apiKey) return status(401, { message: 'API Key requerida' });

      const gatewayResult = await execProcedure('iot.get_gateway_by_api_key', [{ api_key: apiKey }]);
      if (gatewayResult.error || !gatewayResult.result) {
        return status(401, { message: 'Gateway no autorizado' });
      }

      const gateway = gatewayResult.result;
      const payload = body as any;

      const saveResult = await execProcedure('iot.save_reading', [{
        sensor_id: payload.sensor_id,
        gateway_id: gateway.id,
        temperature: payload.temperature,
        voltage: payload.voltage,
        battery: payload.battery,
        extra_data: payload.extra_data || null,
      }]);

      if (saveResult.error) return status(500, { message: saveResult.error });

      const reading = { ...payload, gateway_id: gateway.id, received_at: new Date().toISOString() };

      watchdogService.heartbeat(payload.sensor_id);
      await rulesEngine.evaluate(reading);
      broadcastToGateway(gateway.id, { type: 'reading', data: reading });

      return { ok: true, reading_id: saveResult.result?.id };
    }, {
      body: t.Object({
        sensor_id: t.Number(),
        temperature: t.Optional(t.Number()),
        voltage: t.Optional(t.Number()),
        battery: t.Optional(t.Number()),
        extra_data: t.Optional(t.Any()),
      }),
    })

    // Última lectura por gateway (snapshot en tiempo real)
    .get('/latest/:gateway_id', async ({ params, status }) => {
      const result = await execProcedure('iot.get_latest_readings_by_gateway', [{ gateway_id: parseInt(params.gateway_id) }]);
      if (result.error) return status(500, { message: result.error });
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.view_telemetry })
  );
