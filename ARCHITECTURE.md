# 淡江夕照通 — 設計與架構文件

> **Danjiang SunSeeker** — Android 原生 APP，預測淡江大橋日落最佳拍攝點與時刻。

---

## 1. 專案目標

淡江大橋為世界最大單塔不對稱斜張橋（主塔高 211m）。所謂「夕陽穿塔」、「大橋落日」等構圖機會每年僅數天、每天僅數分鐘出現，需要精準的天文 × 地理 × 氣象 × 鏡頭物理運算。

本 APP 提供：

| 功能 | 用途 |
|---|---|
| 熱點分數預測 | 12+ 個觀景點，每日依「對齊度 + 雲量 + 降雨」評分 0-100 |
| 互動地圖 + 黃金拍攝帶 | 任一日期 → 主塔朝日落方位的射線；點地圖任處查詢角差 |
| 焦段構圖模擬器 | 14-800mm 拖動 + 時間 slider，模擬主塔與太陽在畫面中的相對位置 |
| 365 天黃金日曆 | 整年「夕陽穿塔」可能日，一鍵加入手機行事曆 |
| AR 實境勘景 | 無金鑰：CameraX + GPS + 磁力計 + 加速度計疊加虛擬主塔/太陽軌跡 |
| AR 太陽校正 | 對準太陽自動修正磁力計偏差，誤差降至 < 0.5° |
| 自動推播 | 每日凌晨 3 點掃描未來 7 天，分數 ≥ 85 即發送通知 |

---

## 2. 技術棧

### 語言與框架

| 層 | 技術 |
|---|---|
| 語言 | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 |
| 架構 | MVVM + Clean Architecture (data / domain / presentation) |
| DI | Hilt 2.56.2 |
| 非同步 | Coroutines + Flow / StateFlow |

### 主要依賴

| 套件 | 版本 | 用途 |
|---|---|---|
| `org.shredzone.commons:commons-suncalc` | 3.11 | 太陽位置計算 (NOAA SPA 演算法) |
| `org.maplibre.gl:android-sdk` | 11.5.2 | 開源地圖渲染 |
| `androidx.camera:camera-*` | 1.4.1 | AR 相機預覽 + FOV 讀取 |
| `androidx.datastore:datastore-preferences` | 1.1.1 | 自訂熱點 / 評分結果持久化 |
| `com.google.android.gms:play-services-location` | 21.3.0 | FusedLocationProviderClient |
| `androidx.work:work-runtime-ktx` | 2.10.0 | 每日掃描 WorkManager |
| `androidx.room:room-*` | 2.6.1 | （已加入，目前未啟用，預留未來擴充）|
| `com.squareup.retrofit2` + `okhttp` | 2.11 / 4.12 | CWA OpenData HTTP 客戶端 |

### 外部 API / 服務

| 服務 | 金鑰 | 用途 | 失敗時行為 |
|---|---|---|---|
| **CWA OpenData (F-C0032-001)** | 必須 (免費) | 新北市天氣預報 → 雲量、降雨機率 | 無金鑰時 weather 分數略過，只用對齊度評分 |
| **OpenFreeMap** | 不需 | OSM 風格地圖底圖 | — |
| Google Cloud / ARCore Geospatial | **不使用** | — | 改用自建 GPS+磁力計方案 |

---

## 3. 模組架構

### Clean Architecture 三層

```
┌─────────────────────────────────────────────────────────┐
│  presentation/                                          │
│  ├─ hotspot/   (HotspotListScreen + ViewModel)         │
│  ├─ map/       (MapScreen + ComposeMapLibre + Layers)  │
│  ├─ simulator/ (FocalSimulator + 太陽軌跡視覺化)        │
│  ├─ ar/        (ARScreen + Camera + 感測器 + 校正)      │
│  ├─ calendar/  (GoldenCalendar + 行事曆 Intent)        │
│  ├─ theme/                                              │
│  └─ app/       (DanjiangApp Compose root + NavHost)    │
├─────────────────────────────────────────────────────────┤
│  domain/                                                │
│  ├─ model/     (BridgeTower, Hotspot, GeoPoint, ...)   │
│  ├─ physics/   (Geodesy, AtmosphericRefraction, ...)   │
│  └─ usecase/   (PredictHotspots, ComputeSunsetScore,   │
│                 ComputeGoldenLine, SimulateFocalLength,│
│                 ScanGoldenCalendar)                    │
├─────────────────────────────────────────────────────────┤
│  data/                                                  │
│  ├─ astro/     (SunCalcDataSource — commons-suncalc 包) │
│  ├─ sensors/   (DeviceOrientationProvider, Location)   │
│  ├─ hotspot/   (CustomHotspotStore — DataStore)        │
│  ├─ notifications/ (NotificationHelper, DailyScoreWorker) │
│  └─ di/        (NetworkModule — Hilt providers)        │
└─────────────────────────────────────────────────────────┘
```

### 依賴方向（嚴格）

```
presentation  ──▶  domain  ──▶  data
        └────────────────────────▲
                                 │ (data 也被 presentation 直接讀取，
                                    例 WeatherForecast model；可重構)
```

---

## 4. 核心 Domain 模型

### `BridgeTower` (主塔常數)

```kotlin
object BridgeTower {
    const val LATITUDE = 25.17531        // WGS-84
    const val LONGITUDE = 121.41778
    const val BASE_ELEVATION_M = 5.0     // 海平面以上
    const val TOWER_HEIGHT_M = 211.0     // 主塔高
    const val TOWER_TIP_ELEVATION_M = 216.0
    const val TOWER_WIDTH_M = 12.0       // 平均水平寬，用於算可接受對齊容差
}
```

### `GeoPoint` (WGS-84 座標)

```kotlin
data class GeoPoint(
    val latitude: Double,       // -90..90
    val longitude: Double,      // -180..180
    val elevationMeters: Double = 0.0,
)
```

### `Hotspot` (拍攝點)

```kotlin
data class Hotspot(
    val id: String,             // 預設用 "sand_dune"，自訂用 "custom_<timestamp>"
    val nameRes: Int?,          // 預設熱點用 R.string 資源；null 代表自訂
    val customName: String?,    // nameRes 為 null 時使用
    val position: GeoPoint,
    val description: String = "",
    val accessNote: String = "",
) {
    val isCustom: Boolean get() = nameRes == null
}
```

預設熱點 9 個（座標已驗證距主塔 ≤ 25km、主塔方位與夕陽方位 < 90° 可同框）：

| 熱點 | 主塔方位 | 對齊機會 |
|---|---|---|
| 大屯山助航站 | 270° | 春分 / 秋分 |
| 海關碼頭 | 274° | 4 月初 / 9 月中 |
| 嘉士洋行倉庫 | 279° | 4 月中 / 9 月初 |
| 淡水渡船頭 | 289° | 5 月底 / 7 月底 |
| 淡水河岸星巴克 | 295° | 夏至前後 |
| 八里渡船頭 | 307° | 夏至最近 (差 9°) |
| 八里左岸公園 | 311° | 夏至最近 (差 13°) |
| 沙崙海灘 | 157° | 永遠不穿塔 (廣角同框) |
| 石頭廣場 | 3° | 永遠不穿塔 (廣角同框) |

---

## 5. 物理與數學

### 5.1 太陽位置 (commons-suncalc 3.11 包裝)

```kotlin
val position = SunPosition.compute()
    .on(zonedDateTime)
    .at(observer.latitude, observer.longitude)
    .elevation(observer.elevationMeters)   // 高山觀察者必填
    .execute()
// position.azimuth (deg, 從正北順時針 0..360°)
// position.altitude (deg, 幾何仰角 — 不含大氣折射)
```

**注意**：v3.x 起 `altitude` 為**幾何**仰角；本 APP 在 `SunCalcDataSource.positionAt` 內手動套用 `AtmosphericRefraction.apparentFromTrue()` 取得視仰角。

### 5.2 大氣折射 (Bennett 公式)

太陽近地平線時，大氣折射讓「視位置」比「幾何位置」高約 **0.567° (= 34 arcmin)**。這是「夕陽穿塔」預測誤差最大來源。

```
R = cot( h + 7.31 / (h + 4.4) )  arc-min
```

其中 `h` = 真實仰角 (deg)。

逆向 (從視仰角扣折射) 用 Sæmundsson:
```
R = 1.02 / tan( h_apparent + 10.3 / (h_apparent + 5.11) )  arc-min
```

兩者並非嚴格互逆 (h=2° 約差 0.04°)。

### 5.3 測地計算 (Vincenty + Haversine)

**Vincenty 反算** — 兩點 → 距離 + 起終始方位：
- 使用 WGS-84 橢球參數（a = 6378137 m, f = 1/298.257)
- 對近距離 (< 50 km) 收斂於 < 1mm
- 對極點對峙退化情形使用 Haversine fallback

**`signedAzimuthDelta`** — 兩方位差，正規化到 (-180°, 180°]：
```kotlin
fun signedAzimuthDelta(a: Double, b: Double): Double {
    var d = a - b
    d = ((d + 180) % 360 + 360) % 360 - 180
    return d
}
```

### 5.4 視線遮蔽檢查 (LineOfSightChecker)

從觀察者到目標的大圓路徑等距採樣，每個採樣點檢查地形高程 vs 視線插值高度，考量：
- 觀察者眼高 1.65 m
- **地球折射等效半徑** 7,860,000 m（k=1.13 標準大氣折射）

預留 use case（目前未串 elevation API）。

### 5.5 鏡頭視角 / 主體佔比

```
FOV_h = 2 × atan(sensorWidth / (2 × focalLength))
塔在畫面寬度佔比 = (塔實際寬 / 距離) / tan(FOV_h / 2)
太陽角直徑 ≈ 0.53°
```

---

## 6. 評分系統 (ComputeSunsetScoreUseCase)

三維度組合 0-100 分：

| 維度 | 計算 | 權重 |
|---|---|---|
| **alignmentScore** | 0° offset → 100；±2° → 70；±10° → 30；±30°+ → 5 | 60% (對齊 ≥30 時) |
| **photogenicScore** | 雲量 20-50% (火燒雲區間) → 100；全晴/全陰扣分 | 20% |
| **rainSafeScore** | 100 − 降雨機率% | 20% |

**整合邏輯**：
- 對齊 < 30 → 整體分數主要由對齊決定 (alignment × 85% + weather × 15%)，再好的天氣救不了不對齊
- 對齊 ≥ 30 → alignment × 60% + weather × 40%
- 無 CWA 金鑰 → 僅用 alignment

### Wx 代碼 → 雲量 (data/weather/WxCode.kt)

CWA 用 1-23 表示天氣（晴/多雲/陰/雨/雷雨）。本 APP 對應到 0.05–1.00 的「雲量比例」，再轉換成 photogenic 子分。

---

## 7. 主要 Use Cases

| Use Case | 輸入 | 輸出 |
|---|---|---|
| `PredictHotspotsUseCase` | 日期, 熱點清單 | `List<HotspotPrediction>` (sunset 事件 + 距離 + 對齊偏差)；> 25km 自動標 `TOO_FAR` |
| `ComputeGoldenLineUseCase` | 日期 | `GoldenLine`：主塔朝 (日落方位 + 180°) 的測地射線採樣 |
| `SimulateFocalLengthUseCase` | 觀察者、焦距、感光元件 | `FocalSimulationResult`：FOV、塔/太陽畫面佔比 |
| `ScanGoldenCalendarUseCase` | 起始日, 天數, 容差 | `List<GoldenDate>`：未來 N 天滿足對齊條件的日期 |
| `ComputeSunsetScoreUseCase` | 對齊偏差, 天氣 | `SunsetScore` (overall, alignment, photogenic, rainSafe, verdict) |

---

## 8. AR 實作 (無金鑰)

### 8.1 不需要的東西

- ❌ Google Cloud Project
- ❌ ARCore Geospatial API
- ❌ Mapbox / Lightship / 8th Wall
- ❌ Sceneform / Filament

### 8.2 用到的元件 (全部 Android SDK 內建)

| 元件 | 用途 |
|---|---|
| `SensorManager.TYPE_ROTATION_VECTOR` | 已融合磁+加+陀螺儀的手機朝向四元數 |
| `GeomagneticField` | 即時磁偏角（台灣 ~-4.5°） |
| `FusedLocationProviderClient` | GPS + altitude |
| `CameraX` | 預覽顯示 |
| `Camera2 CameraCharacteristics` | 焦距 + 感光元件 → 實測 FOV |
| `Camera.cameraControl.setExposureCompensationIndex` | 校正模式自動降低曝光保護眼睛 |

### 8.3 螢幕投影公式

```
hOffset = signedAzimuthDelta(target.azimuth, camera.azimuth)
vOffset = target.altitude - camera.pitch
xFrac = 0.5 + hOffset / FOV_h        // 畫面寬度 fraction
yFrac = 0.5 - vOffset / FOV_v
inFrame = |hOffset| < FOV_h/2  &&  |vOffset| < FOV_v/2
```

### 8.4 校正

對準太陽按確認後：
```
azimuthOffset = signedAzimuthDelta(sun.azimuth, sensor.azimuth)
pitchOffset = sun.altitude - sensor.pitch
```

之後所有投影：
```
camera.azimuth = sensor.azimuth + azimuthOffset
camera.pitch = sensor.pitch + pitchOffset
```

校正僅 in-memory，每次進入 AR 重新校正（環境磁場干擾每次都不同）。

### 8.5 太陽軌跡密度

範圍：`now - 1h` 到 `sunset + 1h`。採樣間距隨距日落時刻變化：

| 距日落 | 間距 |
|---|---|
| ≤ 5 min | 30 秒/點 |
| 5-15 min | 1 分鐘/點 |
| > 15 min | 3 分鐘/點 |

繪製時點半徑、透明度也依距日落時刻調整（鈴鐺型分布）。

**效能優化**：軌跡的世界座標 (azimuth/altitude) 快取，僅在 observer 或 sunsetTime 變動時重算；每幀 orientation 更新只做投影 (~50ns/點)。

---

## 9. 自訂熱點與匯入匯出

### 資料層

`CustomHotspotStore` — DataStore preferences，存 JSON 序列化的 `List<CustomHotspotDto>`，最多保留 5 筆。

### 覆寫機制

預設熱點以代碼常數定義 (`DefaultHotspots.ALL`)；使用者編輯時不修改原常數，而是**以同 id 寫入 custom store** 作為「覆寫」。

合併邏輯：
```kotlin
val customsById = customs.associateBy { it.id }
val merged = DefaultHotspots.ALL.map { customsById[it.id] ?: it } +
    customs.filter { c -> DefaultHotspots.ALL.none { it.id == c.id } }
```

刪除覆寫即可回復預設。

### 25 km 距離限制

`PredictHotspotsUseCase` 內：距主塔 > 25 km 直接標 `AlignmentClass.TOO_FAR`，跳過 SunCalc 計算，UI 顯示灰色「遠」徽章與「太遠」訊息。

### 匯入匯出

- **匯出**：`ActivityResultContracts.CreateDocument("application/json")` → 寫整個 mergedHotspots 列表（含預設熱點的解析後字串名）
- **匯入**：`ActivityResultContracts.OpenDocument()` → 解析 JSON → 合併到 custom store；id 相同覆寫
- 不需任何權限（SAF 由系統管理）

JSON 格式：
```json
{
  "version": 1,
  "hotspots": [
    {"id": "sand_dune", "name": "沙崙海灘", "lat": 25.196, "lon": 121.408,
     "elev": 2.0, "description": "..."}
  ]
}
```

---

## 10. 每日推播 (WorkManager)

```
DanjiangApp.onCreate
    ↓ DailyScoreWorker.schedule()
    ↓
PeriodicWorkRequest (1 day, ExistingPolicy.KEEP)
    ↓ 觸發於凌晨 3:00 (NetworkType.CONNECTED 限制)
    ↓
ScanGoldenCalendarUseCase(today, days=7, maxOffset=5°)
    ↓ 每天最佳熱點 + WeatherForecast → 整體分數
    ↓ score ≥ 85
NotificationHelper.postGoldenDate(...)
    ↓ NotificationCompat.BigTextStyle 通知
```

權限：Android 13+ 啟動時請求 `POST_NOTIFICATIONS`，被拒絕也不影響 APP 其他功能。

---

## 11. UI 導覽結構

```
DanjiangApp (Compose root)
└─ NavHost
    ├─ "hotspots"          熱點清單 (含日期選擇、FAB 新增、匯入匯出)
    ├─ "hotspot_detail/{id}" 熱點詳情 (含拍攝品質分項)
    ├─ "map"               地圖 + 黃金射線 + 點擊查詢
    ├─ "simulator"         焦段模擬 + 時間 slider + 太陽軌跡
    ├─ "ar"                AR 實境勘景 + 校正
    └─ "calendar"          365 天黃金日曆
```

底部 NavigationBar 5 個 tab（map 與 hotspot_detail 不在底部，但屬同一個 NavHost）。

---

## 12. 設定檔與環境

### `local.properties`（不入版控）

```properties
sdk.dir=C:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk

# 中央氣象署免費金鑰：https://opendata.cwa.gov.tw/userLogin
CWA_API_KEY=CWA-XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX

# 目前未使用 (預留)
GOOGLE_MAPS_API_KEY=
```

### Gradle Wrapper

固定到 **Gradle 8.11.1**（AGP 8.7.3 對 Gradle 9 尚未完整支援）。

### 建置指令

```powershell
.\gradlew.bat :app:compileDebugKotlin   # 純編譯
.\gradlew.bat :app:testDebugUnitTest    # 27 個單元測試
.\gradlew.bat :app:assembleDebug        # debug APK (~66 MB)
```

---

## 13. 關鍵設計決策

| 決策 | 理由 |
|---|---|
| Kotlin + Compose | 原生效能、AR/感測器整合深、Material 3 動態色彩支援 |
| MapLibre Native (非 Google Maps) | 完全免費、不需金鑰；用 OpenFreeMap 圖磚 |
| 自建 AR (非 ARCore Geospatial) | 不需 Google Cloud；GPS+磁力計精度 ±3-5°（校正後 < 0.5°）對淡江情境足夠 |
| commons-suncalc | 純 JVM、可離線；NOAA SPA 演算法誤差 < 0.0003° |
| DataStore preferences (非 Room) | 自訂熱點 / 校正紀錄資料量小，不需要 schema 變遷的彈性 |
| 預設熱點以代碼常數定義 + 覆寫機制 | 預設清單演進易；使用者修改不會破壞 |
| 25 km 距離限制 | 距離大於 25km 後角寬太小 (< 0.05°)，視覺上幾乎是一點，意義不大 |
| 校正 in-memory only | 環境磁場每次都不同，舊校正套用反而誤導；強制重新校正 |
| WorkManager PeriodicWork (KEEP policy) | 跨重啟保留排程；網路條件可設限 |

---

## 14. 已知限制 / 待改善

| 項目 | 現況 | 計畫 |
|---|---|---|
| 視線遮蔽 (Line of Sight) | LineOfSightChecker 寫好但未串高程資料 | 接 SRTM 30m 離線檔或開源 Open-Elevation |
| 潮汐倒影評分 | 未實作 | 串 CWA F-A0021-001 潮汐預報，加為評分第 4 維度 |
| 月落穿塔 (Moonset Through Tower) | 未實作 | commons-suncalc 已有 MoonTimes，新增掃描 |
| 拍攝紀錄本 | 未實作 | 使用者上傳成果照 + 反向標註 |
| 多語系 (i18n) | 僅 zh-TW + en strings | 後續可加日文 (淡水觀光客多) |
| 持久化校正 | 已實作後回退 (環境磁場每次不同)，留純 in-memory | 可實作「同一地點」記憶（按 GPS 群聚） |

---

## 15. 檔案地圖

```
android_app2/
├─ ARCHITECTURE.md                ← 本文件
├─ build.gradle.kts               根 Gradle (plugin 宣告)
├─ settings.gradle.kts            module include
├─ gradle.properties              JVM / KSP 設定
├─ gradle/libs.versions.toml      版本目錄 (集中管理依賴)
├─ local.properties.example       金鑰範本
├─ gradlew / gradlew.bat          wrapper
├─ gradle/wrapper/                wrapper jar + properties
└─ app/
    ├─ build.gradle.kts           module Gradle (依賴 + buildConfig)
    ├─ proguard-rules.pro
    └─ src/
        ├─ main/
        │   ├─ AndroidManifest.xml
        │   ├─ kotlin/com/ghtinc/danjiangsunseeker/
        │   │   ├─ DanjiangApp.kt              @HiltAndroidApp Application
        │   │   ├─ MainActivity.kt             @AndroidEntryPoint
        │   │   ├─ data/                       (見 §3 模組架構)
        │   │   ├─ domain/
        │   │   └─ presentation/
        │   └─ res/
        │       ├─ values/strings.xml          zh-TW (預設)
        │       ├─ values-en/strings.xml       en
        │       ├─ mipmap-anydpi-v26/          adaptive launcher icons
        │       └─ drawable/ic_launcher_foreground.xml
        └─ test/kotlin/...                     27 個 JVM 單元測試
```

---

## 16. 測試覆蓋

| 測試類別 | 數量 | 重點 |
|---|---|---|
| `AtmosphericRefractionTest` | 5 | Bennett 公式 vs Allen 標準大氣值 |
| `GeodesyTest` | 6 | Vincenty 自洽性、與 Haversine 在近距離一致 |
| `SunCalcDataSourceTest` | 4 | 對照 NOAA 春分/夏至/冬至方位、大屯山高度修正 |
| `PredictHotspotsUseCaseTest` | 3 | 9 個熱點都產生預測；距離 / 角寬合理 |
| `ScanGoldenCalendarUseCaseTest` | 3 | 淡水渡船頭有對齊機會；沙崙永遠無；結果按日排序 |
| `ComputeSunsetScoreUseCaseTest` | 5 | 完美對齊 + 好天氣 → ≥ 85；爛對齊救不了；無天氣 fallback |

執行：`.\gradlew.bat :app:testDebugUnitTest`

---

## 17. 撰寫慣例

- **註解原則**：只在「為什麼」非顯而易見時寫；不重複描述「做什麼」
- **註解語言**：繁體中文為主（程式碼註解內），技術詞保留英文
- **檔案 license header**：不加
- **錯誤訊息**：使用者面向訊息用中文（如「校正失敗：尚未取得 GPS」）
- **物理常數**：寫進 domain/model 為頂層 const val
- **數學公式**：在註解中以普通文字寫，不用 LaTeX
