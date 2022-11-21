package com.goodwy.contacts.activities

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.SelectAlarmSoundDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.contacts.R
import com.goodwy.contacts.dialogs.ChooseSocialDialog
import com.goodwy.contacts.dialogs.ManageVisibleFieldsDialog
import com.goodwy.contacts.extensions.*
import com.goodwy.contacts.helpers.*
import com.goodwy.contacts.models.*
import kotlinx.android.synthetic.main.activity_view_contact.*
import kotlinx.android.synthetic.main.item_view_address.view.*
import kotlinx.android.synthetic.main.item_view_contact_source.view.*
import kotlinx.android.synthetic.main.item_view_email.view.*
import kotlinx.android.synthetic.main.item_view_event.view.*
import kotlinx.android.synthetic.main.item_view_group.view.*
import kotlinx.android.synthetic.main.item_view_header.view.*
import kotlinx.android.synthetic.main.item_view_im.view.*
import kotlinx.android.synthetic.main.item_view_messengers_actions.view.*
import kotlinx.android.synthetic.main.item_view_note.view.*
import kotlinx.android.synthetic.main.item_view_phone_number.view.*
import kotlinx.android.synthetic.main.item_website.view.*
import kotlinx.android.synthetic.main.top_view.*

class ViewContactActivity : ContactActivity() {
    private var isViewIntent = false
    private var wasEditLaunched = false
    private var duplicateContacts = ArrayList<Contact>()
    private var contactSources = ArrayList<ContactSource>()
    private var showFields = 0
    private var fullContact: Contact? = null    // contact with all fields filled from duplicates
    private var duplicateInitialized = false
    private val mergeDuplicate: Boolean get() = config.mergeDuplicateContacts
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFFEBEBEB.toInt()

    private val COMPARABLE_PHONE_NUMBER_LENGTH = 9

    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_contact)

        if (checkAppSideloading()) {
            return
        }

        showFields = config.showContactFields
        contact_wrapper.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setupMenu()
    }

    override fun onResume() {
        super.onResume()
        isViewIntent = intent.action == ContactsContract.QuickContact.ACTION_QUICK_CONTACT || intent.action == Intent.ACTION_VIEW
        if (isViewIntent) {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    ensureBackgroundThread {
                        initContact()
                    }
                } else {
                    toast(R.string.no_contacts_permission)
                    finish()
                }
            }
        } else {
            ensureBackgroundThread {
                initContact()
            }
        }
        updateColors()
    }

    private fun updateColors(color: Int = getProperBackgroundColor()) {
        val whiteButton = AppCompatResources.getDrawable(this, R.drawable.call_history_button_white)

        if (baseConfig.backgroundColor == white) {
            supportActionBar?.setBackgroundDrawable(ColorDrawable(0xFFf2f2f6.toInt()))
            window.decorView.setBackgroundColor(0xFFf2f2f6.toInt())
            window.statusBarColor = 0xFFf2f2f6.toInt()
            window.navigationBarColor = 0xFFf2f2f6.toInt()
        } else window.decorView.setBackgroundColor(color)

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_send_sms.background = whiteButton
            contact_start_call.background = whiteButton
            contact_video_call.background = whiteButton
            contact_send_email.background = whiteButton
            val paddingLeftRight = resources.getDimensionPixelOffset(R.dimen.small_margin)
            val paddingTop = resources.getDimensionPixelOffset(R.dimen.ten_dpi)
            val paddingBottom = resources.getDimensionPixelOffset(R.dimen.medium_margin)
            contact_send_sms.setPadding(paddingLeftRight, paddingTop ,paddingLeftRight ,paddingBottom)
            contact_start_call.setPadding(paddingLeftRight, paddingTop ,paddingLeftRight ,paddingBottom)
            contact_video_call.setPadding(paddingLeftRight, paddingTop ,paddingLeftRight ,paddingBottom)
            contact_send_email.setPadding(paddingLeftRight, paddingTop ,paddingLeftRight ,paddingBottom)
        } else window.decorView.setBackgroundColor(color)

        var drawableSMS = resources.getDrawable(R.drawable.ic_sms_vector)
        drawableSMS = DrawableCompat.wrap(drawableSMS!!)
        DrawableCompat.setTint(drawableSMS, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableSMS, PorterDuff.Mode.SRC_IN)
        contact_send_sms.setCompoundDrawablesWithIntrinsicBounds(null, drawableSMS, null, null)
        contact_send_sms.setTextColor(getProperPrimaryColor())

        var drawableCall = resources.getDrawable(R.drawable.ic_phone_vector)
        drawableCall = DrawableCompat.wrap(drawableCall!!)
        DrawableCompat.setTint(drawableCall, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableCall, PorterDuff.Mode.SRC_IN)
        contact_start_call.setCompoundDrawablesWithIntrinsicBounds(null, drawableCall, null, null)
        contact_start_call.setTextColor(getProperPrimaryColor())

        var drawableVideoCall = resources.getDrawable(R.drawable.ic_videocam_vector)
        drawableVideoCall = DrawableCompat.wrap(drawableVideoCall!!)
        DrawableCompat.setTint(drawableVideoCall, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableVideoCall, PorterDuff.Mode.SRC_IN)
        contact_video_call.setCompoundDrawablesWithIntrinsicBounds(null, drawableVideoCall, null, null)
        contact_video_call.setTextColor(getProperPrimaryColor())

        var drawableMail = resources.getDrawable(R.drawable.ic_mail_vector)
        drawableMail = DrawableCompat.wrap(drawableMail!!)
        DrawableCompat.setTint(drawableMail, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableMail, PorterDuff.Mode.SRC_IN)
        contact_send_email.setCompoundDrawablesWithIntrinsicBounds(null, drawableMail, null, null)
        contact_send_email.setTextColor(getProperPrimaryColor())
    }

    override fun onBackPressed() {
        if (contact_photo_big.alpha == 1f) {
            hideBigContactPhoto()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupMenu() {
        //(contact_appbar.layoutParams as CoordinatorLayout.LayoutParams).topMargin = statusBarHeight
        (contact_wrapper.layoutParams as FrameLayout.LayoutParams).topMargin = statusBarHeight
        contact_toolbar.overflowIcon = resources.getColoredDrawableWithColor(R.drawable.ic_three_dots_vector, getProperBackgroundColor().getContrastColor())
        contact_toolbar.menu.apply {
            updateMenuItemColors(this)
            findItem(R.id.favorite).setOnMenuItemClickListener {
                val newIsStarred = if (contact!!.starred == 1) 0 else 1
                ensureBackgroundThread {
                    val contacts = arrayListOf(contact!!)
                    if (newIsStarred == 1) {
                        ContactsHelper(this@ViewContactActivity).addFavorites(contacts)
                    } else {
                        ContactsHelper(this@ViewContactActivity).removeFavorites(contacts)
                    }
                }
                contact!!.starred = newIsStarred
                val favoriteIcon = getStarDrawable(contact!!.starred == 1)
                favoriteIcon.setTint(getProperBackgroundColor().getContrastColor())
                findItem(R.id.favorite).icon = favoriteIcon
                true
            }

            findItem(R.id.share).setOnMenuItemClickListener {
                if (fullContact != null) {
                    shareContact(fullContact!!)
                }
                true
            }

            findItem(R.id.edit).setOnMenuItemClickListener {
                if (contact != null) {
                    launchEditContact(contact!!)
                }
                true
            }

            findItem(R.id.open_with).setOnMenuItemClickListener {
                openWith()
                true
            }

            findItem(R.id.delete).setOnMenuItemClickListener {
                deleteContactFromAllSources()
                true
            }

            findItem(R.id.manage_visible_fields).setOnMenuItemClickListener {
                ManageVisibleFieldsDialog(this@ViewContactActivity) {
                    showFields = config.showContactFields
                    ensureBackgroundThread {
                        initContact()
                    }
                }
                true
            }
        }

        val color = getProperBackgroundColor().getContrastColor()
        contact_toolbar.setNavigationIconTint(color)
        contact_toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initContact() {
        var wasLookupKeyUsed = false
        var contactId: Int
        try {
            contactId = intent.getIntExtra(CONTACT_ID, 0)
        } catch (e: Exception) {
            return
        }

        if (contactId == 0 && isViewIntent) {
            val data = intent.data
            if (data != null) {
                val rawId = if (data.path!!.contains("lookup")) {
                    val lookupKey = getLookupKeyFromUri(data)
                    if (lookupKey != null) {
                        contact = ContactsHelper(this).getContactWithLookupKey(lookupKey)
                        fullContact = contact
                        wasLookupKeyUsed = true
                    }

                    getLookupUriRawId(data)
                } else {
                    getContactUriRawId(data)
                }

                if (rawId != -1) {
                    contactId = rawId
                }
            }
        }

        if (contactId != 0 && !wasLookupKeyUsed) {
            contact = ContactsHelper(this).getContactWithId(contactId, intent.getBooleanExtra(IS_PRIVATE, false))
            fullContact = contact

            if (contact == null) {
                if (!wasEditLaunched) {
                    toast(R.string.unknown_error_occurred)
                }
                finish()
            } else {
                runOnUiThread {
                    gotContact()
                }
            }
        } else {
            if (contact == null) {
                finish()
            } else {
                runOnUiThread {
                    gotContact()
                }
            }
        }
    }

    private fun gotContact() {
        if (isDestroyed || isFinishing) {
            return
        }

        contact_scrollview.beVisible()
        setupViewContact()

        val placeholderImage = BitmapDrawable(resources, SimpleContactsHelper(this).getContactLetterIcon(contact!!.getNameToDisplay()))
        if (contact!!.photoUri.isEmpty() && contact!!.photo == null) {
            contact_photo.setImageDrawable(placeholderImage)
        } else {
            /*val options = RequestOptions()
                .signature(ObjectKey(contact!!.getSignatureKey()))
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .error(placeholderImage)
                .centerCrop()

            val itemToLoad: Any? = if (contact!!.photoUri.isNotEmpty()) {
                contact!!.photoUri
            } else {
                contact!!.photo
            }

            Glide.with(this)
                .load(itemToLoad)
                .apply(options)
                .apply(RequestOptions.circleCropTransform())
                .into(contact_photo)*/

            updateContactPhoto(contact!!.photoUri, contact_photo, contact_photo_bottom_shadow, contact!!.photo)
            val optionsBig = RequestOptions()
                //.transform(FitCenter(), RoundedCorners(resources.getDimension(R.dimen.normal_margin).toInt()))
                .transform(FitCenter())

            Glide.with(this)
                .load(contact!!.photo ?: currentContactPhotoPath)
                .apply(optionsBig)
                .into(contact_photo_big)

            contact_photo.setOnClickListener {
                contact_photo_big.alpha = 0f
                contact_photo_big.beVisible()
                contact_photo_big.animate().alpha(1f).start()
            }

            contact_photo_big.setOnClickListener {
                hideBigContactPhoto()
            }
        }

        updateTextColors(contact_scrollview)
        contact_toolbar.menu.findItem(R.id.open_with).isVisible = contact?.isPrivate() == false
    }

    private fun setupViewContact() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setupFavorite()
        setupNames()

        ContactsHelper(this).getContactSources {
            contactSources = it
            runOnUiThread {
                setupContactDetails()
            }
        }

        getDuplicateContacts {
            duplicateInitialized = true
            setupContactDetails()
        }
    }

    private fun setupContactDetails() {
        if (isFinishing || isDestroyed || contact == null) {
            return
        }

        setupPhoneNumbers()
        setupMessengersActions()
        setupVideoCallActions()
        setupEmails()
        setupAddresses()
        setupIMs()
        setupEvents()
        setupWebsites()
        setupGroups()
        setupContactSources()
        setupNotes()
        setupRingtone()
        setupOrganization()
        updateTextColors(contact_scrollview)
    }

    private fun launchEditContact(contact: Contact) {
        wasEditLaunched = true
        duplicateInitialized = false
        editContact(contact)
    }

    private fun openWith() {
        if (contact != null) {
            val uri = getContactPublicUri(contact!!)
            launchViewContactIntent(uri)
        }
    }

    private fun setupFavorite() {
        val favoriteIcon = getStarDrawable(contact!!.starred == 1)
        favoriteIcon.setTint(getProperBackgroundColor().getContrastColor())
        contact_toolbar.menu.findItem(R.id.favorite).icon = favoriteIcon

        contact_toggle_favorite.apply {
            //beVisible()
            applyColorFilter(getProperTextColor())
            tag = contact!!.starred
            setImageDrawable(getStarDrawable(tag == 1))

            setOnClickListener {
                val newIsStarred = if (tag == 1) 0 else 1
                ensureBackgroundThread {
                    val contacts = arrayListOf(contact!!)
                    if (newIsStarred == 1) {
                        ContactsHelper(context).addFavorites(contacts)
                    } else {
                        ContactsHelper(context).removeFavorites(contacts)
                    }
                }
                contact!!.starred = newIsStarred
                tag = contact!!.starred
                setImageDrawable(getStarDrawable(tag == 1))
            }

            setOnLongClickListener { toast(R.string.toggle_favorite); true; }
        }
    }

    private fun setupNames() {
        var displayName = contact!!.getNameToDisplay()
        if (contact!!.nickname.isNotEmpty()) {
            displayName += " (${contact!!.nickname})"
        }

        val showNameFields = showFields and SHOW_PREFIX_FIELD != 0 || showFields and SHOW_FIRST_NAME_FIELD != 0 || showFields and SHOW_MIDDLE_NAME_FIELD != 0 ||
            showFields and SHOW_SURNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0

        contact_name.text = displayName
        contact_name.setTextColor(getProperTextColor())
        contact_name.copyOnLongClick(displayName)
        contact_name.beVisibleIf(displayName.isNotEmpty() && !contact!!.isABusinessContact() && showNameFields)

        /*collapsingToolbar.setExpandedTitleColor(getProperTextColor())
        collapsingToolbar.setCollapsedTitleTextColor(getProperTextColor())
        collapsingToolbar.title = displayName
        collapsingToolbar.copyOnLongClick(displayName)*/
    }

    private fun setupPhoneNumbers() {
        var phoneNumbers = contact!!.phoneNumbers.toMutableSet() as LinkedHashSet<PhoneNumber>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                phoneNumbers.addAll(it.phoneNumbers)
            }
        }

        if (duplicateInitialized) {
            val contactDefaultsNumbers = contact!!.phoneNumbers.filter { it.isPrimary }
            val duplicateContactsDefaultNumbers = duplicateContacts.flatMap { it.phoneNumbers }.filter { it.isPrimary }
            val defaultNumbers = (contactDefaultsNumbers + duplicateContactsDefaultNumbers).toSet()

            if (defaultNumbers.size > 1) {
                phoneNumbers.forEach { it.isPrimary = false }
            } else if (defaultNumbers.size == 1) {
                if (mergeDuplicate) {
                    val defaultNumber = defaultNumbers.first()
                    val candidate = phoneNumbers.find { it.normalizedNumber == defaultNumber.normalizedNumber && !it.isPrimary }
                    candidate?.isPrimary = true
                } else {
                    duplicateContactsDefaultNumbers.forEach { defaultNumber ->
                        val candidate = phoneNumbers.find { it.normalizedNumber == defaultNumber.normalizedNumber && !it.isPrimary }
                        candidate?.isPrimary = true
                    }
                }
            }
        }

        phoneNumbers = phoneNumbers.distinctBy {
            if (it.normalizedNumber.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                it.normalizedNumber.substring(it.normalizedNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH)
            } else {
                it.normalizedNumber
            }
        }.toMutableSet() as LinkedHashSet<PhoneNumber>

        phoneNumbers = phoneNumbers.sortedBy { it.type }.toMutableSet() as LinkedHashSet<PhoneNumber>
        fullContact!!.phoneNumbers = phoneNumbers.toMutableList() as ArrayList<PhoneNumber>
        contact_numbers_holder.removeAllViews()

        if (phoneNumbers.isNotEmpty() && showFields and SHOW_PHONE_NUMBERS_FIELD != 0) {
            val isFirstItem = phoneNumbers.first()
            val isLastItem = phoneNumbers.last()
            phoneNumbers.forEach { phoneNumber ->
                layoutInflater.inflate(R.layout.item_view_phone_number, contact_numbers_holder, false).apply {

                    contact_numbers_holder.addView(this)
                    contact_number.text = phoneNumber.value
                    contact_number_type.text = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
                    copyOnLongClick(phoneNumber.value)

                    setOnClickListener {
                        if (config.showCallConfirmation) {
                            CallConfirmationDialog(this@ViewContactActivity, phoneNumber.value) {
                                startCallIntent(phoneNumber.value)
                            }
                        } else {
                            startCallIntent(phoneNumber.value)
                        }
                    }

                    val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                        contact_numbers_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_numbers_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_number_holder.default_toggle_icon.isVisible = phoneNumber.isPrimary
                    contact_number_holder.default_toggle_icon.setColorFilter(getProperTextColor())
                    contact_number_holder.contact_number_icon.isVisible = isFirstItem == phoneNumber
                    contact_number_holder.contact_number_icon.setColorFilter(getProperTextColor())
                    contact_number_holder.divider_phone_number.setBackgroundColor(getProperTextColor())
                    contact_number_holder.divider_phone_number.isGone = isLastItem == phoneNumber
                    contact_number_holder.contact_number.setTextColor(getProperPrimaryColor())
                }
            }
            contact_numbers_holder.beVisible()
        } else {
            contact_numbers_holder.beGone()
        }
    }

    private fun setupMessengersActions() {
        contact_messengers_actions_holder.removeAllViews()
        if (showFields and SHOW_MESSENGERS_ACTIONS_FIELD != 0) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            if (mergeDuplicate) {
                duplicateContacts.forEach {
                    sources[it] = getPublicContactSourceSync(it.source, contactSources)
                }
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (key, value) -> value.toLowerCase() }.toMap() as LinkedHashMap<Contact, String>
            }

            for ((key, value) in sources) {
                val isLastItem = sources.keys.last()
                layoutInflater.inflate(R.layout.item_view_messengers_actions, contact_messengers_actions_holder, false).apply {
                    contact_messenger_action_name.text = if (value == "") getString(R.string.phone_storage) else value
                    contact_messenger_action_account.text = " (ID:" + key.source + ")"
                    contact_messenger_action_holder.setOnClickListener {
                        if (contact_messenger_action_account.isVisible()) contact_messenger_action_account.beGone()
                        else contact_messenger_action_account.beVisible()
                    }
                    contact_messenger_action_number.setTextColor(getProperPrimaryColor())
                    contact_messengers_actions_holder.addView(this)

                    val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                        contact_messengers_actions_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_messengers_actions_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_messenger_action_message_icon.background.setTint(getProperTextColor())
                    contact_messenger_action_message_icon.background.alpha = 40
                    contact_messenger_action_message_icon.setColorFilter(getProperPrimaryColor())
                    contact_messenger_action_call_icon.background.setTint(getProperTextColor())
                    contact_messenger_action_call_icon.background.alpha = 40
                    contact_messenger_action_call_icon.setColorFilter(getProperPrimaryColor())
                    contact_messenger_action_video_icon.background.setTint(getProperTextColor())
                    contact_messenger_action_video_icon.background.alpha = 40
                    contact_messenger_action_video_icon.setColorFilter(getProperPrimaryColor())
                    contact_messenger_action_holder.divider_contact_messenger_action.setBackgroundColor(getProperTextColor())
                    contact_messenger_action_holder.divider_contact_messenger_action.isGone = isLastItem == key

                    if (value.toLowerCase() == WHATSAPP) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.toLowerCase() == SIGNAL) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible() //hide not messengers
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.toLowerCase() == VIBER) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            contact_messenger_action_number.beGoneIf(contact!!.phoneNumbers.size > 1 && messageActions.isEmpty())
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.toLowerCase() == TELEGRAM) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(messageActions)
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(callActions)
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(videoActions)
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.toLowerCase() == THREEMA) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }
                }
            }
            //contact_messengers_actions_holder.beVisible()
        } else {
            contact_messengers_actions_holder.beGone()
        }
    }

    private fun setupVideoCallActions() {
        var sources = HashMap<Contact, String>()
        sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                sources[it] = getPublicContactSourceSync(it.source, contactSources)
            }
        }

        if (sources.size > 1) {
            sources = sources.toList().sortedBy { (key, value) -> value.toLowerCase() }.toMap() as LinkedHashMap<Contact, String>
        }

        val videoActions = arrayListOf<SocialAction>()
        for ((key, value) in sources) {

            if (value.toLowerCase() == WHATSAPP) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val whatsappVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(whatsappVideoActions)
                }
            }

            if (value.toLowerCase() == SIGNAL) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val signalVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(signalVideoActions)
                }
            }

            if (value.toLowerCase() == VIBER) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val viberVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(viberVideoActions)
                }
            }

            if (value.toLowerCase() == TELEGRAM) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val telegramVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(telegramVideoActions)
                }
            }

            if (value.toLowerCase() == THREEMA) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val threemaVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(threemaVideoActions)
                }
            }
        }

        //contact_send_sms.isEnabled = contact!!.phoneNumbers.isNotEmpty()
        contact_send_sms.alpha = if (contact!!.phoneNumbers.isNotEmpty()) 1f else 0.5f
        //contact_start_call.isEnabled = contact!!.phoneNumbers.isNotEmpty()
        contact_start_call.alpha = if (contact!!.phoneNumbers.isNotEmpty()) 1f else 0.5f
        //contact_video_call.isEnabled = videoActions.isNotEmpty()
        contact_video_call.alpha = if (videoActions.isNotEmpty()) 1f else 0.5f
        //contact_send_email.isEnabled = contact!!.emails.isNotEmpty()
        contact_send_email.alpha = if (contact!!.emails.isNotEmpty()) 1f else 0.5f

        if (contact!!.phoneNumbers.isNotEmpty()) contact_send_sms.setOnClickListener { trySendSMS() }
        if (contact!!.phoneNumbers.isNotEmpty()) contact_start_call.setOnClickListener { tryStartCall(contact!!) }
        if (videoActions.isNotEmpty()) contact_video_call.setOnClickListener { showVideoCallAction(videoActions) }
        if (contact!!.emails.isNotEmpty()) contact_send_email.setOnClickListener { trySendEmail() }

        contact_send_sms.setOnLongClickListener { toast(R.string.send_sms); true; }
        contact_start_call.setOnLongClickListener { toast(R.string.call_contact); true; }
        contact_video_call.setOnLongClickListener { toast(R.string.video_call); true; }
        contact_send_email.setOnLongClickListener { toast(R.string.send_email); true; }
    }

    // a contact cannot have different emails per contact source. Such contacts are handled as separate ones, not duplicates of each other
    private fun setupEmails() {
        contact_emails_holder.removeAllViews()
        val emails = contact!!.emails
        if (emails.isNotEmpty() && showFields and SHOW_EMAILS_FIELD != 0) {
            val isFirstItem = emails.first()
            val isLastItem = emails.last()
            emails.forEach {
                layoutInflater.inflate(R.layout.item_view_email, contact_emails_holder, false).apply {
                    val email = it
                    contact_emails_holder.addView(this)
                    contact_email.text = email.value
                    contact_email_type.text = getEmailTypeText(email.type, email.label)
                    copyOnLongClick(email.value)

                    setOnClickListener {
                        sendEmailIntent(email.value)
                    }

                    val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                        contact_emails_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_emails_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_email_holder.contact_email_icon.isVisible = isFirstItem == email
                    contact_email_holder.contact_email_icon.setColorFilter(getProperTextColor())
                    contact_email_holder.divider_contact_email.setBackgroundColor(getProperTextColor())
                    contact_email_holder.divider_contact_email.isGone = isLastItem == email
                    contact_email_holder.contact_email.setTextColor(getProperPrimaryColor())
                }
            }
            contact_emails_holder.beVisible()
        } else {
            contact_emails_holder.beGone()
        }
    }

    private fun setupAddresses() {
        var addresses = contact!!.addresses.toMutableSet() as LinkedHashSet<Address>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                addresses.addAll(it.addresses)
            }
        }

        addresses = addresses.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Address>
        fullContact!!.addresses = addresses.toMutableList() as ArrayList<Address>
        contact_addresses_holder.removeAllViews()

        if (addresses.isNotEmpty() && showFields and SHOW_ADDRESSES_FIELD != 0) {
            val isFirstItem = addresses.first()
            val isLastItem = addresses.last()
            addresses.forEach {
                layoutInflater.inflate(R.layout.item_view_address, contact_addresses_holder, false).apply {
                    val address = it
                    contact_addresses_holder.addView(this)
                    contact_address.text = address.value
                    contact_address_type.text = getAddressTypeText(address.type, address.label)
                    copyOnLongClick(address.value)

                    setOnClickListener {
                        sendAddressIntent(address.value)
                    }

                    val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                        contact_addresses_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_addresses_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_address_holder.contact_address_icon.isVisible = isFirstItem == address
                    contact_address_holder.contact_address_icon.setColorFilter(getProperTextColor())
                    contact_address_holder.divider_contact_address.setBackgroundColor(getProperTextColor())
                    contact_address_holder.divider_contact_address.isGone = isLastItem == address
                    contact_address_holder.contact_address.setTextColor(getProperPrimaryColor())
                }
            }
            contact_addresses_holder.beVisible()
        } else {
            contact_addresses_holder.beGone()
        }
    }

    private fun setupIMs() {
        var IMs = contact!!.IMs.toMutableSet() as LinkedHashSet<IM>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                IMs.addAll(it.IMs)
            }
        }

        IMs = IMs.sortedBy { it.type }.toMutableSet() as LinkedHashSet<IM>
        fullContact!!.IMs = IMs.toMutableList() as ArrayList<IM>
        contact_ims_holder.removeAllViews()

        if (IMs.isNotEmpty() && showFields and SHOW_IMS_FIELD != 0) {
            val isFirstItem = IMs.first()
            val isLastItem = IMs.last()
            IMs.forEach {
                layoutInflater.inflate(R.layout.item_view_im, contact_ims_holder, false).apply {
                    val IM = it
                    contact_ims_holder.addView(this)
                    contact_im.text = IM.value
                    contact_im_type.text = getIMTypeText(IM.type, IM.label)
                    copyOnLongClick(IM.value)

                    val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                        contact_ims_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_ims_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_im_holder.contact_im_icon.isVisible = isFirstItem == IM
                    contact_im_holder.contact_im_icon.setColorFilter(getProperTextColor())
                    contact_im_holder.divider_contact_im.setBackgroundColor(getProperTextColor())
                    contact_im_holder.divider_contact_im.isGone = isLastItem == IM
                    contact_im_holder.contact_im.setTextColor(getProperPrimaryColor())
                }
            }
            contact_ims_holder.beVisible()
        } else {
            contact_ims_holder.beGone()
        }
    }

    private fun setupEvents() {
        var events = contact!!.events.toMutableSet() as LinkedHashSet<Event>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                events.addAll(it.events)
            }
        }

        events = events.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Event>
        fullContact!!.events = events.toMutableList() as ArrayList<Event>
        contact_events_holder.removeAllViews()

        if (events.isNotEmpty() && showFields and SHOW_EVENTS_FIELD != 0) {
            val isFirstItem = events.first()
            val isLastItem = events.last()
            events.forEach {
                layoutInflater.inflate(R.layout.item_view_event, contact_events_holder, false).apply {
                    val event = it
                    contact_events_holder.addView(this)
                    it.value.getDateTimeFromDateString(true, contact_event)
                    contact_event_type.setText(getEventTextId(it.type))
                    copyOnLongClick(it.value)

                    val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                        contact_events_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_events_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_event_holder.contact_event_icon.isVisible = isFirstItem == event
                    contact_event_holder.contact_event_icon.setColorFilter(getProperTextColor())
                    contact_event_holder.divider_contact_event.setBackgroundColor(getProperTextColor())
                    contact_event_holder.divider_contact_event.isGone = isLastItem == event
                    contact_event_holder.contact_event.setTextColor(getProperPrimaryColor())
                }
            }
            contact_events_holder.beVisible()
        } else {
            contact_events_holder.beGone()
        }
    }

    private fun setupWebsites() {
        var websites = contact!!.websites.toMutableSet() as LinkedHashSet<String>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                websites.addAll(it.websites)
            }
        }

        websites = websites.sorted().toMutableSet() as LinkedHashSet<String>
        fullContact!!.websites = websites.toMutableList() as ArrayList<String>
        contact_websites_holder.removeAllViews()

        if (websites.isNotEmpty() && showFields and SHOW_WEBSITES_FIELD != 0) {
            val isLastItem = websites.last()
            layoutInflater.inflate(R.layout.item_view_header, contact_websites_holder, false).apply {
                contact_websites_holder.addView(this)
                contact_header_holder.contact_header_type.text = getString(R.string.websites)
                contact_header_holder.contact_header_icon.setImageResource(R.drawable.ic_link_vector)
                contact_header_holder.contact_header_icon.setColorFilter(getProperTextColor())
            }
            websites.forEach {
                val url = it
                layoutInflater.inflate(R.layout.item_website, contact_websites_holder, false).apply {
                    val website = it
                    contact_websites_holder.addView(this)
                    contact_website.text = url
                    copyOnLongClick(url)

                    setOnClickListener {
                        openWebsiteIntent(url)
                    }

                    val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                        contact_websites_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_websites_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_website_holder.divider_contact_website.setBackgroundColor(getProperTextColor())
                    contact_website_holder.divider_contact_website.isGone = isLastItem == website
                    contact_website_holder.contact_website.setTextColor(getProperPrimaryColor())
                }
            }
            contact_websites_holder.beVisible()
        } else {
            contact_websites_holder.beGone()
        }
    }

    private fun setupGroups() {
        var groups = contact!!.groups.toMutableSet() as LinkedHashSet<Group>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                groups.addAll(it.groups)
            }
        }

        groups = groups.sortedBy { it.title }.toMutableSet() as LinkedHashSet<Group>
        fullContact!!.groups = groups.toMutableList() as ArrayList<Group>
        contact_groups_holder.removeAllViews()

        if (groups.isNotEmpty() && showFields and SHOW_GROUPS_FIELD != 0) {
            val isLastItem = groups.last()
            layoutInflater.inflate(R.layout.item_view_header, contact_groups_holder, false).apply {
                contact_groups_holder.addView(this)
                contact_header_holder.contact_header_type.text = getString(R.string.groups)
                contact_header_holder.contact_header_icon.setImageResource(R.drawable.ic_people_rounded)
                contact_header_holder.contact_header_icon.setColorFilter(getProperTextColor())
            }
            groups.forEach {
                layoutInflater.inflate(R.layout.item_view_group, contact_groups_holder, false).apply {
                    val group = it
                    contact_groups_holder.addView(this)
                    contact_group.text = group.title
                    copyOnLongClick(group.title)

                    val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                        contact_groups_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_groups_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_group_holder.divider_contact_group.setBackgroundColor(getProperTextColor())
                    contact_group_holder.divider_contact_group.isGone = isLastItem == group
                    contact_group_holder.contact_group.setTextColor(getProperPrimaryColor())
                }
            }
            contact_groups_holder.beVisible()
        } else {
            contact_groups_holder.beGone()
        }
    }

    private fun setupContactSources() {
        contact_sources_holder.removeAllViews()
        if (showFields and SHOW_CONTACT_SOURCE_FIELD != 0) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            if (mergeDuplicate) {
                duplicateContacts.forEach {
                    sources[it] = getPublicContactSourceSync(it.source, contactSources)
                }
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (key, value) -> value.toLowerCase() }.toMap() as LinkedHashMap<Contact, String>
            }

            layoutInflater.inflate(R.layout.item_view_header, contact_sources_holder, false).apply {
                contact_sources_holder.addView(this)
                contact_header_holder.contact_header_type.text = getString(R.string.contact_source)
                contact_header_holder.contact_header_icon.setImageResource(R.drawable.ic_source_vector)
                contact_header_holder.contact_header_icon.setColorFilter(getProperTextColor())
            }

            for ((key, value) in sources) {
                val isLastItem = sources.keys.last()
                layoutInflater.inflate(R.layout.item_view_contact_source, contact_sources_holder, false).apply {
                    contact_source.text = if (value == "") getString(R.string.phone_storage) else value
                    contact_source.setTextColor(getProperPrimaryColor())
                    contact_source.copyOnLongClick(value)
                    contact_sources_holder.addView(this)

                    contact_source.setOnClickListener {
                        launchEditContact(key)
                    }

                    val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                        contact_sources_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_sources_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_source_holder.divider_contact_source.setBackgroundColor(getProperTextColor())
                    contact_source_holder.divider_contact_source.isGone = isLastItem == key

                    if (value.toLowerCase() == WHATSAPP) {
                        contact_source_image.setImageDrawable(getPackageDrawable(WHATSAPP_PACKAGE))
                        contact_source_image.beVisible()
                        contact_source_image.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.toLowerCase() == SIGNAL) {
                        contact_source_image.setImageDrawable(getPackageDrawable(SIGNAL_PACKAGE))
                        contact_source_image.beVisible()
                        contact_source_image.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.toLowerCase() == VIBER) {
                        contact_source_image.setImageDrawable(getPackageDrawable(VIBER_PACKAGE))
                        contact_source_image.beVisible()
                        contact_source_image.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.toLowerCase() == TELEGRAM) {
                        contact_source_image.setImageDrawable(getPackageDrawable(TELEGRAM_PACKAGE))
                        contact_source_image.beVisible()
                        contact_source_image.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.toLowerCase() == THREEMA) {
                        contact_source_image.setImageDrawable(getPackageDrawable(THREEMA_PACKAGE))
                        contact_source_image.beVisible()
                        contact_source_image.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }
                }
            }
            contact_sources_holder.beVisible()
        } else {
            contact_sources_holder.beGone()
        }
    }

    private fun setupNotes() {
        val notes = contact!!.notes
        contact_notes.removeAllViews()
        if (notes.isNotEmpty() && showFields and SHOW_NOTES_FIELD != 0) {
            layoutInflater.inflate(R.layout.item_view_header, contact_notes, false).apply {
                contact_notes.addView(this)
                contact_header_holder.contact_header_type.text = getString(R.string.notes)
                contact_header_holder.contact_header_icon.setImageResource(R.drawable.ic_article_vector)
                contact_header_holder.contact_header_icon.setColorFilter(getProperTextColor())
            }
            layoutInflater.inflate(R.layout.item_view_note, contact_notes, false).apply {
                contact_notes.addView(this)
                contact_note_holder.contact_note.text = notes
                copyOnLongClick(notes)

                val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
                if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                    contact_notes.background = whiteButton
                    val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                    contact_notes.setPadding(padding, padding ,padding ,padding)
                }
            }
            contact_notes.beVisible()
        } else {
            contact_notes.beGone()
        }
    }

    private fun setupRingtone() {
        if (showFields and SHOW_RINGTONE_FIELD != 0) {
            contact_ringtone_holder.beVisible()

            val whiteButton = AppCompatResources.getDrawable(this@ViewContactActivity, R.drawable.call_history_button_white)
            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_ringtone_holder.background = whiteButton
                val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                contact_ringtone_holder.setPadding(padding, padding ,padding ,padding)
            }
            contact_ringtone_chevron.setColorFilter(getProperTextColor())
            contact_ringtone.setTextColor(getProperPrimaryColor())

            val ringtone = contact!!.ringtone
            if (ringtone?.isEmpty() == true) {
                contact_ringtone.text = getString(R.string.no_sound)
            } else if (ringtone?.isNotEmpty() == true && ringtone != getDefaultRingtoneUri().toString()) {
                if (ringtone == SILENT) {
                    contact_ringtone.text = getString(R.string.no_sound)
                } else {
                    systemRingtoneSelected(Uri.parse(ringtone))
                }
            } else {
                contact_ringtone_holder.beGone()
                return
            }

            contact_ringtone_press.copyOnLongClick(contact_ringtone.text.toString())

            contact_ringtone_press.setOnClickListener {
                val ringtonePickerIntent = getRingtonePickerIntent()
                try {
                    startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
                } catch (e: Exception) {
                    val currentRingtone = contact!!.ringtone ?: getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE).uri
                    SelectAlarmSoundDialog(this@ViewContactActivity,
                        currentRingtone,
                        AudioManager.STREAM_RING,
                        PICK_RINGTONE_INTENT_ID,
                        RingtoneManager.TYPE_RINGTONE,
                        true,
                        onAlarmPicked = {
                            contact_ringtone.text = it?.title
                            ringtoneUpdated(it?.uri)
                        },
                        onAlarmSoundDeleted = {}
                    )
                }
            }
        } else {
            contact_ringtone_holder.beGone()
        }
    }

    private fun setupOrganization() {
        val organization = contact!!.organization
        if (organization.isNotEmpty() && showFields and SHOW_ORGANIZATION_FIELD != 0) {
            contact_organization_company.setTextColor(getProperTextColor())
            contact_organization_job_position.setTextColor(getProperTextColor())

            contact_organization_company.text = organization.company
            contact_organization_job_position.text = organization.jobPosition
            //contact_organization_image.beGoneIf(organization.isEmpty())
            contact_organization_company.beGoneIf(organization.company.isEmpty())
            contact_organization_job_position.beGoneIf(organization.jobPosition.isEmpty())
            contact_organization_company.copyOnLongClick(contact_organization_company.value)
            contact_organization_job_position.copyOnLongClick(contact_organization_job_position.value)

            /*if (organization.company.isEmpty() && organization.jobPosition.isNotEmpty()) {
                (contact_organization_image.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ALIGN_TOP, contact_organization_job_position.id)
            }*/
        } else {
            contact_organization_company.beGone()
            contact_organization_job_position.beGone()
        }
    }

    private fun showSocialActions(contactId: Int) {
        ensureBackgroundThread {
            val actions = getSocialActions(contactId)
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    ChooseSocialDialog(this@ViewContactActivity, actions) { action ->
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (e: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showMessengerAction(actions: ArrayList<SocialAction>) {
        ensureBackgroundThread {
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    if (actions.size > 1) {
                        ChooseSocialDialog(this@ViewContactActivity, actions) { action ->
                            Intent(Intent.ACTION_VIEW).apply {
                                val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                                setDataAndType(uri, action.mimetype)
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                                try {
                                    startActivity(this)
                                } catch (e: SecurityException) {
                                    handlePermission(PERMISSION_CALL_PHONE) { success ->
                                        if (success) {
                                            startActivity(this)
                                        } else {
                                            toast(R.string.no_phone_call_permission)
                                        }
                                    }
                                } catch (e: ActivityNotFoundException) {
                                    toast(R.string.no_app_found)
                                } catch (e: Exception) {
                                    showErrorToast(e)
                                }
                            }
                        }
                    } else {
                        val action = actions.first()
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (e: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showVideoCallAction(actions: ArrayList<SocialAction>) {
        ensureBackgroundThread {
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    ChooseSocialDialog(this@ViewContactActivity, actions) { action ->
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (e: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startMessengerAction(action: SocialAction) {
        ensureBackgroundThread {
            Intent(Intent.ACTION_VIEW).apply {
                val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                setDataAndType(uri, action.mimetype)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                try {
                    startActivity(this)
                } catch (e: SecurityException) {
                    handlePermission(PERMISSION_CALL_PHONE) { success ->
                        if (success) {
                            startActivity(this)
                        } else {
                            toast(R.string.no_phone_call_permission)
                        }
                    }
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.no_app_found)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    override fun customRingtoneSelected(ringtonePath: String) {
        contact_ringtone.text = ringtonePath.getFilenameFromPath()
        ringtoneUpdated(ringtonePath)
    }

    override fun systemRingtoneSelected(uri: Uri?) {
        val contactRingtone = RingtoneManager.getRingtone(this, uri)
        contact_ringtone.text = contactRingtone.getTitle(this)
        ringtoneUpdated(uri?.toString() ?: "")
    }

    private fun ringtoneUpdated(path: String?) {
        contact!!.ringtone = path

        ensureBackgroundThread {
            if (contact!!.isPrivate()) {
                LocalContactsHelper(this).updateRingtone(contact!!.contactId, path ?: "")
            } else {
                ContactsHelper(this).updateRingtone(contact!!.contactId.toString(), path ?: "")
            }
        }
    }

    private fun getDuplicateContacts(callback: () -> Unit) {
        ContactsHelper(this).getDuplicatesOfContact(contact!!, false) { contacts ->
            ensureBackgroundThread {
                duplicateContacts.clear()
                val displayContactSources = getVisibleContactSources()
                contacts.filter { displayContactSources.contains(it.source) }.forEach {
                    val duplicate = ContactsHelper(this).getContactWithId(it.id, it.isPrivate())
                    if (duplicate != null) {
                        duplicateContacts.add(duplicate)
                    }
                }

                runOnUiThread {
                    callback()
                }
            }
        }
    }

    private fun deleteContactFromAllSources() {
        val addition = if (contact_sources_holder.childCount > 1) {
            "\n\n${getString(R.string.delete_from_all_sources)}"
        } else {
            ""
        }

        val message = "${getString(R.string.proceed_with_deletion)}$addition"
        ConfirmationDialog(this, message) {
            if (contact != null) {
                ContactsHelper(this).deleteContact(contact!!, true) {
                    finish()
                }
            }
        }
    }

    private fun getStarDrawable(on: Boolean) = resources.getDrawable(if (on) R.drawable.ic_star_vector else R.drawable.ic_star_outline_vector)

    private fun hideBigContactPhoto() {
        contact_photo_big.animate().alpha(0f).withEndAction { contact_photo_big.beGone() }.start()
    }

    private fun View.copyOnLongClick(value: String) {
        setOnLongClickListener {
            copyToClipboard(value)
            true
        }
    }
}
