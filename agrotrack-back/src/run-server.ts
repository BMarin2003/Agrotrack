import { mqttService } from "@services/mqtt.service";
import { watchdogService } from "@services/watchdog.service";
import { gatewayMockService } from "@services/gateway.service";
import { mockFleetService } from "@services/mock-fleet.service";
import { configServer } from "./config";

export const bootServer = async () => {
  if (configServer.mqtt.enabled) {
    mqttService.connect();
  } else {
    gatewayMockService.start();
  }
  // Independiente de MQTT_ENABLED: corre en paralelo para poblar la app con
  // datos realistas mientras no hay hardware Twarm transmitiendo de verdad.
  if (configServer.mockFleet.enabled) {
    mockFleetService.start();
  }
  watchdogService.start();
};
