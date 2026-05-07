import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { authPlugin } from '@core/auth.guard';
import { PERMISSIONS } from '@core/permissions.constants';

const path = '/sensors';

export const SensorsApi = new Elysia()
  .group(path, app => app
    .use(authPlugin)

    .get('/', async ({ query, status }) => {
      const result = await execProcedure('iot.list_sensors', [{ gateway_id: query.gateway_id || null }]);
      if (result.error) return status(500, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.view_sensors,
      query: t.Object({ gateway_id: t.Optional(t.String()) }),
    })

    .get('/:id', async ({ params, status }) => {
      const result = await execProcedure('iot.get_sensor', [{ id: parseInt(params.id) }]);
      if (result.error) return status(500, { message: result.error });
      if (!result.result) return status(404, { message: 'Sensor no encontrado' });
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.view_sensors })

    .post('/', async ({ body, status }) => {
      const result = await execProcedure('iot.save_sensor', [body]);
      if (result.error) return status(400, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.manage_sensors,
      body: t.Object({
        gateway_id: t.Number(),
        name: t.String(),
        identifier: t.String(),
        type: t.Optional(t.String()),
        unit: t.Optional(t.String()),
        location: t.Optional(t.String()),
      }),
    })

    .put('/:id', async ({ params, body, status }) => {
      const result = await execProcedure('iot.save_sensor', [{ id: parseInt(params.id), ...(body as any) }]);
      if (result.error) return status(400, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.manage_sensors,
      body: t.Object({
        name: t.Optional(t.String()),
        location: t.Optional(t.String()),
        enable: t.Optional(t.Boolean()),
      }),
    })

    .get('/gateways', async ({ status }) => {
      const result = await execProcedure('iot.list_gateways', []);
      if (result.error) return status(500, { message: result.error });
      return result.result;
    }, { requirePermission: PERMISSIONS.iot.manage_gateways })

    .post('/gateways', async ({ body, status }) => {
      const result = await execProcedure('iot.save_gateway', [body]);
      if (result.error) return status(400, { message: result.error });
      return result.result;
    }, {
      requirePermission: PERMISSIONS.iot.manage_gateways,
      body: t.Object({
        name: t.String(),
        identifier: t.String(),
        location: t.Optional(t.String()),
      }),
    })
  );
