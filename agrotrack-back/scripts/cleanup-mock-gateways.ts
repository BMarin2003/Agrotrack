import "dotenv/config";
import "../src/config";
import { supabaseAdmin } from "../src/core/supabase";

const iot = () => supabaseAdmin.schema("iot") as any;

async function cleanup() {
  const { data: gateways, error: gErr } = await iot()
    .from("gateways")
    .select("id")
    .like("identifier", "MOCK-GW-%");
  if (gErr) throw new Error(gErr.message);

  const gatewayIds = (gateways ?? []).map((g: any) => g.id);
  console.log(`Gateways MOCK-GW-* encontrados: ${gatewayIds.length}`);
  if (gatewayIds.length === 0) {
    console.log("Nada que limpiar.");
    process.exit(0);
  }

  const { data: sensors } = await iot().from("sensors").select("id").in("gateway_id", gatewayIds);
  const sensorIds = (sensors ?? []).map((s: any) => s.id);
  console.log(`Sensores asociados: ${sensorIds.length}`);

  if (sensorIds.length > 0) {
    await iot().from("sensor_readings").delete().in("sensor_id", sensorIds);
    await iot().from("sensor_calibrations").delete().in("sensor_id", sensorIds);
    await iot().from("thresholds").delete().in("sensor_id", sensorIds);
    await iot().from("alerts").delete().in("sensor_id", sensorIds);
    await iot().from("sensor_aliases").delete().in("sensor_id", sensorIds);
    await iot().from("sensors").delete().in("id", sensorIds);
  }

  await iot().from("gateway_maintenance").delete().in("gateway_id", gatewayIds);
  await iot().from("helpdesk_tickets").delete().in("gateway_id", gatewayIds);
  await iot().from("gateways").delete().in("id", gatewayIds);

  console.log("Limpieza completa — gateways/sensores/lecturas MOCK-GW-* eliminados.");
  process.exit(0);
}

cleanup().catch(err => {
  console.error("Error en limpieza:", err.message ?? err);
  process.exit(1);
});
