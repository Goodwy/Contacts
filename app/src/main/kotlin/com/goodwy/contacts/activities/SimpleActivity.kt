package com.goodwy.contacts.activities

import android.content.ContentValues
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getColoredDrawableWithColor
import com.goodwy.commons.extensions.getProperTextColor
import com.goodwy.commons.helpers.KEY_PHONE
import com.goodwy.contacts.R
import com.goodwy.contacts.helpers.KEY_MAILTO
import com.goodwy.contacts.helpers.LOCATION_CONTACTS_TAB
import com.goodwy.contacts.helpers.LOCATION_FAVORITES_TAB

open class SimpleActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher_green,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    protected fun getPhoneNumberFromIntent(intent: Intent): String? {
        if (intent.extras?.containsKey(KEY_PHONE) == true) {
            return intent.getStringExtra(KEY_PHONE)
        } else if (intent.extras?.containsKey("data") == true) {
            // sample contact number from Google Contacts:
            // data: [data1=+123 456 789 mimetype=vnd.android.cursor.item/phone_v2 _id=-1 data2=0]
            val data = intent.extras!!.get("data")
            if (data != null) {
                val contentValues = (data as? ArrayList<Any>)?.firstOrNull() as? ContentValues
                if (contentValues != null && contentValues.containsKey("data1")) {
                    return contentValues.getAsString("data1")
                }
            }
        }
        return null
    }

    protected fun getEmailFromIntent(intent: Intent): String? {
        return if (intent.dataString?.startsWith("$KEY_MAILTO:") == true) {
            Uri.decode(intent.dataString!!.substringAfter("$KEY_MAILTO:").trim())
        } else {
            null
        }
    }

    protected fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            LOCATION_FAVORITES_TAB -> R.drawable.ic_star_vector
            LOCATION_CONTACTS_TAB -> R.drawable.ic_person_rounded
            else -> R.drawable.ic_people_rounded
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    protected fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            LOCATION_FAVORITES_TAB -> R.string.favorites_tab
            LOCATION_CONTACTS_TAB -> R.string.contacts_tab
            else -> R.string.groups_tab
        }

        return resources.getString(stringId)
    }
}
