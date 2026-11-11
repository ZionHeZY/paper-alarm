package tech.hezy.alarm.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** 各机型闹铃要先打开的几项。自启动页面各厂商包名不一样，解析不到就不列。 */
internal class AlarmGate(private val context: Context) {
    private val prefs = context.getSharedPreferences("alarm_gate", Context.MODE_PRIVATE)

    fun notificationsGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun fullScreenGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    fun batteryGranted(): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun missingLabels(notify: String, full: String, battery: String, autostart: String): List<String> {
        val items = ArrayList<String>(4)
        if (!notificationsGranted()) items += notify
        if (!fullScreenGranted()) items += full
        if (!batteryGranted()) items += battery
        if (autostartIntent() != null) items += autostart
        return items
    }

    fun markAsked(permission: String) {
        prefs.edit().putBoolean(permission, true).apply()
    }

    fun permanentlyDenied(permission: String): Boolean {
        val activity = context as? Activity ?: return false
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) return false
        return prefs.getBoolean(permission, false) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun openNotificationSettings() {
        start(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return
        start(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:${context.packageName}")),
        )
    }

    fun openBatterySettings() {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        if (!start(direct)) start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openAppSettings() {
        start(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun openAutostart() {
        val intent = autostartIntent() ?: return
        prefs.edit().putBoolean(KEY_AUTOSTART, true).apply()
        start(intent)
    }

    private fun autostartIntent(): Intent? {
        if (prefs.getBoolean(KEY_AUTOSTART, false)) return null
        val manager = context.packageManager
        return AUTOSTART.firstNotNullOfOrNull { (pkg, cls) ->
            val intent = Intent().setClassName(pkg, cls)
            if (manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) intent else null
        }
    }

    private fun start(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private companion object {
        const val KEY_AUTOSTART = "autostart_opened"
        val AUTOSTART = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        )
    }
}
