import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { authPlugin } from '@core/auth.guard';
import { PERMISSIONS } from '@core/permissions.constants';

const path = '/reports';

export const ReportsApi = new Elysia()
  .group(path, app => app
    .use(authPlugin)

    // Historial de lecturas por sensor con rango de fechas
    .get('/sensor/:sensor_id', async ({ params, query, status }) => {
      const result = await execProcedure('iot.get_sensor_history', [{
        sensor_id: parseInt(params.sensor_id),
        from_ts: query.from || null,
        to_ts: query.to || null,
        limit: parseInt(query.limit || '500'),
      }]);
      if (result.error) return status(500, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.view_reports,
      query: t.Object({
        from: t.Optional(t.String()),
        to: t.Optional(t.String()),
        limit: t.Optional(t.String()),
      }),
    })

    // Resumen estadístico por sensor (min, max, avg) en un rango
    .get('/sensor/:sensor_id/summary', async ({ params, query, status }) => {
      const result = await execProcedure('iot.get_sensor_summary', [{
        sensor_id: parseInt(params.sensor_id),
        from_ts: query.from || null,
        to_ts: query.to || null,
      }]);
      if (result.error) return status(500, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.view_reports,
      query: t.Object({
        from: t.Optional(t.String()),
        to: t.Optional(t.String()),
      }),
    })

    // Historial de alertas por gateway
    .get('/alerts/:gateway_id', async ({ params, query, status }) => {
      const result = await execProcedure('iot.get_alert_history', [{
        gateway_id: parseInt(params.gateway_id),
        from_ts: query.from || null,
        to_ts: query.to || null,
      }]);
      if (result.error) return status(500, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.view_reports,
      query: t.Object({
        from: t.Optional(t.String()),
        to: t.Optional(t.String()),
      }),
    })
  );
