const dashscopeApiKey = process.env.DASHSCOPE_API_KEY;

export const config = {
  host: process.env.HOST ?? "0.0.0.0",
  port: Number(process.env.PORT ?? 8080),
  staleAfterMs: Number(process.env.STALE_AFTER_MS ?? 10_000),
  frameLogInterval: Number(process.env.FRAME_LOG_INTERVAL ?? 30),
  ai: {
    enabled: process.env.AI_BABY_DETECTION_ENABLED !== "false" && Boolean(dashscopeApiKey),
    apiKey: dashscopeApiKey,
    baseUrl: process.env.DASHSCOPE_BASE_URL ?? "https://dashscope.aliyuncs.com/compatible-mode/v1",
    model: process.env.DASHSCOPE_MODEL ?? "qwen-vl-plus",
    minIntervalMs: Number(process.env.AI_DETECTION_INTERVAL_MS ?? 5_000),
    timeoutMs: Number(process.env.AI_DETECTION_TIMEOUT_MS ?? 20_000),
    temperatureThresholdC: Number(process.env.ALERT_TEMPERATURE_C ?? 35),
    co2ThresholdPpm: Number(process.env.ALERT_CO2_PPM ?? 1000)
  }
};
