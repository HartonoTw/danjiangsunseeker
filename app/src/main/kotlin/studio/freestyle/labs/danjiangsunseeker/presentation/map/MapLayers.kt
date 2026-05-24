package studio.freestyle.labs.danjiangsunseeker.presentation.map

import android.content.Context
import android.util.Log
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.GoldenLine
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * 集中管理本 APP 在地圖上的圖層 ID 與來源 ID，並提供更新方法。
 * 採 GeoJsonSource + 多個 layer 的標準 MapLibre 模式，可即時更新資料。
 */
object MapLayers {

    private const val SRC_TOWER = "src-tower"
    private const val SRC_HOTSPOTS = "src-hotspots"
    private const val SRC_GOLDEN_LINE = "src-golden-line"
    private const val SRC_TOWER_TOP_GOLDEN_LINE = "src-tower-top-golden-line"
    private const val SRC_TAP = "src-tap"

    private const val LAYER_TOWER_CIRCLE = "layer-tower-circle"
    private const val LAYER_TOWER_LABEL = "layer-tower-label"
    private const val LAYER_HOTSPOT_CIRCLE = "layer-hotspot-circle"
    private const val LAYER_HOTSPOT_LABEL = "layer-hotspot-label"
    private const val LAYER_GOLDEN_LINE = "layer-golden-line"
    private const val LAYER_TOWER_TOP_GOLDEN_LINE = "layer-tower-top-golden-line"
    private const val LAYER_TAP_CIRCLE = "layer-tap-circle"
    private const val LAYER_TAP_MARK = "layer-tap-mark"

    private const val PROP_NAME = "name"
    private const val TAG = "MapDebug"

    // OpenFreeMap Liberty 的 glyphs server 用 Noto Sans 系列字體。
    // Why 必須明確指定：沒設 textFont 時，MapLibre 拿到的預設 fontstack 名稱常與 server 上實際存在的對不上，
    // 中文 CJK 字塊載入失敗 → SymbolLayer placement 異常 → 整個 symbol(含 circle 旁的 icon/label)被擠掉。
    private val LABEL_FONT_STACK = arrayOf("Noto Sans Regular")

    /**
     * 在 Style 載入完成後呼叫一次，建立所有 source 與 layer。
     * 設計為純新增（add-only）且冪等：每個 source/layer 只在尚未存在時才加入，
     * 永遠不執行 remove 操作，避免與 MapLibre render thread 的 race condition。
     *
     * 只從 onMapReady 呼叫，不從 LaunchedEffect 呼叫。
     * 熱點資料的後續更新請使用 [updateHotspots]。
     */
    fun install(context: Context, style: Style, hotspots: List<Hotspot>) {
        val towerExists = style.getSourceAs<GeoJsonSource>(SRC_TOWER) != null
        val hotspotsExists = style.getSourceAs<GeoJsonSource>(SRC_HOTSPOTS) != null
        val goldenExists = style.getSourceAs<GeoJsonSource>(SRC_GOLDEN_LINE) != null
        val towerTopGoldenExists = style.getSourceAs<GeoJsonSource>(SRC_TOWER_TOP_GOLDEN_LINE) != null
        val tapExists = style.getSourceAs<GeoJsonSource>(SRC_TAP) != null
        Log.d(TAG, "install: hotspots.size=${hotspots.size} existing(tower=$towerExists hotspots=$hotspotsExists golden=$goldenExists topGolden=$towerTopGoldenExists tap=$tapExists)")
        if (!towerExists) addTower(style)
        if (!hotspotsExists) addHotspots(style, hotspots, context)
        if (!goldenExists) addGoldenLine(style, SRC_GOLDEN_LINE, LAYER_GOLDEN_LINE, "#EB6432", 4f, 0.85f)
        if (!towerTopGoldenExists) addGoldenLine(style, SRC_TOWER_TOP_GOLDEN_LINE, LAYER_TOWER_TOP_GOLDEN_LINE, "#2F80ED", 3.5f, 0.82f)
        if (!tapExists) addTapMark(style)
    }

    /**
     * 在自訂熱點資料變動時呼叫（從 LaunchedEffect），只更新 source 的 GeoJSON 資料，
     * 不新增或移除任何 layer，因此不存在 render thread race condition。
     */
    fun updateHotspots(style: Style, hotspots: List<Hotspot>, context: Context) {
        val src = style.getSourceAs<GeoJsonSource>(SRC_HOTSPOTS)
        Log.d(TAG, "updateHotspots: source=${if (src == null) "NULL" else "ok"} hotspots.size=${hotspots.size}")
        src?.setGeoJson(buildHotspotCollection(hotspots, context))
    }

    /**
     * 更新「使用者點選的座標」標記。傳 null 代表清除。
     */
    fun updateTapMark(style: Style, latitude: Double?, longitude: Double?) {
        val src = style.getSourceAs<GeoJsonSource>(SRC_TAP)
        Log.d(TAG, "updateTapMark: source=${if (src == null) "NULL" else "ok"} lat=$latitude lon=$longitude")
        if (src == null) return
        if (latitude == null || longitude == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(longitude, latitude))
        src.setGeoJson(FeatureCollection.fromFeature(feature))
    }

    fun updateGoldenLine(style: Style, line: GoldenLine?) {
        updateLineSource(style, SRC_GOLDEN_LINE, line)
    }

    fun updateTowerTopGoldenLine(style: Style, line: GoldenLine?) {
        updateLineSource(style, SRC_TOWER_TOP_GOLDEN_LINE, line)
    }

    private fun updateLineSource(style: Style, sourceId: String, line: GoldenLine?) {
        val src = style.getSourceAs<GeoJsonSource>(sourceId) ?: return
        if (line == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val points = buildList {
            add(Point.fromLngLat(line.fromTower.longitude, line.fromTower.latitude))
            line.sampledPoints.forEach { p ->
                add(Point.fromLngLat(p.point.longitude, p.point.latitude))
            }
        }
        val feature = Feature.fromGeometry(LineString.fromLngLats(points))
        src.setGeoJson(FeatureCollection.fromFeature(feature))
    }

    private fun addTower(style: Style) {
        val towerFeature = Feature.fromGeometry(
            Point.fromLngLat(BridgeTower.LONGITUDE, BridgeTower.LATITUDE)
        ).apply { addStringProperty(PROP_NAME, "淡江大橋主塔") }

        style.addSource(
            GeoJsonSource(SRC_TOWER, FeatureCollection.fromFeature(towerFeature))
        )
        style.addLayer(
            CircleLayer(LAYER_TOWER_CIRCLE, SRC_TOWER).withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor("#E63946"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_TOWER_LABEL, SRC_TOWER).withProperties(
                PropertyFactory.textField(Expression.get(PROP_NAME)),
                PropertyFactory.textFont(LABEL_FONT_STACK),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor("#1A1A1A"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(2f),
                PropertyFactory.textOffset(arrayOf(0f, 1.6f)),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            )
        )
    }

    /** 建立熱點 FeatureCollection，供 addHotspots 及 install 的更新路徑共用。 */
    private fun buildHotspotCollection(hotspots: List<Hotspot>, context: Context): FeatureCollection {
        val features = hotspots.map { h ->
            val nameText = h.nameRes?.let { context.getString(it) } ?: h.customName.orEmpty()
            Log.d(TAG, "buildFeature: id=${h.id} nameRes=${h.nameRes} customName=${h.customName} -> nameText='$nameText' lat=${h.position.latitude} lon=${h.position.longitude}")
            Feature.fromGeometry(
                Point.fromLngLat(h.position.longitude, h.position.latitude)
            ).apply {
                addStringProperty(PROP_NAME, nameText)
                addStringProperty("id", h.id)
            }
        }
        Log.d(TAG, "buildHotspotCollection: features.size=${features.size}")
        return FeatureCollection.fromFeatures(features)
    }

    private fun addHotspots(style: Style, hotspots: List<Hotspot>, context: Context) {
        Log.d(TAG, "addHotspots: creating source SRC_HOTSPOTS with hotspots.size=${hotspots.size}")
        style.addSource(GeoJsonSource(SRC_HOTSPOTS, buildHotspotCollection(hotspots, context)))
        style.addLayer(
            CircleLayer(LAYER_HOTSPOT_CIRCLE, SRC_HOTSPOTS).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#FFA640"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_HOTSPOT_LABEL, SRC_HOTSPOTS).withProperties(
                PropertyFactory.textField(Expression.get(PROP_NAME)),
                PropertyFactory.textFont(LABEL_FONT_STACK),
                PropertyFactory.textSize(11f),
                PropertyFactory.textColor("#3A2A0F"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(2f),
                PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            )
        )
    }

    private fun addTapMark(style: Style) {
        style.addSource(GeoJsonSource(SRC_TAP, FeatureCollection.fromFeatures(emptyList())))
        // 視覺保底：即使 X 字塊載入失敗，仍能看到圓點識別位置
        style.addLayer(
            CircleLayer(LAYER_TAP_CIRCLE, SRC_TAP).withProperties(
                PropertyFactory.circleRadius(11f),
                PropertyFactory.circleColor("#D72638"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_TAP_MARK, SRC_TAP).withProperties(
                // 用 ASCII "X" 而非 ✕ (U+2715, Dingbats 區)，因為 OpenFreeMap 預烤 glyph 不一定包含該範圍
                PropertyFactory.textField("X"),
                PropertyFactory.textFont(LABEL_FONT_STACK),
                PropertyFactory.textSize(16f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            )
        )
    }

    private fun addGoldenLine(
        style: Style,
        sourceId: String,
        layerId: String,
        color: String,
        width: Float,
        opacity: Float,
    ) {
        // 先放一個空的 source；資料由 updateGoldenLine 設定
        style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyList())))
        style.addLayer(
            LineLayer(layerId, sourceId).withProperties(
                PropertyFactory.lineColor(color),
                PropertyFactory.lineWidth(width),
                PropertyFactory.lineOpacity(opacity),
            )
        )
    }
}
