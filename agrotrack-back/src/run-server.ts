import { mqttService } from "@services/mqtt.service";
import { watchdogService } from "@services/watchdog.service";
import { configServer } from "src/config";

export const bootServer = async () => {
  if (configServer.isProduction) {
    mqttService.connect();
    watchdogService.start();
  }
};
