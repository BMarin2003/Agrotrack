import { mqttService } from "@services/mqtt.service";
import { watchdogService } from "@services/watchdog.service";
import { gatewayMockService } from "@services/gateway.service";
import { configServer } from "./config";

export const bootServer = async () => {
  if (configServer.mqtt.enabled) {
    mqttService.connect();
  } else {
    gatewayMockService.start();
  }
  watchdogService.start();
};
