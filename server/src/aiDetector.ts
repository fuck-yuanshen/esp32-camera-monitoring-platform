import https from "https";
import { config } from "./config";
import { logger } from "./logger";
import { BabyAlert, DeviceRecord, FrameMeta } from "./types";

type DetectionInput = {
  deviceId: string;
  frameMeta: FrameMeta;
  frameBytes: Buffer;
  record: DeviceRecord;
};

let running = false;
let lastRunAtMs = 0;

export function shouldRunBabyDetection(record: DeviceRecord): boolean {
  const moving = record.motion?.moving;
  if (moving !== false) return false;
  if (!record.latestFrameBytes || !record.latestFrameMeta) return false;
  if (!config.ai.enabled) return false;
  return Date.now() - lastRunAtMs >= config.ai.minIntervalMs;
}

export async function detectBaby(input: DetectionInput): Promise<BabyAlert | undefined> {
  if (running) return undefined;
  running = true;
  lastRunAtMs = Date.now();

  try {
    const hasBaby = await callVisionModel(input.frameBytes);
    const temperatureC = readTemperature(input.record);
    const co2Ppm = readCo2(input.record);
    const temperatureTriggered =
      typeof temperatureC === "number" && temperatureC >= config.ai.temperatureThresholdC;
    const co2Triggered = typeof co2Ppm === "number" && co2Ppm >= config.ai.co2ThresholdPpm;
    const level = calculateLevel(hasBaby, temperatureTriggered, co2Triggered);

    const alert: BabyAlert = {
      type: "baby_alert",
      device_id: input.deviceId,
      has_baby: hasBaby,
      level,
      temperature_c: temperatureC,
      co2_ppm: co2Ppm,
      temperature_triggered: temperatureTriggered,
      co2_triggered: co2Triggered,
      temperature_threshold_c: config.ai.temperatureThresholdC,
      co2_threshold_ppm: config.ai.co2ThresholdPpm,
      frame_id: input.frameMeta.frame_id,
      server_ts_ms: Date.now()
    };

    logger.info("Baby detection completed", {
      deviceId: input.deviceId,
      frameId: input.frameMeta.frame_id,
      hasBaby,
      level
    });
    return alert;
  } catch (error) {
    logger.warn("Baby detection failed", { error: String(error) });
    return undefined;
  } finally {
    running = false;
  }
}

function calculateLevel(hasBaby: boolean, temperatureTriggered: boolean, co2Triggered: boolean): number {
  if (!hasBaby) return 0;
  if (temperatureTriggered && co2Triggered) return 3;
  if (temperatureTriggered || co2Triggered) return 2;
  return 1;
}

function readTemperature(record: DeviceRecord): number | undefined {
  const ds18b20 = record.temperature?.celsius;
  if (typeof ds18b20 === "number") return ds18b20;
  const aht = record.co2?.temperature_c;
  return typeof aht === "number" ? aht : undefined;
}

function readCo2(record: DeviceRecord): number | undefined {
  const value = record.co2?.eco2_ppm;
  return typeof value === "number" ? value : undefined;
}

function callVisionModel(frameBytes: Buffer): Promise<boolean> {
  if (!config.ai.apiKey) {
    throw new Error("DASHSCOPE_API_KEY is not configured");
  }

  const payload = JSON.stringify({
    model: config.ai.model,
    messages: [
      {
        role: "user",
        content: [
          {
            type: "image_url",
            image_url: {
              url: `data:image/jpeg;base64,${frameBytes.toString("base64")}`
            }
          },
          {
            type: "text",
            text: "图片里是否有小孩或者婴儿？只需要回答 true 或 false。"
          }
        ]
      }
    ]
  });

  return new Promise((resolve, reject) => {
    const request = https.request(
      `${config.ai.baseUrl}/chat/completions`,
      {
        method: "POST",
        headers: {
          authorization: `Bearer ${config.ai.apiKey}`,
          "content-type": "application/json",
          "content-length": Buffer.byteLength(payload)
        },
        timeout: config.ai.timeoutMs
      },
      (response) => {
        let body = "";
        response.setEncoding("utf8");
        response.on("data", (chunk) => {
          body += chunk;
        });
        response.on("end", () => {
          if (!response.statusCode || response.statusCode < 200 || response.statusCode >= 300) {
            reject(new Error(`AI API returned ${response.statusCode}: ${body.slice(0, 300)}`));
            return;
          }
          resolve(parseBooleanAnswer(body));
        });
      }
    );

    request.on("timeout", () => {
      request.destroy(new Error("AI API timeout"));
    });
    request.on("error", reject);
    request.write(payload);
    request.end();
  });
}

function parseBooleanAnswer(body: string): boolean {
  const parsed = JSON.parse(body);
  const content = parsed?.choices?.[0]?.message?.content;
  const text = Array.isArray(content)
    ? content.map((part) => part?.text ?? "").join(" ")
    : String(content ?? "");
  const normalized = text.trim().toLowerCase();
  if (normalized.includes("true")) return true;
  if (normalized.includes("false")) return false;
  throw new Error(`AI answer is not boolean: ${text}`);
}
