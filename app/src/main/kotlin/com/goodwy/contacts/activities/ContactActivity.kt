package com.goodwy.contacts.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.*
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.contacts.R
import com.goodwy.contacts.extensions.shareContacts
import com.goodwy.contacts.extensions.config
import com.goodwy.contacts.helpers.*
import kotlin.math.abs
import androidx.core.net.toUri
import androidx.core.graphics.drawable.toDrawable

abstract class ContactActivity : SimpleActivity() {
    protected val PICK_RINGTONE_INTENT_ID = 1500
    protected val INTENT_SELECT_RINGTONE = 600

    protected var contact: Contact? = null
    protected var originalRingtone: String? = null
    protected var currentContactPhotoPath = ""

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_RINGTONE_INTENT_ID && resultCode == RESULT_OK && resultData != null && resultData.dataString != null) {
            customRingtoneSelected(Uri.decode(resultData.dataString!!))
        } else if (requestCode == INTENT_SELECT_RINGTONE && resultCode == Activity.RESULT_OK && resultData != null) {
            val extras = resultData.extras
            if (extras?.containsKey(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) == true) {
                val uri = extras.getParcelable<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                try {
                    systemRingtoneSelected(uri)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    abstract fun customRingtoneSelected(ringtonePath: String)

    abstract fun systemRingtoneSelected(uri: Uri?)

    fun showPhotoPlaceholder(photoView: ImageView) {
        val fullName = contact?.getNameToDisplay() ?: "A"
        val placeholderImage =
            if (contact?.isABusinessContact() == true) {
                val drawable = ResourcesCompat.getDrawable(resources, R.drawable.placeholder_company, theme)
                if (baseConfig.useColoredContacts) {
                    val letterBackgroundColors = getLetterBackgroundColors()
                    val color = letterBackgroundColors[abs(fullName.hashCode()) % letterBackgroundColors.size].toInt()
                    (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                }
                drawable
            } else {
                SimpleContactsHelper(this).getContactLetterIcon(fullName).toDrawable(resources)
            }
        photoView.setImageDrawable(placeholderImage)
        currentContactPhotoPath = ""
        contact?.photo = null
    }

    fun updateContactPhoto(path: String, photoView: ImageView, bottomShadow: ImageView, bitmap: Bitmap? = null) {
        currentContactPhotoPath = path

        if (isDestroyed || isFinishing) {
            return
        }

        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()

        val wantedWidth = realScreenSize.x
        val wantedHeight = resources.getDimension(R.dimen.top_contact_image_height).toInt()

        Glide.with(this)
            .load(bitmap ?: path)
            .transition(DrawableTransitionOptions.withCrossFade())
            .apply(options)
            .apply(RequestOptions.circleCropTransform())
            .override(wantedWidth, wantedHeight)
            .listener(object : RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    photoView.background = 0.toDrawable()
                    //bottomShadow.beVisible()
                    return false
                }

                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    showPhotoPlaceholder(photoView)
                    bottomShadow.beGone()
                    return true
                }
            }).into(photoView)
    }

    fun deleteContact() {
        ConfirmationDialog(this) {
            if (contact != null) {
                ContactsHelper(this).deleteContact(contact!!, false) {
                    finish()
                }
            }
        }
    }

    fun shareContact(contact: Contact) {
        shareContacts(arrayListOf(contact))
    }

    fun trySendSMSRecommendation() {
        val simpleSmsMessenger = "com.goodwy.smsmessenger"
        val simpleSmsMessengerDebug = "com.goodwy.smsmessenger.debug"
        if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleSmsMessenger) && !isPackageInstalled(simpleSmsMessengerDebug))) {
            NewAppDialog(this, simpleSmsMessenger, getString(com.goodwy.strings.R.string.recommendation_dialog_messages_g), getString(com.goodwy.commons.R.string.right_sms_messenger),
                AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_sms_messenger)) {
                trySendSMS()
            }
        } else {
            trySendSMS()
        }
    }

    private fun trySendSMS() {
        val numbers = contact!!.phoneNumbers
        if (numbers.size == 1) {
            launchSendSMSIntent(numbers.first().value)
        } else if (numbers.size > 1) {
            val primaryNumber = numbers.find { it.isPrimary }
            if (primaryNumber != null) {
                launchSendSMSIntent(primaryNumber.value)
            } else {
                val items = ArrayList<RadioItem>()
                numbers.forEachIndexed { index, phoneNumber ->
                    items.add(RadioItem(index, phoneNumber.value, phoneNumber.value))
                }

                RadioGroupDialog(this, items) {
                    launchSendSMSIntent(it as String)
                }
            }
        }
    }

    fun trySendEmail() {
        val emails = contact!!.emails
        if (emails.size == 1) {
            sendEmailIntent(emails.first().value)
        } else if (emails.size > 1) {
            val items = ArrayList<RadioItem>()
            emails.forEachIndexed { index, email ->
                items.add(RadioItem(index, email.value, email.value))
            }

            RadioGroupDialog(this, items) {
                sendEmailIntent(it as String)
            }
        }
    }

    fun Context.getAddressTypeText(type: Int, label: String): String {
        return if (type == BaseTypes.TYPE_CUSTOM) {
            label
        } else {
            getString(
                when (type) {
                    StructuredPostal.TYPE_HOME -> com.goodwy.commons.R.string.home
                    StructuredPostal.TYPE_WORK -> com.goodwy.commons.R.string.work
                    else -> com.goodwy.commons.R.string.other
                }
            )
        }
    }

    fun Context.getIMTypeText(type: Int, label: String): String {
        return if (type == Im.PROTOCOL_CUSTOM) {
            label
        } else {
            getString(
                when (type) {
                    PROTOCOL_TEAMS -> com.goodwy.commons.R.string.teams
                    PROTOCOL_WECOM -> com.goodwy.commons.R.string.wecom
                    PROTOCOL_GOOGLE_CHAT -> com.goodwy.commons.R.string.google_chat
                    PROTOCOL_MATRIX -> com.goodwy.commons.R.string.matrix
                    PROTOCOL_DISCORD -> com.goodwy.commons.R.string.discord
                    PROTOCOL_WECHAT -> com.goodwy.commons.R.string.wechat
                    PROTOCOL_LINE -> com.goodwy.commons.R.string.line
                    Im.PROTOCOL_AIM -> com.goodwy.commons.R.string.aim
                    Im.PROTOCOL_MSN -> com.goodwy.commons.R.string.windows_live
                    Im.PROTOCOL_YAHOO -> com.goodwy.commons.R.string.yahoo
                    Im.PROTOCOL_SKYPE -> com.goodwy.commons.R.string.skype
                    Im.PROTOCOL_QQ -> com.goodwy.commons.R.string.qq
                    Im.PROTOCOL_GOOGLE_TALK -> com.goodwy.commons.R.string.hangouts
                    Im.PROTOCOL_ICQ -> com.goodwy.commons.R.string.icq
                    PROTOCOL_TELEGRAM -> com.goodwy.commons.R.string.telegram
                    PROTOCOL_TELEGRAM_CHANNEL -> com.goodwy.commons.R.string.telegram_channel
                    PROTOCOL_WHATSAPP -> com.goodwy.commons.R.string.whatsapp
                    PROTOCOL_INSTAGRAM -> com.goodwy.commons.R.string.instagram
                    PROTOCOL_FACEBOOK -> com.goodwy.commons.R.string.facebook
                    PROTOCOL_VIBER -> com.goodwy.commons.R.string.viber
                    PROTOCOL_SIGNAL -> com.goodwy.commons.R.string.signal
                    PROTOCOL_TWITTER -> com.goodwy.commons.R.string.twitter
                    PROTOCOL_LINKEDIN -> com.goodwy.commons.R.string.linkedin
                    PROTOCOL_THREEMA -> com.goodwy.commons.R.string.threema
                    else -> com.goodwy.commons.R.string.jabber
                }
            )
        }
    }

    protected fun getDefaultRingtoneUri() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    protected fun getRingtonePickerIntent(): Intent {
        val defaultRingtoneUri = getDefaultRingtoneUri()
        val currentRingtoneUri = if (contact!!.ringtone != null && contact!!.ringtone!!.isNotEmpty()) {
            contact!!.ringtone!!.toUri()
        } else if (contact!!.ringtone?.isNotEmpty() == false) {
            null
        } else {
            defaultRingtoneUri
        }

        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultRingtoneUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtoneUri)
        }
    }
}
