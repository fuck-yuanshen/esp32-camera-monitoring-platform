import { BabyAlert, DeviceRecord, FrameMeta, JsonMessage } from "./types";

const DEFAULT_DEVICE_ID = "esp32p4-vehicle";

export class DeviceStateStore {
  private readonly devices = new Map<string, DeviceRecord>();

  markOnline(deviceId = DEFAULT_DEVICE_ID): DeviceRecord {
    const record = this.getOrCreate(deviceId);
    record.online = true;
    record.connectedAtMs = Date.now();
    record.disconnectedAtMs = undefined;
    return record;
  }

  markOffline(deviceId = DEFAULT_DEVICE_ID): DeviceRecord {
    const record = this.getOrCreate(deviceId);
    record.online = false;
    record.disconnectedAtMs = Date.now();
    return record;
  }

  updateJson(message: JsonMessage): DeviceRecord {
    const deviceId = message.device_id ?? DEFAULT_DEVICE_ID;
    const record = this.getOrCreate(deviceId);
    const enriched = { ...message, device_id: deviceId, server_ts_ms: Date.now() };
    record.lastMessageAtMs = enriched.server_ts_ms as number;

    if (message.type === "hello") record.hello = enriched;
    if (message.type === "motion") record.motion = enriched;
    if (message.type === "temperature") record.temperature = enriched;
    if (message.type === "co2") record.co2 = enriched;

    return record;
  }

  updateFrame(meta: FrameMeta, bytes: Buffer): DeviceRecord {
    const deviceId = meta.device_id ?? DEFAULT_DEVICE_ID;
    const record = this.getOrCreate(deviceId);
    const serverTs = Date.now();
    record.latestFrameMeta = { ...meta, device_id: deviceId, server_ts_ms: serverTs };
    record.latestFrameBytes = bytes;
    record.lastMessageAtMs = serverTs;
    return record;
  }

  updateBabyAlert(alert: BabyAlert): DeviceRecord {
    const record = this.getOrCreate(alert.device_id);
    record.babyAlert = alert;
    record.lastMessageAtMs = alert.server_ts_ms;
    return record;
  }

  get(deviceId = DEFAULT_DEVICE_ID): DeviceRecord | undefined {
    return this.devices.get(deviceId);
  }

  snapshot() {
    return {
      server_ts_ms: Date.now(),
      devices: Array.from(this.devices.values()).map((record) => ({
        ...record,
        latestFrameBytes: record.latestFrameBytes
          ? { byteLength: record.latestFrameBytes.byteLength }
          : undefined
      }))
    };
  }

  private getOrCreate(deviceId: string): DeviceRecord {
    let record = this.devices.get(deviceId);
    if (!record) {
      record = { deviceId, online: false };
      this.devices.set(deviceId, record);
    }
    return record;
  }
}
