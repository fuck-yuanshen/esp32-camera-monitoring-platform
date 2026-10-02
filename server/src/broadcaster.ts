import WebSocket from "ws";
import { DeviceRecord, FrameMeta, JsonMessage } from "./types";
import { logger } from "./logger";

export class AppBroadcaster {
  private readonly clients = new Set<WebSocket>();

  addClient(ws: WebSocket, state: DeviceRecord | undefined) {
    this.clients.add(ws);
    ws.on("close", () => {
      this.clients.delete(ws);
      logger.info("App disconnected", { appClients: this.clients.size });
    });
    ws.on("error", (error) => logger.warn("App websocket error", { error: error.message }));

    if (state) {
      this.sendDeviceStatus(ws, state.deviceId, state.online);
      for (const message of [state.hello, state.motion, state.temperature, state.co2]) {
        if (message) this.safeSendJson(ws, message);
      }
      if (state.babyAlert) this.safeSendJson(ws, state.babyAlert);
      if (state.latestFrameMeta && state.latestFrameBytes) {
        this.safeSendJson(ws, state.latestFrameMeta);
        this.safeSendBinary(ws, state.latestFrameBytes);
      }
    }
  }

  broadcastJson(message: JsonMessage) {
    for (const client of this.clients) this.safeSendJson(client, message);
  }

  broadcastDeviceStatus(deviceId: string, online: boolean) {
    this.broadcastJson({
      type: "device_status",
      device_id: deviceId,
      online,
      server_ts_ms: Date.now()
    });
  }

  broadcastFrame(meta: FrameMeta, bytes: Buffer) {
    for (const client of this.clients) {
      this.safeSendJson(client, meta);
      this.safeSendBinary(client, bytes);
    }
  }

  private sendDeviceStatus(ws: WebSocket, deviceId: string, online: boolean) {
    this.safeSendJson(ws, {
      type: "device_status",
      device_id: deviceId,
      online,
      server_ts_ms: Date.now()
    });
  }

  private safeSendJson(ws: WebSocket, message: unknown) {
    if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(message));
  }

  private safeSendBinary(ws: WebSocket, bytes: Buffer) {
    if (ws.readyState === WebSocket.OPEN) ws.send(bytes, { binary: true });
  }
}
