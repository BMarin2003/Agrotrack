import "dotenv/config";
import "../src/config";
import { execProcedure } from "../src/core/db/connection";

const gws = await execProcedure("iot.list_gateways", [{}]);
if (gws.error) { console.error("list_gateways error:", gws.error); process.exit(1); }
const gateways = gws.result ?? [];
console.log("gateways found:", gateways.length);
if (gateways.length === 0) { console.log("no gateways to test against"); process.exit(0); }

const id = gateways[0].id;
const nowIso = new Date().toISOString();
const dayAgoIso = new Date(Date.now() - 86400000).toISOString();

const gwReport = await execProcedure("iot.get_gateway_report", [{ gateway_id: id, from_ts: dayAgoIso, to_ts: nowIso, user_id: null }]);
console.log("get_gateway_report error:", gwReport.error ?? "none");
if (!gwReport.error) console.log("get_gateway_report keys:", Object.keys(gwReport.result ?? {}));

const genReport = await execProcedure("iot.get_general_report", [{ from_ts: dayAgoIso, to_ts: nowIso }]);
console.log("get_general_report error:", genReport.error ?? "none");
if (!genReport.error) console.log("get_general_report keys:", Object.keys(genReport.result ?? {}));
