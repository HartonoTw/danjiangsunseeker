package studio.freestyle.labs.danjiangsunseeker

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import studio.freestyle.labs.danjiangsunseeker.data.notifications.NotificationHelper
import studio.freestyle.labs.danjiangsunseeker.data.settings.LocaleManager
import studio.freestyle.labs.danjiangsunseeker.presentation.app.DanjiangApp as DanjiangAppRoot
import studio.freestyle.labs.danjiangsunseeker.presentation.theme.DanjiangTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒絕也沒關係，通知會 silently skip */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ 需要動態請求 POST_NOTIFICATIONS 才會看到通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasPermission(this)
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            DanjiangTheme {
                DanjiangAppRoot()
            }
        }
    }
}
