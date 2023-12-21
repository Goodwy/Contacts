package com.goodwy.contacts.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.contacts.extensions.checkAndBackupContactsOnBoot

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureBackgroundThread {
            context.apply {
                checkAndBackupContactsOnBoot()
            }
        }
    }
}
