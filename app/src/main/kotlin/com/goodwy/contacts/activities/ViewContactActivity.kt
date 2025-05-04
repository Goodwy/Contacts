package com.goodwy.contacts.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.BidiFormatter
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestOptions
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.SelectAlarmSoundDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.contacts.*
import com.goodwy.contacts.R
import com.goodwy.contacts.databinding.*
import com.goodwy.contacts.dialogs.ChooseSocialDialog
import com.goodwy.contacts.dialogs.ManageVisibleFieldsDialog
import com.goodwy.contacts.extensions.*
import com.goodwy.contacts.helpers.*
import java.util.Locale
import kotlin.math.abs
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri

class ViewContactActivity : ContactActivity() {
    private var isViewIntent = false
    private var wasEditLaunched = false
    private var duplicateContacts = ArrayList<Contact>()
    private var contactSources = ArrayList<ContactSource>()
    private var showFields = 0
    private var fullContact: Contact? = null    // contact with all fields filled from duplicates
    private var duplicateInitialized = false
    private val mergeDuplicate: Boolean get() = config.mergeDuplicateContacts
    private val binding by viewBinding(ActivityViewContactBinding::inflate)
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFFEBEBEB.toInt()
    private var buttonBg = white

    companion object {
        private const val COMPARABLE_PHONE_NUMBER_LENGTH = 9
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (checkAppSideloading()) {
            return
        }

        updateMaterialActivityViews(binding.contactWrapper, binding.contactHolder, useTransparentNavigation = false, useTopSearchMenu = false)
        setWindowTransparency(true) { _, _, leftNavigationBarSize, rightNavigationBarSize ->
            binding.contactWrapper.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
            updateNavigationBarColor(getProperBackgroundColor())
        }

        showFields = config.showContactFields
        binding.contactWrapper.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setupMenu()
        initButton()
    }

    override fun onResume() {
        super.onResume()
        buttonBg = if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) white else getBottomNavigationBackgroundColor()

        isViewIntent = intent.action == ContactsContract.QuickContact.ACTION_QUICK_CONTACT || intent.action == Intent.ACTION_VIEW
        if (isViewIntent) {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    ensureBackgroundThread {
                        initContact()
                    }
                } else {
                    toast(com.goodwy.commons.R.string.no_contacts_permission)
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

    private fun initButton() {
        val properPrimaryColor = getProperPrimaryColor()

        var drawableSMS = AppCompatResources.getDrawable(this, R.drawable.ic_messages)
        drawableSMS = DrawableCompat.wrap(drawableSMS!!)
        DrawableCompat.setTint(drawableSMS, properPrimaryColor)
        DrawableCompat.setTintMode(drawableSMS, PorterDuff.Mode.SRC_IN)
        binding.contactSendSms.setCompoundDrawablesWithIntrinsicBounds(null, drawableSMS, null, null)
        binding.contactSendSms.setTextColor(properPrimaryColor)

        var drawableCall = AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_phone_vector)
        drawableCall = DrawableCompat.wrap(drawableCall!!)
        DrawableCompat.setTint(drawableCall, properPrimaryColor)
        DrawableCompat.setTintMode(drawableCall, PorterDuff.Mode.SRC_IN)
        binding.contactStartCall.setCompoundDrawablesWithIntrinsicBounds(null, drawableCall, null, null)
        binding.contactStartCall.setTextColor(properPrimaryColor)

        var drawableVideoCall = AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_videocam_vector)
        drawableVideoCall = DrawableCompat.wrap(drawableVideoCall!!)
        DrawableCompat.setTint(drawableVideoCall, properPrimaryColor)
        DrawableCompat.setTintMode(drawableVideoCall, PorterDuff.Mode.SRC_IN)
        binding.contactVideoCall.setCompoundDrawablesWithIntrinsicBounds(null, drawableVideoCall, null, null)
        binding.contactVideoCall.setTextColor(properPrimaryColor)

        var drawableMail = AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_mail_vector)
        drawableMail = DrawableCompat.wrap(drawableMail!!)
        DrawableCompat.setTint(drawableMail, properPrimaryColor)
        DrawableCompat.setTintMode(drawableMail, PorterDuff.Mode.SRC_IN)
        binding.contactSendEmail.setCompoundDrawablesWithIntrinsicBounds(null, drawableMail, null, null)
        binding.contactSendEmail.setTextColor(properPrimaryColor)
    }

    private fun updateColors() {
        val properBackgroundColor = getProperBackgroundColor()

        if (baseConfig.backgroundColor == white) {
            val colorToWhite = 0xFFf2f2f6.toInt()
            supportActionBar?.setBackgroundDrawable(colorToWhite.toDrawable())
            window.decorView.setBackgroundColor(colorToWhite)
            window.statusBarColor = colorToWhite
            //window.navigationBarColor = colorToWhite
            binding.contactAppbar.setBackgroundColor(colorToWhite)
        } else {
            window.decorView.setBackgroundColor(properBackgroundColor)
            binding.contactAppbar.setBackgroundColor(properBackgroundColor)
        }

        binding.apply {
            arrayOf(
                contactSendSms, contactStartCall, contactVideoCall, contactSendEmail,
            ).forEach {
                it.background.setTint(buttonBg)
            }
        }
    }

    override fun onBackPressed() {
        if (binding.contactPhotoBig.alpha == 1f) {
            hideBigContactPhoto()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupMenu() {
        val contrastColor = getProperBackgroundColor().getContrastColor()
        val primaryColor = getProperPrimaryColor()
        val iconColor = if (baseConfig.topAppBarColorIcon) primaryColor else contrastColor
        //(contact_appbar.layoutParams as CoordinatorLayout.LayoutParams).topMargin = statusBarHeight
        (binding.contactWrapper.layoutParams as FrameLayout.LayoutParams).topMargin = statusBarHeight
        binding.contactToolbar.overflowIcon = resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.ic_three_dots_vector, iconColor)
        binding.contactToolbar.menu.apply {
            updateMenuItemColors(this)
            findItem(R.id.favorite).setOnMenuItemClickListener {
                val newIsStarred = if ((contact?.starred ?: 0) == 1) 0 else 1
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
                favoriteIcon.setTint(iconColor)
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
                deleteContactWithMergeLogic()
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

        binding.contactToolbar.setNavigationIconTint(iconColor)
        binding.contactToolbar.setNavigationOnClickListener {
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
                    toast(com.goodwy.commons.R.string.unknown_error_occurred)
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

        binding.contactScrollview.beVisible()
        setupViewContact()

        if (contact!!.photoUri.isEmpty() && contact!!.photo == null) {
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
            binding.topDetails.contactPhoto.setImageDrawable(placeholderImage)
        } else {
            updateContactPhoto(contact!!.photoUri, binding.topDetails.contactPhoto, binding.contactPhotoBottomShadow, contact!!.photo)
            val optionsBig = RequestOptions()
                //.transform(FitCenter(), RoundedCorners(resources.getDimension(R.dimen.normal_margin).toInt()))
                .transform(FitCenter())

            Glide.with(this)
                .load(contact!!.photo ?: currentContactPhotoPath)
                .apply(optionsBig)
                .into(binding.contactPhotoBig)

            binding.topDetails.contactPhoto.setOnClickListener {
                binding.contactPhotoBig.alpha = 0f
                binding.contactPhotoBig.beVisible()
                binding.contactPhotoBig.animate().alpha(1f).start()
            }

            binding.contactPhotoBig.setOnClickListener {
                hideBigContactPhoto()
            }
        }

        updateTextColors(binding.contactScrollview)
        binding.contactToolbar.menu.findItem(R.id.open_with).isVisible = contact?.isPrivate() == false
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
        setupRelations()
        setupWebsites()
        setupGroups()
        setupContactSources()
        setupNotes()
        setupRingtone()
        setupOrganization()
        updateTextColors(binding.contactScrollview)
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
        val contrastColor = getProperBackgroundColor().getContrastColor()
        val primaryColor = getProperPrimaryColor()
        val iconColor = if (baseConfig.topAppBarColorIcon) primaryColor else contrastColor
        val favoriteIcon = getStarDrawable(contact!!.starred == 1)
        favoriteIcon.setTint(iconColor)
        binding.contactToolbar.menu.findItem(R.id.favorite).icon = favoriteIcon

        binding.contactToggleFavorite.apply {
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

        binding.topDetails.contactName.text = displayName
        binding.topDetails.contactName.setTextColor(getProperTextColor())
        binding.topDetails.contactName.copyOnLongClick(displayName)
        binding.topDetails.contactName.beVisibleIf(displayName.isNotEmpty() && showNameFields)

        /*collapsingToolbar.setExpandedTitleColor(getProperTextColor())
        collapsingToolbar.setCollapsedTitleTextColor(getProperTextColor())
        collapsingToolbar.title = displayName
        collapsingToolbar.copyOnLongClick(displayName)*/
    }

    //This converts the string to RTL and left-aligns it if there is at least one RTL-language character in the string, and returns to LTR otherwise.
    fun formatterUnicodeWrap(text: String): String {
        return BidiFormatter.getInstance().unicodeWrap(text)
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

            if (defaultNumbers.size > 1 && defaultNumbers.distinctBy { it.normalizedNumber }.size > 1) {
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
        binding.contactNumbersHolder.removeAllViews()

        if (phoneNumbers.isNotEmpty() && showFields and SHOW_PHONE_NUMBERS_FIELD != 0) {
            val isFirstItem = phoneNumbers.first()
            val isLastItem = phoneNumbers.last()
            phoneNumbers.forEach { phoneNumber ->
                ItemViewPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false).apply {

                    binding.contactNumbersHolder.addView(root)
                    if (config.formatPhoneNumbers) {
                        contactNumber.text = formatterUnicodeWrap(phoneNumber.value.formatPhoneNumber())
                    } else {
                        contactNumber.text = formatterUnicodeWrap(phoneNumber.value)
                    }
                    contactNumberType.text = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
                    root.copyOnLongClick(phoneNumber.value)

                    root.setOnClickListener {
                        if (config.showCallConfirmation) {
                            CallConfirmationDialog(this@ViewContactActivity, phoneNumber.value) {
                                startCallIntent(phoneNumber.value)
                            }
                        } else {
                            startCallIntent(phoneNumber.value)
                        }
                    }

                    binding.contactNumbersHolder.background .setTint(buttonBg)

                    val getProperTextColor = getProperTextColor()
                    defaultToggleIcon.isVisible = phoneNumber.isPrimary
                    defaultToggleIcon.setColorFilter(getProperTextColor)
                    contactNumberIcon.isVisible = isFirstItem == phoneNumber
                    contactNumberIcon.setColorFilter(getProperTextColor)
                    dividerPhoneNumber.setBackgroundColor(getProperTextColor)
                    dividerPhoneNumber.isGone = isLastItem == phoneNumber
                    contactNumber.setTextColor(getProperPrimaryColor())
                }
            }
            binding.contactNumbersHolder.beVisible()
        } else {
            binding.contactNumbersHolder.beGone()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupMessengersActions() {
        binding.contactMessengersActionsHolder.removeAllViews()
        if (showFields and SHOW_MESSENGERS_ACTIONS_FIELD != 0) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            if (mergeDuplicate) {
                duplicateContacts.forEach {
                    sources[it] = getPublicContactSourceSync(it.source, contactSources)
                }
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (key, value) -> value.lowercase(Locale.getDefault()) }.toMap() as LinkedHashMap<Contact, String>
            }

            for ((key, value) in sources) {
                val isLastItem = sources.keys.last()
                ItemViewMessengersActionsBinding.inflate(layoutInflater, binding.contactMessengersActionsHolder, false).apply {
                    contactMessengerActionName.text = if (value == "") getString(R.string.phone_storage) else value
                    contactMessengerActionAccount.text = " (ID:" + key.source + ")"
                    contactMessengerActionHolder.setOnClickListener {
                        if (contactMessengerActionAccount.isVisible()) contactMessengerActionAccount.beGone()
                        else contactMessengerActionAccount.beVisible()
                    }
                    val getProperPrimaryColor = getProperPrimaryColor()
                    val getProperTextColor = getProperTextColor()
                    contactMessengerActionName.setTextColor(getProperPrimaryColor)
                    contactMessengerActionNumber.setTextColor(getProperPrimaryColor)
                    binding.contactMessengersActionsHolder.addView(root)
                    binding.contactMessengersActionsHolder.background .setTint(buttonBg)

                    arrayOf(
                        contactMessengerActionMessageIcon, contactMessengerActionCallIcon, contactMessengerActionVideoIcon,
                    ).forEach {
                        it.background.setTint(getProperTextColor)
                        it.background.alpha = 40
                        it.setColorFilter(getProperPrimaryColor)
                    }

                    dividerContactMessengerAction.setBackgroundColor(getProperTextColor)
                    dividerContactMessengerAction.beGoneIf(isLastItem == key)

                    if (value.lowercase(Locale.getDefault()) == WHATSAPP) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) {
                                contactMessengerActionMessage.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(messageActions)
                                    }
                                }
                            }
                            if (callActions.isNotEmpty()) {
                                contactMessengerActionCall.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(callActions)
                                    }
                                }
                            }
                            if (videoActions.isNotEmpty()) {
                                contactMessengerActionVideo.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(videoActions)
                                    }
                                }
                            }
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == SIGNAL) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible() //hide not messengers
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) {
                                contactMessengerActionMessage.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(messageActions)
                                    }
                                }
                            }
                            if (callActions.isNotEmpty()) {
                                contactMessengerActionCall.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(callActions)
                                    }
                                }
                            }
                            if (videoActions.isNotEmpty()) {
                                contactMessengerActionVideo.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(videoActions)
                                    }
                                }
                            }
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == VIBER) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            contactMessengerActionNumber.beGoneIf(contact!!.phoneNumbers.size > 1 && messageActions.isEmpty())
                            if (messageActions.isNotEmpty()) {
                                contactMessengerActionMessage.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(messageActions)
                                    }
                                }
                            }
                            if (callActions.isNotEmpty()) {
                                contactMessengerActionCall.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(callActions)
                                    }
                                }
                            }
                            if (videoActions.isNotEmpty()) {
                                contactMessengerActionVideo.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(videoActions)
                                    }
                                }
                            }
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == TELEGRAM) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) {
                                contactMessengerActionMessage.apply {
                                    beVisible()
                                    setOnClickListener {
                                        //startMessengerAction(messageActions)
                                        showMessengerAction(messageActions)
                                    }
                                }
                            }
                            if (callActions.isNotEmpty()) {
                                contactMessengerActionCall.apply {
                                    beVisible()
                                    setOnClickListener {
                                        //startMessengerAction(callActions)
                                        showMessengerAction(callActions)
                                    }
                                }
                            }
                            if (videoActions.isNotEmpty()) {
                                contactMessengerActionVideo.apply {
                                    beVisible()
                                    setOnClickListener {
                                        //startMessengerAction(videoActions)
                                        showMessengerAction(videoActions)
                                    }
                                }
                            }
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == THREEMA) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) {
                                contactMessengerActionMessage.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(messageActions)
                                    }
                                }
                            }
                            if (callActions.isNotEmpty()) {
                                contactMessengerActionCall.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(callActions)
                                    }
                                }
                            }
                            if (videoActions.isNotEmpty()) {
                                contactMessengerActionVideo.apply {
                                    beVisible()
                                    setOnClickListener {
                                        showMessengerAction(videoActions)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            //binding.contactMessengersActionsHolder.beVisible()
        } else {
            binding.contactMessengersActionsHolder.beGone()
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
            sources = sources.toList().sortedBy { (key, value) -> value.lowercase(Locale.getDefault()) }.toMap() as LinkedHashMap<Contact, String>
        }

        val videoActions = arrayListOf<SocialAction>()
        for ((key, value) in sources) {

            if (value.lowercase(Locale.getDefault()) == WHATSAPP) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val whatsappVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(whatsappVideoActions)
                }
            }

            if (value.lowercase(Locale.getDefault()) == SIGNAL) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val signalVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(signalVideoActions)
                }
            }

            if (value.lowercase(Locale.getDefault()) == VIBER) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val viberVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(viberVideoActions)
                }
            }

            if (value.lowercase(Locale.getDefault()) == TELEGRAM) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val telegramVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(telegramVideoActions)
                }
            }

            if (value.lowercase(Locale.getDefault()) == THREEMA) {
                val actions = getSocialActions(key.id)
                if (actions.firstOrNull() != null) {
                    val threemaVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                    videoActions.addAll(threemaVideoActions)
                }
            }
        }

        binding.contactSendSms.alpha = if (contact!!.phoneNumbers.isNotEmpty()) 1f else 0.5f
        binding.contactStartCall.alpha = if (contact!!.phoneNumbers.isNotEmpty()) 1f else 0.5f
        binding.contactVideoCall.alpha = if (videoActions.isNotEmpty()) 1f else 0.5f
        binding.contactSendEmail.alpha = if (contact!!.emails.isNotEmpty()) 1f else 0.5f

        if (contact!!.phoneNumbers.isNotEmpty()) binding.contactSendSms.setOnClickListener { trySendSMSRecommendation() }
        if (contact!!.phoneNumbers.isNotEmpty()) binding.contactStartCall.setOnClickListener { tryStartCallRecommendation(contact!!) }
        if (videoActions.isNotEmpty()) binding.contactVideoCall.setOnClickListener { showVideoCallAction(videoActions) }
        if (contact!!.emails.isNotEmpty()) binding.contactSendEmail.setOnClickListener { trySendEmail() }

        binding.contactSendSms.setOnLongClickListener { toast(com.goodwy.commons.R.string.send_sms); true; }
        binding.contactStartCall.setOnLongClickListener { toast(R.string.call_contact); true; }
        binding.contactVideoCall.setOnLongClickListener { toast(com.goodwy.strings.R.string.video_call); true; }
        binding.contactSendEmail.setOnLongClickListener { toast(com.goodwy.commons.R.string.send_email); true; }
    }

    // a contact cannot have different emails per contact source. Such contacts are handled as separate ones, not duplicates of each other
    private fun setupEmails() {
        binding.contactEmailsHolder.removeAllViews()
        val emails = contact!!.emails
        if (emails.isNotEmpty() && showFields and SHOW_EMAILS_FIELD != 0) {
            val isFirstItem = emails.first()
            val isLastItem = emails.last()
            emails.forEach {
                ItemViewEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false).apply {
                    val email = it
                    binding.contactEmailsHolder.addView(root)
                    contactEmail.text = email.value
                    contactEmailType.text = getEmailTypeText(email.type, email.label)
                    root.copyOnLongClick(email.value)

                    root.setOnClickListener {
                        sendEmailIntent(email.value)
                    }

                    binding.contactEmailsHolder.background .setTint(buttonBg)

                    contactEmailIcon.isVisible = isFirstItem == email
                    contactEmailIcon.setColorFilter(getProperTextColor())
                    dividerContactEmail.setBackgroundColor(getProperTextColor())
                    dividerContactEmail.isGone = isLastItem == email
                    contactEmail.setTextColor(getProperPrimaryColor())
                }
            }
            binding.contactEmailsHolder.beVisible()
        } else {
            binding.contactEmailsHolder.beGone()
        }
    }

    private fun setupRelations() {
        val relations: ArrayList<ContactRelation> = contact!!.relations

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                relations.addAll(it.relations)
            }
        }

        relations.sortBy { it.type }
        fullContact!!.relations = relations

        binding.contactRelationsHolder.removeAllViews()

        if (relations.isNotEmpty() && showFields and SHOW_RELATIONS_FIELD != 0) {
            val isFirstItem = relations.first()
            val isLastItem = relations.last()
            relations.forEach {
                ItemViewRelationBinding.inflate(layoutInflater, binding.contactRelationsHolder, false).apply {
                    val relation = it
                    binding.contactRelationsHolder.addView(root)
                    contactRelation.text = relation.name
                    contactRelationType.text = getRelationTypeText(relation.type, relation.label)
                    root.copyOnLongClick(relation.name)

                    binding.contactRelationsHolder.background .setTint(buttonBg)

                    contactRelationIcon.isVisible = isFirstItem == relation
                    contactRelationIcon.setColorFilter(getProperTextColor())
                    dividerContactRelation.setBackgroundColor(getProperTextColor())
                    dividerContactRelation.isGone = isLastItem == relation
                    contactRelation.setTextColor(getProperPrimaryColor())
                }
            }
            binding.contactRelationsHolder.beVisible()
        } else {
            binding.contactRelationsHolder.beGone()
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
        binding.contactAddressesHolder.removeAllViews()

        if (addresses.isNotEmpty() && showFields and SHOW_ADDRESSES_FIELD != 0) {
            val isFirstItem = addresses.first()
            val isLastItem = addresses.last()
            addresses.forEach {
                ItemViewAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false).apply {
                    val address = it
                    binding.contactAddressesHolder.addView(root)
                    contactAddress.text = address.value
                    contactAddressType.text = getAddressTypeText(address.type, address.label)
                    root.copyOnLongClick(address.value)

                    root.setOnClickListener {
                        sendAddressIntent(address.value)
                    }

                    binding.contactAddressesHolder.background .setTint(buttonBg)

                    val getProperTextColor = getProperTextColor()
                    contactAddressIcon.isVisible = isFirstItem == address
                    contactAddressIcon.setColorFilter(getProperTextColor)
                    dividerContactAddress.setBackgroundColor(getProperTextColor)
                    dividerContactAddress.isGone = isLastItem == address
                    contactAddress.setTextColor(getProperPrimaryColor())
                }
            }
            binding.contactAddressesHolder.beVisible()
        } else {
            binding.contactAddressesHolder.beGone()
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
        binding.contactImsHolder.removeAllViews()

        if (IMs.isNotEmpty() && showFields and SHOW_IMS_FIELD != 0) {
            val isFirstItem = IMs.first()
            val isLastItem = IMs.last()
            IMs.forEach {
                ItemViewImBinding.inflate(layoutInflater, binding.contactImsHolder, false).apply {
                    val IM = it
                    binding.contactImsHolder.addView(root)
                    contactIm.text = IM.value
                    contactImType.text = getIMTypeText(IM.type, IM.label)
                    root.copyOnLongClick(IM.value)

                    binding.contactImsHolder.background .setTint(buttonBg)

                    contactImIcon.isVisible = isFirstItem == IM
                    contactImIcon.setColorFilter(getProperTextColor())
                    dividerContactIm.setBackgroundColor(getProperTextColor())
                    dividerContactIm.isGone = isLastItem == IM
                    contactIm.setTextColor(getProperPrimaryColor())
                }
            }
            binding.contactImsHolder.beVisible()
        } else {
            binding.contactImsHolder.beGone()
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
        binding.contactEventsHolder.removeAllViews()

        if (events.isNotEmpty() && showFields and SHOW_EVENTS_FIELD != 0) {
            val isFirstItem = events.first()
            val isLastItem = events.last()
            events.forEach {
                ItemViewEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false).apply {
                    binding.contactEventsHolder.addView(root)
                    it.value.getDateTimeFromDateString(true, contactEvent)
                    contactEventType.setText(getEventTextId(it.type))
                    root.copyOnLongClick(it.value)

                    binding.contactEventsHolder.background .setTint(buttonBg)

                    contactEventIcon.isVisible = isFirstItem == it
                    contactEventIcon.setColorFilter(getProperTextColor())
                    dividerContactEvent.setBackgroundColor(getProperTextColor())
                    dividerContactEvent.isGone = isLastItem == it
                    contactEvent.setTextColor(getProperPrimaryColor())
                }
            }
            binding.contactEventsHolder.beVisible()
        } else {
            binding.contactEventsHolder.beGone()
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
        binding.contactWebsitesHolder.removeAllViews()

        if (websites.isNotEmpty() && showFields and SHOW_WEBSITES_FIELD != 0) {
            val isLastItem = websites.last()
            ItemViewHeaderBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false).apply {
                binding.contactWebsitesHolder.addView(root)
                contactHeaderType.text = getString(R.string.websites)
                contactHeaderIcon.setImageResource(R.drawable.ic_link_vector)
                contactHeaderIcon.setColorFilter(getProperTextColor())
            }
            websites.forEach {
                val url = it
                ItemWebsiteBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false).apply {
                    val website = it
                    binding.contactWebsitesHolder.addView(root)
                    contactWebsite.text = url
                    root.copyOnLongClick(url)

                    root.setOnClickListener {
                        openWebsiteIntent(url)
                    }

                    binding.contactWebsitesHolder.background .setTint(buttonBg)

                    dividerContactWebsite.setBackgroundColor(getProperTextColor())
                    dividerContactWebsite.isGone = isLastItem == website
                    contactWebsite.setTextColor(getProperPrimaryColor())
                }
            }
            binding.contactWebsitesHolder.beVisible()
        } else {
            binding.contactWebsitesHolder.beGone()
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
        binding.contactGroupsHolder.removeAllViews()

        if (groups.isNotEmpty() && showFields and SHOW_GROUPS_FIELD != 0) {
            val isLastItem = groups.last()
            ItemViewHeaderBinding.inflate(layoutInflater, binding.contactGroupsHolder, false).apply {
                binding.contactGroupsHolder.addView(root)
                contactHeaderType.text = getString(R.string.groups)
                contactHeaderIcon.setImageResource(R.drawable.ic_people_rounded)
                contactHeaderIcon.setColorFilter(getProperTextColor())
            }
            groups.forEach {
                ItemViewGroupBinding.inflate(layoutInflater, binding.contactGroupsHolder, false).apply {
                    val group = it
                    binding.contactGroupsHolder.addView(root)
                    contactGroup.text = group.title
                    root.copyOnLongClick(group.title)

                    binding.contactGroupsHolder.background .setTint(buttonBg)

                    dividerContactGroup.setBackgroundColor(getProperTextColor())
                    dividerContactGroup.isGone = isLastItem == group
                    contactGroup.setTextColor(getProperPrimaryColor())
                }
            }
            binding.contactGroupsHolder.beVisible()
        } else {
            binding.contactGroupsHolder.beGone()
        }
    }

    private fun setupContactSources() {
        binding.contactSourcesHolder.removeAllViews()
        if (showFields and SHOW_CONTACT_SOURCE_FIELD != 0) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            if (mergeDuplicate) {
                duplicateContacts.forEach {
                    sources[it] = getPublicContactSourceSync(it.source, contactSources)
                }
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (key, value) -> value.lowercase(Locale.getDefault()) }.toMap() as LinkedHashMap<Contact, String>
            }

            ItemViewHeaderBinding.inflate(layoutInflater, binding.contactSourcesHolder, false).apply {
                binding.contactSourcesHolder.addView(root)
                contactHeaderType.text = getString(R.string.contact_source)
                contactHeaderIcon.setImageResource(R.drawable.ic_source_vector)
                contactHeaderIcon.setColorFilter(getProperTextColor())
            }

            for ((key, value) in sources) {
                val isLastItem = sources.keys.last()
                ItemViewContactSourceBinding.inflate(layoutInflater, binding.contactSourcesHolder, false).apply {
                    contactSource.text = if (value == "") getString(R.string.phone_storage) else value
                    contactSource.setTextColor(getProperPrimaryColor())
                    contactSource.copyOnLongClick(value)
                    binding.contactSourcesHolder.addView(root)

                    contactSource.setOnClickListener {
                        launchEditContact(key)
                    }

                    binding.contactSourcesHolder.background .setTint(buttonBg)

                    dividerContactSource.setBackgroundColor(getProperTextColor())
                    dividerContactSource.isGone = isLastItem == key

                    if (key.source.contains("gmail.com", true) || key.source.contains("googlemail.com", true)) {
                        contactSourceImage.setImageDrawable(getPackageDrawable("google"))
                        contactSourceImage.beVisible()
                    }

                    if (key.source == "") {
                        contactSourceImage.setImageDrawable(getPackageDrawable(key.source))
                        contactSourceImage.beVisible()
                    }

                    if (key.source == SMT_PRIVATE) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(SMT_PRIVATE))
                        contactSourceImage.beVisible()
                    }

                    if (value.lowercase(Locale.getDefault()) == WHATSAPP) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(WHATSAPP_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == SIGNAL) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(SIGNAL_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == VIBER) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(VIBER_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == TELEGRAM) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(TELEGRAM_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == THREEMA) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(THREEMA_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }
                }
            }
            binding.contactSourcesHolder.beVisible()
        } else {
            binding.contactSourcesHolder.beGone()
        }
    }

    private fun setupNotes() {
        val notes = contact!!.notes
        binding.contactNotes.removeAllViews()
        if (notes.isNotEmpty() && showFields and SHOW_NOTES_FIELD != 0) {
            ItemViewHeaderBinding.inflate(layoutInflater, binding.contactNotes, false).apply {
                binding.contactNotes.addView(root)
                contactHeaderType.text = getString(com.goodwy.commons.R.string.notes)
                contactHeaderIcon.setImageResource(com.goodwy.commons.R.drawable.ic_article_vector)
                contactHeaderIcon.setColorFilter(getProperTextColor())
            }
            ItemViewNoteBinding.inflate(layoutInflater, binding.contactNotes, false).apply {
                binding.contactNotes.addView(root)
                contactNote.text = notes
                root.copyOnLongClick(notes)

                binding.contactNotes.background .setTint(buttonBg)
            }
            binding.contactNotes.beVisible()
        } else {
            binding.contactNotes.beGone()
        }
    }

    private fun setupRingtone() {
        if (showFields and SHOW_RINGTONE_FIELD != 0) {
            binding.contactRingtoneHolder.beVisibleIf(contact!!.source != SMT_PRIVATE)

            binding.contactRingtoneHolder.background.setTint(buttonBg)
            binding.contactRingtoneChevron.setColorFilter(getProperTextColor())
            binding.contactRingtone.setTextColor(getProperPrimaryColor())

            val ringtone = contact!!.ringtone
            if (ringtone?.isEmpty() == true) {
                binding.contactRingtone.text = getString(com.goodwy.commons.R.string.no_sound)
            } else if (ringtone?.isNotEmpty() == true && ringtone != getDefaultRingtoneUri().toString()) {
                if (ringtone == SILENT) {
                    binding.contactRingtone.text = getString(com.goodwy.commons.R.string.no_sound)
                } else {
                    systemRingtoneSelected(ringtone.toUri())
                }
            } else {
                binding.contactRingtoneHolder.beGone()
                return
            }

            binding.contactRingtonePress.copyOnLongClick(binding.contactRingtone.text.toString())

            binding.contactRingtonePress.setOnClickListener {
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
                            binding.contactRingtone.text = it?.title
                            ringtoneUpdated(it?.uri)
                        },
                        onAlarmSoundDeleted = {}
                    )
                }
            }
        } else {
            binding.contactRingtoneHolder.beGone()
        }
    }

    private fun setupOrganization() {
        val organization = contact!!.organization
        if (organization.isNotEmpty() && showFields and SHOW_ORGANIZATION_FIELD != 0) {
            binding.topDetails.contactOrganizationCompany.setTextColor(getProperTextColor())
            binding.topDetails.contactOrganizationJobPosition.setTextColor(getProperTextColor())

            binding.topDetails.contactOrganizationCompany.text = organization.company
            binding.topDetails.contactOrganizationJobPosition.text = organization.jobPosition
            //binding.topDetails.contactOrganizationImage.beGoneIf(organization.isEmpty())
            binding.topDetails.contactCompanyHolder.beVisibleIf(contact?.isABusinessContact() != true)
            binding.topDetails.contactOrganizationCompany.beGoneIf(organization.company.isEmpty())
            binding.topDetails.contactOrganizationJobPosition.beGoneIf(organization.jobPosition.isEmpty())
            binding.topDetails.contactOrganizationCompany.copyOnLongClick(binding.topDetails.contactOrganizationCompany.value)
            binding.topDetails.contactOrganizationJobPosition.copyOnLongClick(binding.topDetails.contactOrganizationJobPosition.value)

//            if (organization.company.isEmpty() && organization.jobPosition.isNotEmpty()) {
//                (binding.topDetails.contactOrganizationImage.layoutParams as RelativeLayout.LayoutParams).addRule(
//                    RelativeLayout.ALIGN_TOP,
//                    binding.topDetails.contactOrganizationJobPosition.id
//                )
//            }
        } else {
            binding.topDetails.contactCompanyHolder.beGone()
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
                                        toast(com.goodwy.commons.R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(com.goodwy.commons.R.string.no_app_found)
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
                                            toast(com.goodwy.commons.R.string.no_phone_call_permission)
                                        }
                                    }
                                } catch (e: ActivityNotFoundException) {
                                    toast(com.goodwy.commons.R.string.no_app_found)
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
                                        toast(com.goodwy.commons.R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(com.goodwy.commons.R.string.no_app_found)
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
                                        toast(com.goodwy.commons.R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(com.goodwy.commons.R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

//    private fun startMessengerAction(action: SocialAction) {
//        ensureBackgroundThread {
//            Intent(Intent.ACTION_VIEW).apply {
//                val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
//                setDataAndType(uri, action.mimetype)
//                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
//                try {
//                    startActivity(this)
//                } catch (e: SecurityException) {
//                    handlePermission(PERMISSION_CALL_PHONE) { success ->
//                        if (success) {
//                            startActivity(this)
//                        } else {
//                            toast(com.goodwy.commons.R.string.no_phone_call_permission)
//                        }
//                    }
//                } catch (e: ActivityNotFoundException) {
//                    toast(com.goodwy.commons.R.string.no_app_found)
//                } catch (e: Exception) {
//                    showErrorToast(e)
//                }
//            }
//        }
//    }

    override fun customRingtoneSelected(ringtonePath: String) {
        binding.contactRingtone.text = ringtonePath.getFilenameFromPath()
        ringtoneUpdated(ringtonePath)
    }

    override fun systemRingtoneSelected(uri: Uri?) {
        val contactRingtone = RingtoneManager.getRingtone(this, uri)
        binding.contactRingtone.text = contactRingtone.getTitle(this)
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

    private fun deleteContactWithMergeLogic() {
        val addition = if (binding.contactSourcesHolder.childCount > 1 && mergeDuplicate) {
            "\n\n${getString(R.string.delete_from_all_sources)}"
        } else {
            ""
        }

        val message = "${getString(com.goodwy.commons.R.string.proceed_with_deletion)}$addition"
        ConfirmationDialog(this, message) {
            if (contact != null) {
                ContactsHelper(this).deleteContact(contact!!, mergeDuplicate) {
                    finish()
                }
            }
        }
    }

    private fun getStarDrawable(on: Boolean) =
        resources.getDrawable(if (on) com.goodwy.commons.R.drawable.ic_star_vector else com.goodwy.commons.R.drawable.ic_star_outline_vector)

    private fun hideBigContactPhoto() {
        binding.contactPhotoBig.animate().alpha(0f).withEndAction { binding.contactPhotoBig.beGone() }.start()
    }

    private fun View.copyOnLongClick(value: String) {
        setOnLongClickListener {
            copyToClipboard(value)
            true
        }
    }
}
