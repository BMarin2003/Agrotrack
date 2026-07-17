import "dotenv/config";
import "../src/config";
import { execProcedure } from "../src/core/db/connection";

const gws = await execProcedure("iot.list_gateways", [{}]);
if (gws.error) { console.error("list_gateways:", gws.error); process.exit(1); }
const mock = gws.result.find((g: any) => g.name === "Cámara Fría 1 - Packing");
console.log("Gateway mock (mqtt fields):", mock);

if (mock) {
  console.log("\n=== Test tópico MQTT ===");
  const upd = await execProcedure("iot.update_gateway_mqtt_topic", [{ id: mock.id, topic: "twarm/prueba-test" }]);
  console.log("update_gateway_mqtt_topic:", upd);
}

const sensors = await execProcedure("iot.get_latest_readings_by_gateway", [{ gateway_id: mock?.id }]);
console.log("\n=== Últimas lecturas (voltage) ===");
for (const s of (sensors.result ?? []).slice(0, 3)) {
  console.log(`${s.sensor_name}: T=${s.temperature} voltage=${s.voltage} battery=${s.battery}`);
}

process.exit(0);
