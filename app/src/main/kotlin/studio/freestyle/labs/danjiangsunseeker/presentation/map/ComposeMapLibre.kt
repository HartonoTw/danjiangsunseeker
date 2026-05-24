package studio.freestyle.labs.danjiangsunseeker.presentation.map

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * MapLibre Native 的 Compose 包裝。
 *
 * Why: MapLibre 的 [MapView] 是 View-based 元件，必須手動轉發 Android lifecycle 事件
 * (onCreate / onStart / onResume / onPause / onStop / onDestroy / onLowMemory)，
 * 否則地圖無法正常顯示或會洩漏記憶體。
 *
 * @param styleUri 樣式 URL — 預設 OpenFreeMap Liberty (完全免費、無 API 金鑰、無流量限制)
 * @param onMapReady 樣式載入完成時呼叫，可用來新增 source / layer / annotation
 * @param onMapClick 使用者點擊地圖時呼叫，傳入 (lat, lon)
 */
@Composable
fun ComposeMapLibre(
    modifier: Modifier = Modifier,
    styleUri: String = OPEN_FREE_MAP_LIBERTY,
    onMapReady: (MapLibreMap, Style) -> Unit = { _, _ -> },
    onMapClick: (Double, Double) -> Unit = { _, _ -> },
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { mutableMapViewHolder() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).also { view ->
                mapView.view = view
                view.onCreate(null)
                view.getMapAsync { map ->
                    map.setStyle(styleUri) { style ->
                        onMapReady(map, style)
                    }
                    map.addOnMapClickListener { point ->
                        onMapClick(point.latitude, point.longitude)
                        true
                    }
                }
            }
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val v = mapView.view ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> v.onStart()
                Lifecycle.Event.ON_RESUME -> v.onResume()
                Lifecycle.Event.ON_PAUSE -> v.onPause()
                Lifecycle.Event.ON_STOP -> v.onStop()
                Lifecycle.Event.ON_DESTROY -> v.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.view?.onDestroy()
            mapView.view = null
        }
    }
}

/** 用一個輕量 holder 包住 mutable MapView ref，避免在 remember 內部直接 var。 */
private class MapViewHolder { var view: MapView? = null }
private fun mutableMapViewHolder() = MapViewHolder()

/** OpenFreeMap 提供完全免費的 OSM 圖磚 + MapLibre style，無金鑰、無流量限制。 */
const val OPEN_FREE_MAP_LIBERTY: String = "https://tiles.openfreemap.org/styles/liberty"
const val OPEN_FREE_MAP_POSITRON: String = "https://tiles.openfreemap.org/styles/positron"
const val OPEN_FREE_MAP_BRIGHT: String = "https://tiles.openfreemap.org/styles/bright"
