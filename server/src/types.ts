export type JsonMessage = {
  type: string;
  device_id?: string;
  ts_us?: number;
  [key: string]: unknown;
};

export type FrameMeta = JsonMessage & {
  type: "frame_meta";
  device_id: string;
  frame_id: number;
  width?: number;
  height?: number;
  format?: string;
  size?: number;
  server_ts_ms?: number;
  abnormal?: boolean;
};

export type DeviceRecord = {
  deviceId: string;
  online: boolean;
  connectedAtMs?: number;
  disconnectedAtMs?: number;
  lastMessageAtMs?: number;
  hello?: JsonMessage;
  motion?: JsonMessage;
  temperature?: JsonMessage;
  co2?: JsonMessage;
  latestFrameMeta?: FrameMeta;
  latestFrameBytes?: Buffer;
  babyAlert?: BabyAlert;
};

export type BabyAlert = JsonMessage & {
  type: "baby_alert";
  device_id: string;
  has_baby: boolean;
  level: number;
  temperature_c?: number;
  co2_ppm?: number;
  temperature_triggered: boolean;
  co2_triggered: boolean;
  temperature_threshold_c: number;
  co2_threshold_ppm: number;
  frame_id?: number;
  server_ts_ms: number;
};
