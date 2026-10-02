import WebSocket from "ws";
import { detectBaby, shouldRunBabyDetection } from "./aiDetector";
import { AppBroadcaster } from "./broadcaster";
import { config } from "./config";
import { DeviceStateStore } from "./deviceState";
import { logger } from "./logger";
import { FrameMeta, JsonMessage } from "./types";

export function handleDeviceConnection(
  ws: WebSocket,
  broadcaster: AppBroadcaster,
  stateStore: DeviceStateStore
) {
  let deviceId = "esp32p4-vehicle";
  let pendingFrameMeta: FrameMeta | undefined;
  let frameCount = 0;

  stateStore.markOnline(deviceId);
  broadcaster.broadcastDeviceStatus(deviceId, true);
  logger.info("Device connected", { deviceId });

  ws.on("message", (data, isBinary) => {
    if (isBinary) {
      const bytes = Buffer.isBuffer(data) ? data : Buffer.from(data as ArrayBuffer);
      if (!pendingFrameMeta) {
        logger.warn("Dropped binary frame without frame_meta", { byteLength: bytes.byteLength });
        return;
      }
      const meta = pendingFrameMeta;
      pendingFrameMeta = undefined;
      const abnormal = typeof meta.size === "number" && meta.size !== bytes.byteLength;
      if (abnormal) {
        logger.warn("JPEG byte length mismatch", {
          deviceId: meta.device_id,
          frameId: meta.frame_id,
          expected: meta.size,
          actual: bytes.byteLength
        });
      }
      const storedMeta = {
        ...meta,
        abnormal,
        server_ts_ms: Date.now()
      };
      const record = stateStore.updateFrame(storedMeta, bytes);
      broadcaster.broadcastFrame(storedMeta, bytes);
      if (shouldRunBabyDetection(record)) {
        void detectBaby({
          deviceId: meta.device_id,
          frameMeta: storedMeta,
          frameBytes: bytes,
          record
        }).then((alert) => {
          if (!alert) return;
          stateStore.updateBabyAlert(alert);
          broadcaster.broadcastJson(alert);
        });
      }
      frameCount += 1;
      if (frameCount % config.frameLogInterval === 0) {
        logger.info("Frame received", {
          deviceId: meta.device_id,
          frameId: meta.frame_id,
          width: meta.width,
          height: meta.height,
          byteLength: bytes.byteLength
        });
      }
      return;
    }

    const text = data.toString("utf8");
    let message: JsonMessage;
    try {
      message = JSON.parse(text);
    } catch (error) {
      logger.warn("JSON parse failed", { error: String(error), text: text.slice(0, 160) });
      return;
    }

    if (typeof message.type !== "string") {
      logger.warn("JSON message missing type", { message });
      return;
    }

    deviceId = message.device_id ?? deviceId;
    stateStore.markOnline(deviceId);

    if (message.type === "frame_meta") {
      if (pendingFrameMeta) {
        logger.warn("Dropped pending frame_meta because a new meta arrived", {
          deviceId,
          frameId: pendingFrameMeta.frame_id
        });
      }
      pendingFrameMeta = normalizeFrameMeta(message, deviceId);
      return;
    }

    const record = stateStore.updateJson({ ...message, device_id: deviceId });
    const enriched = pickStoredMessage(record, message.type);
    if (enriched) broadcaster.broadcastJson(enriched);

    if (message.type === "hello") {
      logger.info("Hello received", { deviceId, protocol: message.protocol, fw: message.fw });
      broadcaster.broadcastDeviceStatus(deviceId, true);
      return;
    }
    if (message.type === "motion" || message.type === "temperature" || message.type === "co2") {
      logger.info("Sensor updated", { deviceId, type: message.type });
      return;
    }
    logger.warn("Unknown message type", { deviceId, type: message.type });
  });

  ws.on("close", () => {
    stateStore.markOffline(deviceId);
    broadcaster.broadcastDeviceStatus(deviceId, false);
    logger.warn("Device disconnected", { deviceId });
  });
  ws.on("error", (error) => logger.warn("Device websocket error", { deviceId, error: error.message }));
}

function normalizeFrameMeta(message: JsonMessage, deviceId: string): FrameMeta {
  return {
    ...message,
    type: "frame_meta",
    device_id: deviceId,
    frame_id: Number(message.frame_id ?? 0),
    width: typeof message.width === "number" ? message.width : undefined,
    height: typeof message.height === "number" ? message.height : undefined,
    format: typeof message.format === "string" ? message.format : "jpeg",
    size: typeof message.size === "number" ? message.size : undefined,
    server_ts_ms: Date.now()
  };
}

function pickStoredMessage(record: ReturnType<DeviceStateStore["updateJson"]>, type: string) {
  if (type === "hello") return record.hello;
  if (type === "motion") return record.motion;
  if (type === "temperature") return record.temperature;
  if (type === "co2") return record.co2;
  return undefined;
}
