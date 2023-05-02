package com.goodwy.contacts.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
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
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.letterBackgroundColors
import com.goodwy.commons.models.RadioItem
import com.goodwy.contacts.R
import com.goodwy.contacts.extensions.shareContacts
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.ContactRelation
import com.goodwy.contacts.extensions.config

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
        //val placeholder = BitmapDrawable(resources, getBigLetterPlaceholder(contact?.getNameToDisplay() ?: "A"))
        val placeholderImage = BitmapDrawable(resources, SimpleContactsHelper(this).getContactLetterIcon(contact!!.getNameToDisplay()))
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
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    photoView.background = ColorDrawable(0)
                    //bottomShadow.beVisible()
                    return false
                }

                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
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
            NewAppDialog(this, simpleSmsMessenger, getString(R.string.recommendation_dialog_messages_g), getString(R.string.right_sms_messenger),
                AppCompatResources.getDrawable(this, R.mipmap.ic_sms_messenger)) {
                trySendSMS()
            }
        } else {
            trySendSMS()
        }
    }

    fun trySendSMS() {
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

    fun getEmailTypeText(type: Int, label: String): String {
        return if (type == BaseTypes.TYPE_CUSTOM) {
            label
        } else {
            getString(
                when (type) {
                    Email.TYPE_HOME -> R.string.home
                    Email.TYPE_WORK -> R.string.work
                    Email.TYPE_MOBILE -> R.string.mobile
                    else -> R.string.other
                }
            )
        }
    }

    fun getRelationTypeText(type: Int, label: String): String {
        return if (type == BaseTypes.TYPE_CUSTOM) {
            label
        } else {
            getString(
                when (type) {
                    // Relation.TYPE_CUSTOM   -> R.string.custom
                    Relation.TYPE_ASSISTANT   -> R.string.relation_assistant_g
                    Relation.TYPE_BROTHER     -> R.string.relation_brother_g
                    Relation.TYPE_CHILD       -> R.string.relation_child_g
                    Relation.TYPE_DOMESTIC_PARTNER -> R.string.relation_domestic_partner_g
                    Relation.TYPE_FATHER      -> R.string.relation_father_g
                    Relation.TYPE_FRIEND      -> R.string.relation_friend_g
                    Relation.TYPE_MANAGER     -> R.string.relation_manager_g
                    Relation.TYPE_MOTHER      -> R.string.relation_mother_g
                    Relation.TYPE_PARENT      -> R.string.relation_parent_g
                    Relation.TYPE_PARTNER     -> R.string.relation_partner_g
                    Relation.TYPE_REFERRED_BY -> R.string.relation_referred_by_g
                    Relation.TYPE_RELATIVE    -> R.string.relation_relative_g
                    Relation.TYPE_SISTER      -> R.string.relation_sister_g
                    Relation.TYPE_SPOUSE      -> R.string.relation_spouse_g

                    // Relation types defined in vCard 4.0
                    ContactRelation.TYPE_CONTACT -> R.string.relation_contact_g
                    ContactRelation.TYPE_ACQUAINTANCE -> R.string.relation_acquaintance_g
                    // ContactRelation.TYPE_FRIEND -> R.string.relation_friend
                    ContactRelation.TYPE_MET -> R.string.relation_met_g
                    ContactRelation.TYPE_CO_WORKER -> R.string.relation_co_worker_g
                    ContactRelation.TYPE_COLLEAGUE -> R.string.relation_colleague_g
                    ContactRelation.TYPE_CO_RESIDENT -> R.string.relation_co_resident_g
                    ContactRelation.TYPE_NEIGHBOR -> R.string.relation_neighbor_g
                    // ContactRelation.TYPE_CHILD -> R.string.relation_child
                    // ContactRelation.TYPE_PARENT -> R.string.relation_parent
                    ContactRelation.TYPE_SIBLING -> R.string.relation_sibling_g
                    // ContactRelation.TYPE_SPOUSE -> R.string.relation_spouse
                    ContactRelation.TYPE_KIN -> R.string.relation_kin_g
                    ContactRelation.TYPE_MUSE -> R.string.relation_muse_g
                    ContactRelation.TYPE_CRUSH -> R.string.relation_crush_g
                    ContactRelation.TYPE_DATE -> R.string.relation_date_g
                    ContactRelation.TYPE_SWEETHEART -> R.string.relation_sweetheart_g
                    ContactRelation.TYPE_ME -> R.string.relation_me_g
                    ContactRelation.TYPE_AGENT -> R.string.relation_agent_g
                    ContactRelation.TYPE_EMERGENCY -> R.string.relation_emergency_g

                    ContactRelation.TYPE_SUPERIOR -> R.string.relation_superior_g
                    ContactRelation.TYPE_SUBORDINATE -> R.string.relation_subordinate_g
                    ContactRelation.TYPE_HUSBAND -> R.string.relation_husband_g
                    ContactRelation.TYPE_WIFE -> R.string.relation_wife_g
                    ContactRelation.TYPE_SON -> R.string.relation_son_g
                    ContactRelation.TYPE_DAUGHTER -> R.string.relation_daughter_g
                    ContactRelation.TYPE_GRANDPARENT -> R.string.relation_grandparent_g
                    ContactRelation.TYPE_GRANDFATHER -> R.string.relation_grandfather_g
                    ContactRelation.TYPE_GRANDMOTHER -> R.string.relation_grandmother_g
                    ContactRelation.TYPE_GRANDCHILD -> R.string.relation_grandchild_g
                    ContactRelation.TYPE_GRANDSON -> R.string.relation_grandson_g
                    ContactRelation.TYPE_GRANDDAUGHTER -> R.string.relation_granddaughter_g
                    ContactRelation.TYPE_UNCLE -> R.string.relation_uncle_g
                    ContactRelation.TYPE_AUNT -> R.string.relation_aunt_g
                    ContactRelation.TYPE_NEPHEW -> R.string.relation_nephew_g
                    ContactRelation.TYPE_NIECE -> R.string.relation_niece_g
                    ContactRelation.TYPE_FATHER_IN_LAW -> R.string.relation_father_in_law_g
                    ContactRelation.TYPE_MOTHER_IN_LAW -> R.string.relation_mother_in_law_g
                    ContactRelation.TYPE_SON_IN_LAW -> R.string.relation_son_in_law_g
                    ContactRelation.TYPE_DAUGHTER_IN_LAW -> R.string.relation_daughter_in_law_g
                    ContactRelation.TYPE_BROTHER_IN_LAW -> R.string.relation_brother_in_law_g
                    ContactRelation.TYPE_SISTER_IN_LAW -> R.string.relation_sister_in_law_g

                    else -> R.string.other
                }
            )
        }
    }

    fun getAddressTypeText(type: Int, label: String): String {
        return if (type == BaseTypes.TYPE_CUSTOM) {
            label
        } else {
            getString(
                when (type) {
                    StructuredPostal.TYPE_HOME -> R.string.home
                    StructuredPostal.TYPE_WORK -> R.string.work
                    else -> R.string.other
                }
            )
        }
    }

    fun getIMTypeText(type: Int, label: String): String {
        return if (type == Im.PROTOCOL_CUSTOM) {
            label
        } else {
            getString(
                when (type) {
                    Im.PROTOCOL_AIM -> R.string.aim
                    Im.PROTOCOL_MSN -> R.string.windows_live
                    Im.PROTOCOL_YAHOO -> R.string.yahoo
                    Im.PROTOCOL_SKYPE -> R.string.skype
                    Im.PROTOCOL_QQ -> R.string.qq
                    Im.PROTOCOL_GOOGLE_TALK -> R.string.hangouts
                    Im.PROTOCOL_ICQ -> R.string.icq
                    else -> R.string.jabber
                }
            )
        }
    }

    fun getEventTextId(type: Int) = when (type) {
        Event.TYPE_ANNIVERSARY -> R.string.anniversary
        Event.TYPE_BIRTHDAY -> R.string.birthday
        else -> R.string.other
    }

    private fun getBigLetterPlaceholder(name: String): Bitmap {
        val letter = name.getNameLetter()
        val height = resources.getDimension(R.dimen.top_contact_image_height).toInt()
        val bitmap = Bitmap.createBitmap(realScreenSize.x, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val view = TextView(this)
        view.layout(0, 0, bitmap.width, bitmap.height)

        val circlePaint = Paint().apply {
            color = letterBackgroundColors[Math.abs(name.hashCode()) % letterBackgroundColors.size].toInt()
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val wantedTextSize = bitmap.height / 2f
        val textPaint = Paint().apply {
            color = circlePaint.color.getContrastColor()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = wantedTextSize
            style = Paint.Style.FILL
        }

        canvas.drawPaint(circlePaint)

        val xPos = canvas.width / 2f
        val yPos = canvas.height / 2 - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(letter, xPos, yPos, textPaint)
        view.draw(canvas)
        return bitmap
    }

    protected fun getDefaultRingtoneUri() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    protected fun getRingtonePickerIntent(): Intent {
        val defaultRingtoneUri = getDefaultRingtoneUri()
        val currentRingtoneUri = if (contact!!.ringtone != null && contact!!.ringtone!!.isNotEmpty()) {
            Uri.parse(contact!!.ringtone)
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
