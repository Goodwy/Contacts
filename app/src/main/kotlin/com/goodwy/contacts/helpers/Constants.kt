package com.goodwy.contacts.helpers

import com.goodwy.commons.helpers.TAB_CONTACTS
import com.goodwy.commons.helpers.TAB_FAVORITES
import com.goodwy.commons.helpers.TAB_GROUPS
import org.joda.time.DateTime

const val GROUP = "group"
const val IS_FROM_SIMPLE_CONTACTS = "is_from_simple_contacts"
const val ADD_NEW_CONTACT_NUMBER = "add_new_contact_number"
const val DEFAULT_FILE_NAME = "contacts.vcf"
const val AVOID_CHANGING_TEXT_TAG = "avoid_changing_text_tag"
const val AVOID_CHANGING_VISIBILITY_TAG = "avoid_changing_visibility_tag"

const val AUTOMATIC_BACKUP_REQUEST_CODE = 10001
//const val AUTO_BACKUP_INTERVAL_IN_DAYS = 1

const val AUTO_BACKUP_CONTACT_SOURCES = "auto_backup_contact_sources"

// extras used at third party intents
const val KEY_NAME = "name"
const val KEY_EMAIL = "email"
const val KEY_MAILTO = "mailto"

const val LOCATION_FAVORITES_TAB = 0
const val LOCATION_CONTACTS_TAB = 1
const val LOCATION_GROUP_CONTACTS = 2
const val LOCATION_INSERT_OR_EDIT = 3

val tabsList = arrayListOf(
    TAB_FAVORITES,
    TAB_CONTACTS,
    TAB_GROUPS
)
const val ALL_TABS_MASK = TAB_CONTACTS or TAB_FAVORITES or TAB_GROUPS

// phone number/email types
const val CELL = "CELL"
const val WORK = "WORK"
const val HOME = "HOME"
const val OTHER = "OTHER"
const val PREF = "PREF"
const val MAIN = "MAIN"
const val FAX = "FAX"
const val WORK_FAX = "WORK;FAX"
const val HOME_FAX = "HOME;FAX"
const val PAGER = "PAGER"
const val MOBILE = "MOBILE"

// IMs not supported by Ez-vcard
const val HANGOUTS = "Hangouts"
const val QQ = "QQ"
const val JABBER = "Jabber"

const val WHATSAPP = "whatsapp"
const val SIGNAL = "signal"
const val VIBER = "viber"
const val TELEGRAM = "telegram"
const val THREEMA = "threema"


// 6 am is the hardcoded automatic backup time, intervals shorter than 1 day are not yet supported.
//fun getNextAutoBackupTime(): DateTime {
//    val now = DateTime.now()
//    val sixHour = now.withHourOfDay(6)
//    return if (now.millis < sixHour.millis) {
//        sixHour
//    } else {
//        sixHour.plusDays(AUTO_BACKUP_INTERVAL_IN_DAYS)
//    }
//}
//
//fun getPreviousAutoBackupTime(): DateTime {
//    val nextBackupTime = getNextAutoBackupTime()
//    return nextBackupTime.minusDays(AUTO_BACKUP_INTERVAL_IN_DAYS)
//}

fun formatTime(showSeconds: Boolean, use24HourFormat: Boolean, hours: Int, minutes: Int, seconds: Int): String {
    val hoursFormat = if (use24HourFormat) "%02d" else "%01d"
    var format = "$hoursFormat:%02d"

    return if (showSeconds) {
        format += ":%02d"
        String.format(format, hours, minutes, seconds)
    } else {
        String.format(format, hours, minutes)
    }
}

// swiped left action
const val SWIPE_RIGHT_ACTION = "swipe_right_action"
const val SWIPE_LEFT_ACTION = "swipe_left_action"
const val SWIPE_ACTION_DELETE = 2
const val SWIPE_ACTION_BLOCK = 4 //!! isNougatPlus()
const val SWIPE_ACTION_CALL = 5
const val SWIPE_ACTION_MESSAGE = 6
const val SWIPE_ACTION_EDIT = 7
const val SWIPE_VIBRATION = "swipe_vibration"
const val SWIPE_RIPPLE = "swipe_ripple"
