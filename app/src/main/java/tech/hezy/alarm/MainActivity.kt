package tech.hezy.alarm

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.hezy.alarm.data.ContactBook
import tech.hezy.alarm.ring.AlarmBannerPopup
import tech.hezy.alarm.ring.RingSession
import tech.hezy.alarm.ring.RingingActivity
import tech.hezy.alarm.ring.RingingService
import tech.hezy.alarm.ring.formatAlarmBannerTime
import tech.hezy.alarm.ui.AlarmHome
import tech.hezy.alarm.ui.AlarmListViewModel
import tech.hezy.alarm.ui.IosFontFamily

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withChinese())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        val app = application as AlarmApplication
        setContent {
            val model = viewModel<AlarmListViewModel>(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        AlarmListViewModel(
                            app.graph.repository,
                            app.graph.scheduler,
                            app.graph.player,
                            ContactBook(app.contentResolver),
                            getString(R.string.alarm_default_label),
                        ) as T
                },
            )
            val state by model.state.collectAsState()
            val ringSession by RingSession.state.collectAsState()
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = IosFontFamily)) {
                Box(Modifier.fillMaxSize()) {
                    AlarmHome(state, model::dispatch)
                    AlarmBannerPopup(
                        time = formatAlarmBannerTime(this@MainActivity, ringSession.hour, ringSession.minute),
                        label = ringSession.label.ifBlank { stringResource(R.string.ringing) },
                        snoozeVisible = ringSession.snoozeVisible,
                        visible = ringSession.active && ringSession.inAppBanner,
                        onSnooze = { RingingService.snooze(this@MainActivity) },
                        onStop = { RingingService.stop(this@MainActivity) },
                        onOpenFullScreen = {
                            RingSession.promoteToFullScreen()
                            RingingActivity.start(this@MainActivity)
                        },
                    )
                }
            }
        }
    }
}
