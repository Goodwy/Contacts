package com.goodwy.contacts.extensions

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.FileProvider
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.contacts.BuildConfig
import com.goodwy.contacts.R
import com.goodwy.contacts.helpers.*
import com.goodwy.contacts.receivers.AutomaticBackupReceiver
import org.joda.time.DateTime
import java.io.File
import java.io.FileOutputStream

val Context.config: Config get() = Config.newInstance(applicationContext)
fun Context.getCachePhotoUri(file: File = getCachePhoto()) = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", file)

@SuppressLint("UseCompatLoadingForDrawables")
fun Context.getPackageDrawable(packageName: String): Drawable {
    return resources.getDrawable(
        when (packageName) {
            "google" -> R.drawable.ic_google
            SMT_PRIVATE -> R.drawable.ic_launcher
            TELEGRAM_PACKAGE -> R.drawable.ic_telegram_vector
            SIGNAL_PACKAGE -> R.drawable.ic_signal_vector
            WHATSAPP_PACKAGE -> R.drawable.ic_whatsapp_vector
            VIBER_PACKAGE -> R.drawable.ic_viber_vector
            else -> R.drawable.ic_threema_vector
        }, theme
    )
}

fun Context.getAutomaticBackupIntent(): PendingIntent {
    val intent = Intent(this, AutomaticBackupReceiver::class.java)
    return PendingIntent.getBroadcast(this, AUTOMATIC_BACKUP_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun Context.scheduleNextAutomaticBackup() {
    if (config.autoBackup) {
        val backupAtMillis = getNextAutoBackupTime().millis
        val pendingIntent = getAutomaticBackupIntent()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, backupAtMillis, pendingIntent)
            config.nextAutoBackupTime = backupAtMillis
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun Context.cancelScheduledAutomaticBackup() {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(getAutomaticBackupIntent())
}

fun Context.checkAndBackupContactsOnBoot() {
    if (config.autoBackup) {
        val previousRealBackupTime = config.lastAutoBackupTime
        val previousScheduledBackupTime = getPreviousAutoBackupTime().millis
        val missedPreviousBackup = previousRealBackupTime < previousScheduledBackupTime
        if (missedPreviousBackup) {
            // device was probably off at the scheduled time so backup now
            backupContacts{}
        }
    }
}

fun Context.backupContacts(callback: (success: Boolean) -> Unit) {
    require(isRPlus())
    ensureBackgroundThread {
        val config = config
        ContactsHelper(this).getContactsToExport(selectedContactSources = config.autoBackupContactSources) { contactsToBackup ->
            if (contactsToBackup.isEmpty()) {
                toast(com.goodwy.commons.R.string.no_entries_for_exporting)
                config.lastAutoBackupTime = DateTime.now().millis
                scheduleNextAutomaticBackup()
                return@getContactsToExport
            }


            val now = DateTime.now()
            val year = now.year.toString()
            val month = now.monthOfYear.ensureTwoDigits()
            val day = now.dayOfMonth.ensureTwoDigits()
            val hours = now.hourOfDay.ensureTwoDigits()
            val minutes = now.minuteOfHour.ensureTwoDigits()
            val seconds = now.secondOfMinute.ensureTwoDigits()

            val filename = config.autoBackupFilename
                .replace("%Y", year, false)
                .replace("%M", month, false)
                .replace("%D", day, false)
                .replace("%h", hours, false)
                .replace("%m", minutes, false)
                .replace("%s", seconds, false)

            val outputFolder = File(config.autoBackupFolder).apply {
                mkdirs()
            }

            var exportFile = File(outputFolder, "$filename.vcf")
            var exportFilePath = exportFile.absolutePath
            val outputStream = try {
                if (hasProperStoredFirstParentUri(exportFilePath)) {
                    val exportFileUri = createDocumentUriUsingFirstParentTreeUri(exportFilePath)
                    if (!getDoesFilePathExist(exportFilePath)) {
                        createSAFFileSdk30(exportFilePath)
                    }
                    applicationContext.contentResolver.openOutputStream(exportFileUri, "wt") ?: FileOutputStream(exportFile)
                } else {
                    var num = 0
                    while (getDoesFilePathExist(exportFilePath) && !exportFile.canWrite()) {
                        num++
                        exportFile = File(outputFolder, "${filename}_${num}.vcf")
                        exportFilePath = exportFile.absolutePath
                    }
                    FileOutputStream(exportFile)
                }
            } catch (e: Exception) {
                showErrorToast(e)
                scheduleNextAutomaticBackup()
                return@getContactsToExport
            }

            val exportResult = try {
                ContactsHelper(this).exportContacts(contactsToBackup, outputStream)
            } catch (e: Exception) {
                showErrorToast(e)
            }

            config.lastAutoBackupTime = DateTime.now().millis
            scheduleNextAutomaticBackup()

            when (exportResult) {
                ExportResult.EXPORT_OK -> {
                    toast(com.goodwy.commons.R.string.exporting_successful)
                    callback(true)
                }
                else -> {
                    toast(com.goodwy.commons.R.string.exporting_failed)
                    callback(false)
                }
            }
        }
    }
}

fun Context.getFormattedTime(passedSeconds: Int, showSeconds: Boolean, makeAmPmSmaller: Boolean): SpannableString {
    val use24HourFormat = DateFormat.is24HourFormat(this)
    val hours = (passedSeconds / 3600) % 24
    val minutes = (passedSeconds / 60) % 60
    val seconds = passedSeconds % 60

    return if (use24HourFormat) {
        val formattedTime = formatTime(showSeconds, use24HourFormat, hours, minutes, seconds)
        SpannableString(formattedTime)
    } else {
        val formattedTime = formatTo12HourFormat(showSeconds, hours, minutes, seconds)
        val spannableTime = SpannableString(formattedTime)
        val amPmMultiplier = if (makeAmPmSmaller) 0.4f else 1f
        spannableTime.setSpan(RelativeSizeSpan(amPmMultiplier), spannableTime.length - 3, spannableTime.length, 0)
        spannableTime
    }
}

fun Context.formatTo12HourFormat(showSeconds: Boolean, hours: Int, minutes: Int, seconds: Int): String {
    val appendable = getString(if (hours >= 12) com.goodwy.commons.R.string.p_m else com.goodwy.commons.R.string.a_m)
    val newHours = if (hours == 0 || hours == 12) 12 else hours % 12
    return "${formatTime(showSeconds, false, newHours, minutes, seconds)} $appendable"
}

fun Context.getNextAutoBackupTime(): DateTime {
    val now = DateTime.now()
    val hour = now.withHourOfDay(config.autoBackupTime / 60).withMinuteOfHour(config.autoBackupTime % 60).withSecondOfMinute(0)
    return if (now.millis < hour.millis) {
        hour
    } else {
        hour.plusDays(config.autoBackupInterval)
    }
}

fun Context.getPreviousAutoBackupTime(): DateTime {
    val nextBackupTime = getNextAutoBackupTime()
    return nextBackupTime.minusDays(config.autoBackupInterval)
}
