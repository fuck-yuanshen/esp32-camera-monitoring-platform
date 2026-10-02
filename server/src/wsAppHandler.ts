import WebSocket from "ws";
import { AppBroadcaster } from "./broadcaster";
import { DeviceStateStore } from "./deviceState";
import { logger } from "./logger";

export function handleAppConnection(
  ws: WebSocket,
  broadcaster: AppBroadcaster,
  stateStore: DeviceStateStore
) {
  logger.info("App connected");
  broadcaster.addClient(ws, stateStore.get());
}
