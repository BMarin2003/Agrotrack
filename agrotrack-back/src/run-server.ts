import { mqttService } from '@services/mqtt.service';
import { watchdogService } from '@services/watchdog.service';

export const bootServer = async () => {
  mqttService.connect();
  watchdogService.start();
};
