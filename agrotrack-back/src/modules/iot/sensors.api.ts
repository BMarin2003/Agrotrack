import { Elysia, t } from "elysia";
import { execProcedure } from "@core/db/connection";
import { authPlugin } from "@core/auth.guard";
import { PERMISSIONS } from "@core/permissions.constants";
import { mqttService } from "@services/mqtt.service";
import { mockFleetService } from "@services/mock-fleet.service";

const path = "/sensors";

export const SensorsApi = new Elysia().group(path, (app) =>
  app
    .use(authPlugin)

    .get(
      "/gateways",
      async ({ set }) => {
        const result = await execProcedure("iot.list_gateways", []);

        if (result.error) {
          set.status = 500;
          return { message: result.error };
        }

        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.view_sensors,
      },
    )

    .get(
      "/",
      async ({ query, user, set }) => {
        const result = await execProcedure("iot.list_sensors", [
          { gateway_id: query.gateway_id || null, user_id: (user as any).id },
        ]);

        if (result.error) {
          set.status = 500;
          return { message: result.error };
        }

        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.view_sensors,
        query: t.Object({
          gateway_id: t.Optional(t.String()),
        }),
      },
    )

    .get(
      "/gateways/:id/maintenance",
      async ({ params, set }) => {
        const result = await execProcedure("iot.list_maintenance", [
          { gateway_id: parseInt(params.id) },
        ]);
        if (result.error) { set.status = 500; return { message: result.error }; }
        return result.result ?? [];
      },
      { requirePermission: PERMISSIONS.iot.view_sensors },
    )

    .post(
      "/gateways/:id/maintenance",
      async ({ params, body, set }) => {
        const result = await execProcedure("iot.register_maintenance", [
          { gateway_id: parseInt(params.id), ...(body as any) },
        ]);
        if (result.error) { set.status = 400; return { message: result.error }; }
        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_gateways,
        body: t.Object({
          notes:          t.Optional(t.String()),
          next_scheduled: t.Optional(t.String()),
        }),
      },
    )

    .get(
      "/:id/calibration",
      async ({ params, set }) => {
        const result = await execProcedure("iot.get_calibration", [
          { sensor_id: parseInt(params.id) },
        ]);
        if (result.error) { set.status = 500; return { message: result.error }; }
        return result.result;
      },
      { requirePermission: PERMISSIONS.iot.view_sensors },
    )

    .post(
      "/:id/calibration",
      async ({ params, body, user, set }) => {
        const sensorId = parseInt(params.id);
        const result = await execProcedure("iot.save_calibration", [
          { sensor_id: sensorId, user_id: (user as any).id, ...(body as any) },
        ]);
        if (result.error) { set.status = 400; return { message: result.error }; }

        // Publicar el comando al gateway físico — best-effort. La calibración
        // ya quedó guardada en BD aunque el publish falle o no exista
        // firmware que lo escuche; ack_status se resuelve por timeout en la app.
        const sensorResult = await execProcedure("iot.get_sensor", [{ id: sensorId, user_id: (user as any).id }]);
        const sensor = sensorResult.result;
        if (sensor?.gateway_identifier) {
          if (mockFleetService.isMockIdentifier(sensor.gateway_identifier)) {
            // Gateway de la flota simulada: nada de MQTT real (no tiene
            // sentido publicar al broker compartido para un dispositivo que
            // no existe) — el "device" confirma solo, en unos segundos.
            mockFleetService.simulateCalibrationAck(result.result.request_id);
          } else {
            mqttService.publishCalibrationCommand(sensor.gateway_identifier, {
              requestId: result.result.request_id,
              slave: sensor.identifier,
              gain: (body as any).gain,
              intercept: (body as any).intercept,
            });
          }
        }

        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_sensors,
        body: t.Object({
          gain:      t.Number(),
          intercept: t.Number(),
          notes:     t.Optional(t.String()),
        }),
      },
    )

    .get(
      "/:id",
      async ({ params, user, set }) => {
        const result = await execProcedure("iot.get_sensor", [
          { id: parseInt(params.id), user_id: (user as any).id },
        ]);

        if (result.error) {
          set.status = 500;
          return { message: result.error };
        }

        if (!result.result) {
          set.status = 404;
          return { message: "Sensor no encontrado" };
        }

        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.view_sensors,
      },
    )

    .post(
      "/",
      async ({ body, set }) => {
        const result = await execProcedure("iot.save_sensor", [body]);

        if (result.error) {
          set.status = 400;
          return { message: result.error };
        }

        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_sensors,
        body: t.Object({
          gateway_id: t.Number(),
          name: t.String(),
          identifier: t.String(),
          type: t.Optional(t.String()),
          unit: t.Optional(t.String()),
          location: t.Optional(t.String()),
        }),
      },
    )

    .put(
      "/:id",
      async ({ params, body, set }) => {
        const result = await execProcedure("iot.save_sensor", [
          { id: parseInt(params.id), ...(body as any) },
        ]);

        if (result.error) {
          set.status = 400;
          return { message: result.error };
        }

        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_sensors,
        body: t.Object({
          name: t.Optional(t.String()),
          location: t.Optional(t.String()),
          enable: t.Optional(t.Boolean()),
        }),
      },
    )

    .get(
      "/:id/alias",
      async ({ params, user, set }) => {
        const result = await execProcedure("iot.get_sensor_alias", [
          { sensor_id: parseInt(params.id), user_id: (user as any).id },
        ]);
        if (result.error) { set.status = 500; return { message: result.error }; }
        return result.result ?? { alias: null };
      },
      { requirePermission: PERMISSIONS.iot.view_sensors },
    )

    .put(
      "/:id/alias",
      async ({ params, body, user, set }) => {
        const result = await execProcedure("iot.upsert_sensor_alias", [
          { sensor_id: parseInt(params.id), user_id: (user as any).id, alias: (body as any).alias },
        ]);
        if (result.error) { set.status = 400; return { message: result.error }; }
        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.rename_sensor_alias,
        body: t.Object({ alias: t.String({ minLength: 1, maxLength: 200 }) }),
      },
    )

    .post(
      "/gateways",
      async ({ body, set }) => {
        const result = await execProcedure("iot.save_gateway", [body]);

        if (result.error) {
          set.status = 400;
          return { message: result.error };
        }

        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_gateways,
        body: t.Object({
          name: t.String(),
          identifier: t.String(),
          location: t.Optional(t.String()),
        }),
      },
    )

    .put(
      "/gateways/pin",
      async ({ body, set }) => {
        const { gateway_ids, pin } = body as any;
        const result = await execProcedure("iot.set_gateway_pin", [{ gateway_ids, pin }]);
        if (result.error) { set.status = 400; return { message: result.error }; }
        // TODO: publicar pin al topic MQTT de cada gateway cuando se defina el topic
        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_gateways,
        body: t.Object({
          gateway_ids: t.Array(t.Number()),
          pin:         t.String({ pattern: "^\\d{4}$" }),
        }),
      },
    )

    .put(
      "/gateways/:id/wifi",
      async ({ params, body, set }) => {
        const result = await execProcedure("iot.update_gateway_wifi", [
          {
            id: parseInt(params.id),
            ssid: (body as any).ssid,
            password: (body as any).password ?? null,
            security: (body as any).security ?? "WPA2",
          },
        ]);
        if (result.error) {
          set.status = 400;
          return { message: result.error };
        }
        return {
          ok: true,
          gateway_id: parseInt(params.id),
          ssid: (body as any).ssid,
        };
      },
      {
        requirePermission: PERMISSIONS.iot.manage_gateways,
        body: t.Object({
          ssid: t.String(),
          password: t.Optional(t.String()),
          security: t.Optional(
            t.Union([t.Literal("WPA2"), t.Literal("WPA3"), t.Literal("open")]),
          ),
        }),
      },
    )

    .put(
      "/gateways/:id/mqtt-topic",
      async ({ params, body, set }) => {
        const result = await execProcedure("iot.update_gateway_mqtt_topic", [
          { id: parseInt(params.id), topic: (body as any).topic },
        ]);
        if (result.error) { set.status = 400; return { message: result.error }; }

        const { request_id, identifier } = result.result as { request_id: string; identifier: string };
        if (identifier && mockFleetService.isMockIdentifier(identifier)) {
          mockFleetService.simulateMqttTopicAck(request_id);
        }
        // Gateway real: sin canal de aprovisionamiento todavía (igual que
        // WiFi/PIN) — queda 'pending' hasta que exista un mecanismo real.

        return result.result;
      },
      {
        requirePermission: PERMISSIONS.iot.manage_gateways,
        body: t.Object({
          topic: t.String({ minLength: 1, maxLength: 200 }),
        }),
      },
    ),
);
