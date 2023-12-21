package com.goodwy.contacts.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.goodwy.commons.extensions.toast
import com.goodwy.contacts.extensions.backupContacts

class AutomaticBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rightcontacts:automaticbackupreceiver")
        wakelock.acquire(5000)
        context.backupContacts{}
    }
}
