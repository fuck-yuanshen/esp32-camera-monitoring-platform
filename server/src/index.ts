import http from "http";
import { WebSocketServer } from "ws";
import { AppBroadcaster } from "./broadcaster";
import { config } from "./config";
import { DeviceStateStore } from "./deviceState";
import { logger } from "./logger";
import { handleAppConnection } from "./wsAppHandler";
import { handleDeviceConnection } from "./wsDeviceHandler";

const stateStore = new DeviceStateStore();
const broadcaster = new AppBroadcaster();

const server = http.createServer((req, res) => {
  if (req.url === "/health") {
    sendJson(res, 200, { ok: true, server_ts_ms: Date.now() });
    return;
  }
  if (req.url === "/state") {
    sendJson(res, 200, stateStore.snapshot());
    return;
  }
  sendJson(res, 404, { error: "not_found" });
});

const wsServer = new WebSocketServer({ noServer: true });

server.on("upgrade", (req, socket, head) => {
  const path = new URL(req.url ?? "/", `http://${req.headers.host}`).pathname;
  if (path !== "/device" && path !== "/app") {
    socket.destroy();
    return;
  }
  wsServer.handleUpgrade(req, socket, head, (ws) => {
    if (path === "/device") handleDeviceConnection(ws, broadcaster, stateStore);
    if (path === "/app") handleAppConnection(ws, broadcaster, stateStore);
  });
});

server.listen(config.port, config.host, () => {
  logger.info("Server started", { host: config.host, port: config.port });
});

function sendJson(res: http.ServerResponse, status: number, body: unknown) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(payload)
  });
  res.end(payload);
}
