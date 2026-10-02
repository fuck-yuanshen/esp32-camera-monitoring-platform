# 车辆摄像头与传感器监控系统当前完成情况

本文档用于给 ESP32-P4 下位机开发 AI 了解当前服务器后端和 Android App 的实现状态。下位机固件上传协议不需要改变，当前服务器按既定协议兼容接收。

## 项目目录

- Android 原生 App：`E:\esp32\MyApplication\app`
- Node.js TypeScript 后端：`E:\esp32\MyApplication\server`
- Debug APK：`E:\esp32\MyApplication\app\build\outputs\apk\debug\app-debug.apk`

说明：Android 工程根目录原本已有 `app` 模块，因此后端放在 `server` 目录中，避免破坏 Gradle 工程结构。

## 服务器后端状态

后端使用 Node.js + TypeScript + `ws` 实现，监听 `0.0.0.0:8080`。

已实现接口：

- 设备 WebSocket 上传入口：`ws://0.0.0.0:8080/device`
- Android App WebSocket 订阅入口：`ws://0.0.0.0:8080/app`
- 健康检查：`GET http://服务器IP:8080/health`
- 当前缓存状态：`GET http://服务器IP:8080/state`

已实现模块：

- `src/index.ts`：HTTP/WebSocket 入口
- `src/wsDeviceHandler.ts`：ESP32-P4 设备端 WebSocket 消息处理
- `src/wsAppHandler.ts`：App WebSocket 连接处理
- `src/deviceState.ts`：设备状态缓存
- `src/broadcaster.ts`：向所有 App 广播状态、传感器数据和摄像头帧
- `src/aiDetector.ts`：停车后调用 AI 视觉模型检测车内是否有小孩或婴儿
- `src/config.ts`：端口、阈值、AI 配置
- `src/logger.ts`：日志输出

## 下位机需要连接的服务器地址

云服务器后端已部署，公网地址为：

```text
ws://服务器地址:8080/device
```

如果 ESP32-P4 固件已支持 STA/APSTA 并能访问公网，应作为 WebSocket client 连接：

```text
ws://服务器地址:8080/device
```

如果 ESP32-P4 仍然只运行 SoftAP，默认不能直接访问公网服务器。这种情况下仍可使用局域网/热点内服务器地址：

```text
ws://192.168.4.2:8080/device
```

如果服务器运行在电脑上，需要确保电脑连接 `P4-CAMERA` 热点，并确认电脑在该热点中的 IP。如果电脑不是 `192.168.4.2`，需要把固件里的服务器地址改成电脑实际 IP：

```text
ws://电脑热点网段IP:8080/device
```

Android App 当前默认连接云服务器：

```text
ws://服务器地址:8080/app
```

如需连接热点内本地服务器，可在 App 的“设置”里改成：

```text
ws://192.168.4.2:8080/app
```

App 界面里有“设置”按钮，可以手动修改服务器 URL。

## 设备上传协议

服务器兼容以下设备消息，不要求改固件协议。

### hello

设备连接成功后发送一次：

```json
{
  "type": "hello",
  "device_id": "esp32p4-vehicle",
  "protocol": 1,
  "fw": "vehicle_ws_uplink",
  "ts_us": 123456789
}
```

### motion

车辆运动状态：

```json
{
  "type": "motion",
  "device_id": "esp32p4-vehicle",
  "ts_us": 123456789,
  "moving": true,
  "accel_delta": 1234,
  "gyro_sum": 456,
  "ax": 100,
  "ay": 200,
  "az": 16000,
  "gx": 3,
  "gy": 4,
  "gz": 5
}
```

关键字段：

- `moving=true`：车辆运动或震动
- `moving=false`：车辆静止

AI 婴儿检测只会在服务器收到 `moving=false` 且有摄像头帧时触发。

### temperature

DS18B20 温度：

```json
{
  "type": "temperature",
  "device_id": "esp32p4-vehicle",
  "ts_us": 123456789,
  "celsius": 25.38
}
```

### co2

ENS160/AHTx0 空气质量数据：

```json
{
  "type": "co2",
  "device_id": "esp32p4-vehicle",
  "ts_us": 123456789,
  "eco2_ppm": 520,
  "tvoc_ppb": 18,
  "aqi": 1,
  "ens160_status": 0,
  "aht_valid": true,
  "temperature_c": 25.10,
  "humidity_rh": 48.50
}
```

### 摄像头帧

摄像头帧必须继续使用“文本 JSON 元数据 + 二进制 JPEG”的顺序。

先发 `frame_meta`：

```json
{
  "type": "frame_meta",
  "device_id": "esp32p4-vehicle",
  "ts_us": 123456789,
  "frame_id": 123,
  "width": 1920,
  "height": 1080,
  "format": "jpeg",
  "size": 45678
}
```

紧接着发一条 WebSocket 二进制消息，内容为原始 JPEG bytes。

注意：

- 不要把 JPEG 转 base64 发给服务器。
- `frame_id` 可以跳号，服务器和 App 不要求连续。
- 服务器收到二进制帧时会检查 JPEG bytes 长度是否等于 `frame_meta.size`，不一致会记录日志，但仍尝试转发和显示。
- 如果服务器收到二进制帧时没有等待中的 `frame_meta`，会丢弃该二进制帧。

## Android App 当前状态

App 是原生 Android Java 实现。

当前界面能力：

- 中文界面。
- 顶部显示服务器连接状态。
- “设置”按钮：展开后可以修改 WebSocket URL 并重新连接。
- “隐藏摄像头/显示摄像头”按钮：可以隐藏或显示摄像头画面区域。
- 摄像头 JPEG bytes 直接内存解码显示，不使用 base64，不写入磁盘。
- 显示设备在线/离线。
- 显示车辆状态：运动中/静止。
- 显示 DS18B20 温度。
- 显示 eCO2、TVOC、AQI、湿度。
- 显示更新时间。
- 5 秒无新视频帧时显示“视频未更新”。
- 10 秒无新传感器状态时显示“可能过期”。
- 显示 AI 小孩/婴儿检测告警，并触发手机本地 Toast、提示音和震动。

## AI 小孩/婴儿检测

后端已添加 DashScope 百炼兼容 OpenAI API 调用能力。

模型默认：

```text
qwen-vl-plus
```

API 地址默认：

```text
https://dashscope.aliyuncs.com/compatible-mode/v1
```

提示词：

```text
图片里是否有小孩或者婴儿？只需要回答 true 或 false。
```

触发条件：

1. 服务器已收到 `motion` 消息。
2. `motion.moving` 必须是 `false`。
3. 服务器已收到最新摄像头 JPEG 帧。
4. 已配置 `DASHSCOPE_API_KEY`。
5. 距离上一次 AI 检测超过节流间隔，默认 5000 ms。

服务器不会在车辆运动中调用 AI 检测。

### AI 请求图片格式

设备到服务器仍然传原始 JPEG 二进制。

服务器内部调用 AI API 时，会把当前 JPEG bytes 转成 data URL：

```text
data:image/jpeg;base64,...
```

这是服务器到 AI 平台的请求格式，不影响 ESP32-P4 到服务器的协议。

## 告警等级

AI 检测结果由服务器下发给 App，消息类型为 `baby_alert`。

默认阈值：

- 温度阈值：`35 C`
- eCO2 阈值：`1000 ppm`

等级规则：

- `level=0`：未发现小孩或婴儿。
- `level=1`：发现小孩或婴儿。
- `level=2`：发现小孩或婴儿，并且温度或 CO2 任一超过阈值。
- `level=3`：发现小孩或婴儿，并且温度和 CO2 同时超过阈值。

服务器下发示例：

```json
{
  "type": "baby_alert",
  "device_id": "esp32p4-vehicle",
  "has_baby": true,
  "level": 2,
  "temperature_c": 36.5,
  "co2_ppm": 1200,
  "temperature_triggered": true,
  "co2_triggered": false,
  "temperature_threshold_c": 35,
  "co2_threshold_ppm": 1000,
  "frame_id": 123,
  "server_ts_ms": 1710000000000
}
```

Android App 收到后：

- 一级提醒：页面告警条 + Toast + 提示音 + 短震动。
- 二级提醒：更高强度提示音和震动。
- 三级提醒：最强提示音和震动。

## 后端运行方式

进入后端目录：

```powershell
cd E:\esp32\MyApplication\server
```

安装依赖：

```powershell
npm install
```

配置百炼 API Key：

```powershell
$env:DASHSCOPE_API_KEY="你的百炼API Key"
```

可选配置：

```powershell
$env:ALERT_TEMPERATURE_C="35"
$env:ALERT_CO2_PPM="1000"
$env:AI_DETECTION_INTERVAL_MS="5000"
$env:DASHSCOPE_MODEL="qwen-vl-plus"
```

构建：

```powershell
npm run build
```

启动：

```powershell
npm start
```

如果没有配置 `DASHSCOPE_API_KEY`，AI 检测默认不会启用，但设备数据转发、摄像头显示、传感器显示仍正常工作。

也可以显式关闭 AI 检测：

```powershell
$env:AI_BABY_DETECTION_ENABLED="false"
```

## Android 构建方式

进入项目根目录：

```powershell
cd E:\esp32\MyApplication
```

构建 Debug APK：

```powershell
.\gradlew.bat :app:assembleDebug
```

安装到手机：

```powershell
adb install -r E:\esp32\MyApplication\app\build\outputs\apk\debug\app-debug.apk
```

如果手机上之前安装了同包名模板 App，可以先卸载：

```powershell
adb uninstall com.example.myapplication
```

## 已验证情况

当前已验证：

- 后端 `npm run build` 通过。
- Android `.\gradlew.bat :app:assembleDebug` 通过。
- 本地模拟 `/device` 和 `/app` WebSocket，通过以下链路：
  - `motion`
  - `temperature`
  - `co2`
  - `frame_meta`
  - JPEG 二进制帧
- 未配置 API Key 时，AI 检测不会影响原有转发链路。
- Android APK 可安装到手机，中文界面、设置按钮、摄像头隐藏按钮可显示。

## 对下位机开发的重点要求

1. ESP32-P4 继续作为 WebSocket client 连接服务器 `/device`。
2. 不要改变 JSON 字段名。
3. 摄像头帧必须保持 `frame_meta` 文本 JSON 后紧跟 JPEG 二进制消息。
4. JPEG 不要 base64，不要拆成 JSON 字段。
5. `motion.moving=false` 是服务器触发 AI 婴儿检测的关键条件。
6. 传感器不存在或读取失败时，可以停止上报对应类型消息，服务器和 App 会显示无数据或保留最后状态。
7. 如果希望 AI 检测更及时，停车状态下应持续发送摄像头帧，服务器会按节流间隔选择部分帧进行检测。
