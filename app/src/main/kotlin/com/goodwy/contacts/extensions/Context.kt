package com.goodwy.contacts.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.FileProvider
import com.goodwy.commons.extensions.getCachePhoto
import com.goodwy.commons.helpers.SIGNAL_PACKAGE
import com.goodwy.commons.helpers.TELEGRAM_PACKAGE
import com.goodwy.commons.helpers.VIBER_PACKAGE
import com.goodwy.commons.helpers.WHATSAPP_PACKAGE
import com.goodwy.contacts.BuildConfig
import com.goodwy.contacts.R
import com.goodwy.contacts.helpers.Config
import java.io.File

val Context.config: Config get() = Config.newInstance(applicationContext)
fun Context.getCachePhotoUri(file: File = getCachePhoto()) = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", file)

@SuppressLint("UseCompatLoadingForDrawables")
fun Context.getPackageDrawable(packageName: String): Drawable {
    return resources.getDrawable(
        when (packageName) {
            TELEGRAM_PACKAGE -> R.drawable.ic_telegram_vector
            SIGNAL_PACKAGE -> R.drawable.ic_signal_vector
            WHATSAPP_PACKAGE -> R.drawable.ic_whatsapp_vector
            VIBER_PACKAGE -> R.drawable.ic_viber_vector
            else -> R.drawable.ic_threema_vector
        }, theme
    )
}
