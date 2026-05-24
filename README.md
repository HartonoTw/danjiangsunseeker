# 淡江夕照通 — Danjiang SunSeeker

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin%202.1.0-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![DI](https://img.shields.io/badge/DI-Hilt-FF6F00)
![License](https://img.shields.io/badge/License-MIT-blue)

> **精準預測淡江大橋「夕陽穿塔」黃金拍攝點與時刻的 Android APP**

淡江大橋是世界最大單塔不對稱斜張橋，主塔高 211m、塔頂海拔 200m。每年只有特定幾天、每次只有幾分鐘，太陽會從主塔「穿」過去。淡江夕照通把天文計算、地理幾何、鏡頭物理整合為一，幫攝影師找到正確地點、正確時間，按下快門。

---

## 功能特色

- 🎯 **熱點評分** — 5 個觀景點，每日對齊角差即時評分 0-100，按分數排序一目了然
- 🗺️ **互動地圖** — 任選日期顯示黃金拍攝帶射線；點地圖任何位置即時查詢與主塔的角差
- 📷 **焦段模擬器** — 14-800mm 焦距 Slider + 時間 Slider；支援塔頂（200m）/ 塔基（5m）兩種對焦高度；即時渲染大橋、斜張索、太陽軌跡
- 📅 **365 天黃金日曆** — 掃描整年最佳拍攝日，±0.5°/±1°/±2°/±5° 四檔容差可調；日曆列點擊一鍵跳到模擬器；加入手機行事曆
- 🔭 **AR 實境勘景** — 無金鑰，GPS + 磁力計 + CameraX 疊加虛擬主塔與太陽軌跡；對準太陽校正後方位誤差 < 0.5°

---

## 螢幕截圖

> 詳細畫面書請見 `SCREENBOOK.pdf`（含各功能頁面截圖）。

```
熱點清單        互動地圖         焦段模擬器       黃金日曆
  ┌──────┐      ┌──────┐         ┌──────┐        ┌──────┐
  │ 83   │      │ 🗺️   │         │ ☀️🏗️ │        │ 5/14 │
  │ 星巴克│      │      │         │      │        │ ───  │
  │      │      │ ──── │         │ ════ │        │ 6/21 │
  └──────┘      └──────┘         └──────┘        └──────┘
```

---

## 快速開始

### Prerequisites

| 工具 | 版本需求 |
|---|---|
| Android Studio | Meerkat 2024.3.x 以上 |
| Android SDK | API 35 (Android 15) |
| JDK | 17 |
| Gradle | 8.11.1（Wrapper 已鎖定，無需手動安裝） |

> **無需任何 API 金鑰**即可完整執行核心功能（地圖、天文計算、AR、日曆）。

### Clone & Build

```powershell
# Clone
git clone https://github.com/<your-account>/danjiangsunseeker.git
cd danjiangsunseeker

# 僅編譯（快速驗證）
.\gradlew.bat :app:compileDebugKotlin

# 執行單元測試
.\gradlew.bat :app:testDebugUnitTest

# 建置 Debug APK
.\gradlew.bat :app:assembleDebug
# APK 輸出於 app/build/outputs/apk/debug/app-debug.apk
```

### 安裝到裝置

```powershell
.\gradlew.bat :app:installDebug
```

---

## 技術架構

```
presentation  ──▶  domain  ──▶  data
```

| 層 | 說明 |
|---|---|
| **presentation** | Jetpack Compose + Material 3 + Hilt ViewModel；5 個主頁 + HotspotDetail + 共用 TowerTargetSelector |
| **domain** | 純 Kotlin；BridgeTower 常數、Geodesy/AtmosphericRefraction 物理計算、6 個 Use Case |
| **data** | SunCalcDataSource（commons-suncalc）、DataStore（CustomHotspot + TowerTarget）、WorkManager 推播 |

### 主要技術棧

| 技術 | 版本 | 用途 |
|---|---|---|
| Kotlin | 2.1.0 | 主要語言 |
| Jetpack Compose | BOM 2024.12 | UI |
| Material 3 | — | 設計系統 |
| Hilt | 2.56.2 | 依賴注入 |
| MapLibre Android | 11.5.2 | 無金鑰地圖（OpenFreeMap 底圖） |
| commons-suncalc | 3.11 | NOAA SPA 太陽位置演算法 |
| CameraX | 1.4.1 | AR 相機預覽 + FOV 讀取 |
| DataStore Preferences | 1.1.1 | 設定與熱點持久化 |
| WorkManager | 2.10.0 | 每日 3AM 推播掃描 |

---

## 主塔參數

本 APP 所有幾何運算的基準點：

| 參數 | 數值 |
|---|---|
| 座標 (WGS-84) | 25.17531°N, 121.41778°E |
| 塔高（水下基礎到塔頂） | 211 m |
| 塔頂海拔 | 200 m |
| 橋面（甲板）海拔 | 20 m |
| A 字兩柱「合體點」海拔 | 72 m |
| 主跨（朝八里） | 450 m |
| 側跨（朝淡水） | 175 m |
| 塔朝八里方位角 | 241° |

> 座標來源：公路總局公告 + 維基百科估算。通車後建議以實測 GPS 校正。

---

## 參考資料

- [淡江大橋 — 維基百科](https://zh.wikipedia.org/wiki/淡江大橋)
- [NOAA Solar Position Algorithm (SPA)](https://midcdmz.nrel.gov/spa/)
- [commons-suncalc — shredzone.org](https://shredzone.org/maven/commons-suncalc/)
- [MapLibre Native Android](https://maplibre.org/maplibre-native/android/api/)

---

## License

```
MIT License

Copyright (c) 2025 Danjiang SunSeeker Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```
