# 基于 ESP32 的车辆摄像头与传感器监控平台

本项目由 Android 客户端和 TypeScript WebSocket 服务端组成，用于接收 ESP32 摄像头、温度和气体传感器数据，并在移动端展示设备状态和告警信息。

## 目录

- `android/`：Android 监控客户端
- `server/`：Node.js + TypeScript WebSocket 服务端

## 配置

- 服务端通过 `HOST`、`PORT`、`DASHSCOPE_API_KEY` 等环境变量配置。
- Android 默认连接本机 `ws://127.0.0.1:8080/app`，部署到其他设备时请在本地配置服务器地址。
- 仓库不包含公网地址、APK、ZIP、构建目录或云端密钥。

## 构建

Android 使用 Gradle 构建；服务端使用 `npm install` 和 `npm run build`。请根据实际硬件协议和部署网络调整参数。
