package com.goodwy.contacts.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.contacts.R

class DateTimePatternInfoDialog(activity: BaseSimpleActivity) {

    init {
        val view = activity.layoutInflater.inflate(R.layout.datetime_pattern_info_layout, null)
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok) { _, _ -> { } }
            .apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
