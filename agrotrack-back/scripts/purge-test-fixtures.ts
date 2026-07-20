import "dotenv/config";
import "../src/config";
import { supabaseAdmin } from "../src/core/supabase";

// Limpieza manual de los fixtures sintéticos que deja la suite de caja negra
// (tests/gateways.blackbox.test.ts, tests/thresholds.blackbox.test.ts,
// tests/helpdesk.blackbox.test.ts) en la base de datos Supabase compartida.
// No hay endpoint DELETE /sensors/gateways/:id en la API, así que este script
// borra directamente contra el schema `iot`, igual que cleanup-mock-gateways.ts.
// No se ejecuta automáticamente — es una herramienta de mantenimiento manual.

const iot = () => supabaseAdmin.schema("iot") as any;

async function purge() {
  // ─── 1. Gateways sintéticos: test-cn-* (gateways.blackbox) y test-thresholds-* (thresholds.blackbox) ───
  const { data: gateways, error: gErr } = await iot()
    .from("gateways")
    .select("id")
    .or("identifier.like.test-cn-%,identifier.like.test-thresholds-%");
  if (gErr) throw new Error(gErr.message);

  const gatewayIds = (gateways ?? []).map((g: any) => g.id);
  console.log(`Gateways sintéticos (test-cn-*, test-thresholds-*) encontrados: ${gatewayIds.length}`);

  let sensorsDeleted = 0;
  let gatewaysDeleted = 0;

  if (gatewayIds.length > 0) {
    const { data: sensors } = await iot().from("sensors").select("id").in("gateway_id", gatewayIds);
    const sensorIds = (sensors ?? []).map((s: any) => s.id);
    console.log(`Sensores sintéticos asociados: ${sensorIds.length}`);

    if (sensorIds.length > 0) {
      await iot().from("sensor_readings").delete().in("sensor_id", sensorIds);
      await iot().from("sensor_calibrations").delete().in("sensor_id", sensorIds);
      await iot().from("thresholds").delete().in("sensor_id", sensorIds);
      await iot().from("alerts").delete().in("sensor_id", sensorIds);
      await iot().from("sensor_aliases").delete().in("sensor_id", sensorIds);
      const { data: deletedSensors } = await iot().from("sensors").delete().in("id", sensorIds).select("id");
      sensorsDeleted = deletedSensors?.length ?? 0;
    }

    await iot().from("gateway_maintenance").delete().in("gateway_id", gatewayIds);
    await iot().from("helpdesk_tickets").delete().in("gateway_id", gatewayIds);
    const { data: deletedGateways } = await iot().from("gateways").delete().in("id", gatewayIds).select("id");
    gatewaysDeleted = deletedGateways?.length ?? 0;
  }

  // ─── 2. Tickets sintéticos (helpdesk.blackbox), no asociados a ningún gateway ───
  const { data: tickets, error: tErr } = await iot()
    .from("helpdesk_tickets")
    .delete()
    .or('subject.like.Ticket de prueba%,subject.like.Ticket para probar%')
    .select("id");
  if (tErr) throw new Error(tErr.message);
  const ticketsDeleted = tickets?.length ?? 0;

  console.log("");
  console.log("Limpieza de fixtures sintéticos de la suite de pruebas — resumen:");
  console.log(`  iot.gateways         : ${gatewaysDeleted}`);
  console.log(`  iot.sensors          : ${sensorsDeleted}`);
  console.log(`  iot.helpdesk_tickets : ${ticketsDeleted}`);
  console.log("Limpieza completa.");
  process.exit(0);
}

purge().catch(err => {
  console.error("Error en limpieza:", err.message ?? err);
  process.exit(1);
});
