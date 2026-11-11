package tech.hezy.alarm

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

fun Context.withChinese(): Context {
    val locale = Locale.SIMPLIFIED_CHINESE
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}
