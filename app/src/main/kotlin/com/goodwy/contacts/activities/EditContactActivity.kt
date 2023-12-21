package com.goodwy.contacts.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.*
import android.provider.MediaStore
import android.telephony.PhoneNumberUtils
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.dialogs.SelectAlarmSoundDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.*
import com.goodwy.commons.models.contacts.ContactRelation
import com.goodwy.commons.models.contacts.Email
import com.goodwy.commons.models.contacts.Event
import com.goodwy.commons.models.contacts.Organization
import com.goodwy.commons.views.MyAutoCompleteTextView
import com.goodwy.contacts.R
import com.goodwy.contacts.adapters.AutoCompleteTextViewAdapter
import com.goodwy.contacts.databinding.*
import com.goodwy.contacts.dialogs.CustomLabelDialog
import com.goodwy.contacts.dialogs.ManageVisibleFieldsDialog
import com.goodwy.contacts.dialogs.MyDatePickerDialog
import com.goodwy.contacts.dialogs.SelectGroupsDialog
import com.goodwy.contacts.extensions.config
import com.goodwy.contacts.extensions.getCachePhotoUri
import com.goodwy.contacts.extensions.getPackageDrawable
import com.goodwy.contacts.extensions.showContactSourcePicker
import com.goodwy.contacts.helpers.*
import java.util.Locale

class EditContactActivity : ContactActivity() {
    companion object {
        private const val INTENT_TAKE_PHOTO = 1
        private const val INTENT_CHOOSE_PHOTO = 2
        private const val INTENT_CROP_PHOTO = 3

        private const val TAKE_PHOTO = 1
        private const val CHOOSE_PHOTO = 2
        private const val REMOVE_PHOTO = 3

        private const val AUTO_COMPLETE_DELAY = 5000L
    }

    private var mLastSavePromptTS = 0L
    private var wasActivityInitialized = false
    private var lastPhotoIntentUri: Uri? = null
    private var isSaving = false
    private var isThirdPartyIntent = false
    private var highlightLastPhoneNumber = false
    private var highlightLastEmail = false
    private var numberViewToColor: EditText? = null
    private var emailViewToColor: EditText? = null
    private var originalContactSource = ""
    private val binding by viewBinding(ActivityEditContactBinding::inflate)
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFFEBEBEB.toInt()

    enum class PrimaryNumberStatus {
        UNCHANGED, STARRED, UNSTARRED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (checkAppSideloading()) {
            return
        }

        updateMaterialActivityViews(binding.contactWrapper, binding.contactHolder, useTransparentNavigation = false, useTopSearchMenu = false)
//        setWindowTransparency(true) { _, _, leftNavigationBarSize, rightNavigationBarSize ->
//            binding.contactWrapper.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
//            updateNavigationBarColor(getProperBackgroundColor())
//        }

        binding.contactWrapper.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setupInsets()
        setupMenu()

        val action = intent.action
        isThirdPartyIntent = action == Intent.ACTION_EDIT || action == Intent.ACTION_INSERT || action == ADD_NEW_CONTACT_NUMBER
        val isFromSimpleContacts = intent.getBooleanExtra(IS_FROM_SIMPLE_CONTACTS, false)
        if (isThirdPartyIntent && !isFromSimpleContacts) {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    handlePermission(PERMISSION_WRITE_CONTACTS) {
                        if (it) {
                            initContact()
                        } else {
                            toast(com.goodwy.commons.R.string.no_contacts_permission)
                            hideKeyboard()
                            finish()
                        }
                    }
                } else {
                    toast(com.goodwy.commons.R.string.no_contacts_permission)
                    hideKeyboard()
                    finish()
                }
            }
        } else {
            initContact()
        }
    }

    override fun onResume() {
        super.onResume()
        updateColors()
    }

    private fun updateColors(color: Int = getProperBackgroundColor()) {
        if (baseConfig.backgroundColor == white) {
            supportActionBar?.setBackgroundDrawable(ColorDrawable(0xFFf2f2f6.toInt()))
            window.decorView.setBackgroundColor(0xFFf2f2f6.toInt())
            window.statusBarColor = 0xFFf2f2f6.toInt()
            window.navigationBarColor = 0xFFf2f2f6.toInt()
        } else window.decorView.setBackgroundColor(color)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                INTENT_TAKE_PHOTO, INTENT_CHOOSE_PHOTO -> startCropPhotoIntent(lastPhotoIntentUri, resultData?.data)
                INTENT_CROP_PHOTO -> updateContactPhoto(lastPhotoIntentUri.toString(), binding.topDetails.contactPhoto, binding.contactPhotoBottomShadow)
            }
        }
    }

    private fun initContact() {
        var contactId = intent.getIntExtra(CONTACT_ID, 0)
        val action = intent.action
        if (contactId == 0 && (action == Intent.ACTION_EDIT || action == ADD_NEW_CONTACT_NUMBER)) {
            val data = intent.data
            if (data != null && data.path != null) {
                val rawId = if (data.path!!.contains("lookup")) {
                    if (data.pathSegments.last().startsWith("local_")) {
                        data.path!!.substringAfter("local_").toInt()
                    } else {
                        getLookupUriRawId(data)
                    }
                } else {
                    getContactUriRawId(data)
                }

                if (rawId != -1) {
                    contactId = rawId
                }
            }
        }

        if (contactId != 0) {
            ensureBackgroundThread {
                contact = ContactsHelper(this).getContactWithId(contactId, intent.getBooleanExtra(IS_PRIVATE, false))
                if (contact == null) {
                    toast(com.goodwy.commons.R.string.unknown_error_occurred)
                    hideKeyboard()
                    finish()
                } else {
                    runOnUiThread {
                        gotContact()
                    }
                }
            }
        } else {
            gotContact()
        }
    }

    private fun gotContact() {
        binding.contactScrollview.beVisible()
        if (contact == null) {
            setupNewContact()
        } else {
            setupEditContact()
            originalRingtone = contact?.ringtone
        }

        val action = intent.action
        if (((contact!!.id == 0 && action == Intent.ACTION_INSERT) || action == ADD_NEW_CONTACT_NUMBER) && intent.extras != null) {
            val phoneNumber = getPhoneNumberFromIntent(intent)
            if (phoneNumber != null) {
                contact!!.phoneNumbers.add(PhoneNumber(phoneNumber, DEFAULT_PHONE_NUMBER_TYPE, "", phoneNumber.normalizePhoneNumber()))
                if (phoneNumber.isNotEmpty() && action == ADD_NEW_CONTACT_NUMBER) {
                    highlightLastPhoneNumber = true
                }
            }

            val email = intent.getStringExtra(KEY_EMAIL)
            if (email != null) {
                val newEmail = Email(email, DEFAULT_EMAIL_TYPE, "")
                contact!!.emails.add(newEmail)
                highlightLastEmail = true
            }

            val firstName = intent.extras!!.get(KEY_NAME)
            if (firstName != null) {
                contact!!.firstName = firstName.toString()
            }

            val data = intent.extras!!.getParcelableArrayList<ContentValues>("data")
            if (data != null) {
                parseIntentData(data)
            }
            setupEditContact()
        }

        setupTypePickers()
        setupRingtone()

        if (contact!!.photoUri.isEmpty() && contact!!.photo == null) {
            showPhotoPlaceholder(binding.topDetails.contactPhoto)
            //binding.contactPhotoBottomShadow.beGone()
        } else {
            updateContactPhoto(contact!!.photoUri, binding.topDetails.contactPhoto, binding.contactPhotoBottomShadow, contact!!.photo)
        }

        val textColor = getProperTextColor()
        arrayOf(
            binding.contactNumbersIcon,
            binding.contactEmailsIcon,
            binding.contactAddressesIcon,
            binding.contactImsIcon,
            binding.contactEventsIcon,
            binding.contactRelationsIcon,
            binding.contactNotesIcon,
            binding.contactRingtoneIcon,
            binding.contactWebsitesIcon,
            binding.contactGroupsTitleIcon,
            binding.contactSourceTitleIcon
        ).forEach {
            it.applyColorFilter(textColor)
        }

        binding.contactToggleFavorite.setOnClickListener { toggleFavorite() }
        binding.topDetails.contactPhoto.setOnClickListener { trySetPhotoRecommendation() }
        binding.contactChangePhoto.setOnClickListener { trySetPhotoRecommendation() }
        binding.contactNumbersAddNewHolder.setOnClickListener { addNewPhoneNumberField() }
        binding.contactEmailsAddNewHolder.setOnClickListener { addNewEmailField() }
        binding.contactAddressesAddNewHolder.setOnClickListener { addNewAddressField() }
        binding.contactImsAddNewHolder.setOnClickListener { addNewIMField() }
        binding.contactEventsAddNewHolder.setOnClickListener { addNewEventField() }
        binding.contactWebsitesAddNewHolder.setOnClickListener { addNewWebsiteField() }
        binding.contactGroupsAddNewHolder.setOnClickListener { showSelectGroupsDialog() }
        binding.contactSource.setOnClickListener { showSelectContactSourceDialog() }
        binding.contactRelationsAddNewHolder.setOnClickListener { addNewRelationField() }

        binding.contactChangePhoto.setOnLongClickListener { toast(R.string.change_photo); true; }

        setupFieldVisibility()

        binding.contactToggleFavorite.apply {
            setImageDrawable(getStarDrawable(contact!!.starred == 1))
            tag = contact!!.starred
            setOnLongClickListener { toast(R.string.toggle_favorite); true; }
        }

        val nameTextViews = arrayOf(binding.contactFirstName, binding.contactMiddleName, binding.contactSurname).filter { it.isVisible() }
        if (nameTextViews.isNotEmpty()) {
            setupAutoComplete(nameTextViews)
        }

        val properPrimaryColor = getProperPrimaryColor()
        updateTextColors(binding.contactScrollview)
        numberViewToColor?.setTextColor(properPrimaryColor)
        emailViewToColor?.setTextColor(properPrimaryColor)
        wasActivityInitialized = true

        binding.contactToolbar.menu.apply {
            findItem(R.id.delete).isVisible = contact?.id != 0
            findItem(R.id.share).isVisible = contact?.id != 0
            findItem(R.id.open_with).isVisible = contact?.id != 0 && contact?.isPrivate() == false

            val contrastColor = getProperBackgroundColor().getContrastColor()
            val iconColor = if (baseConfig.topAppBarColorIcon) properPrimaryColor else contrastColor
            val favoriteIcon = getStarDrawable(contact!!.starred == 1)
            favoriteIcon.setTint(iconColor)
            findItem(R.id.favorite).icon = favoriteIcon
        }
    }

    override fun onBackPressed() {
        if (System.currentTimeMillis() - mLastSavePromptTS > SAVE_DISCARD_PROMPT_INTERVAL && hasContactChanged()) {
            mLastSavePromptTS = System.currentTimeMillis()
            ConfirmationAdvancedDialog(
                this,
                "",
                com.goodwy.commons.R.string.save_before_closing,
                com.goodwy.commons.R.string.save,
                com.goodwy.commons.R.string.discard
            ) {
                if (it) {
                    saveContact()
                } else {
                    super.onBackPressed()
                }
            }
        } else {
            super.onBackPressed()
        }
    }

    private fun setupInsets() {
        binding.contactWrapper.setOnApplyWindowInsetsListener { _, insets ->
            val windowInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            binding.contactScrollview.run {
                setPadding(paddingLeft, paddingTop, paddingRight, imeInsets.bottom)
            }
            insets
        }
    }

    private fun setupMenu() {
        val contrastColor = getProperBackgroundColor().getContrastColor()
        val primaryColor = getProperPrimaryColor()
        val iconColor = if (baseConfig.topAppBarColorIcon) primaryColor else contrastColor
        //(binding.contactAppbar.layoutParams as RelativeLayout.LayoutParams).topMargin = statusBarHeight
        (binding.contactWrapper.layoutParams as FrameLayout.LayoutParams).topMargin = statusBarHeight
        binding.contactToolbar.overflowIcon = resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.ic_three_dots_vector, iconColor)
        binding.contactToolbar.menu.apply {
            updateMenuItemColors(this)
            findItem(R.id.favorite).setOnMenuItemClickListener {
                val newIsStarred = if (contact!!.starred == 1) 0 else 1
                ensureBackgroundThread {
                    val contacts = arrayListOf(contact!!)
                    if (newIsStarred == 1) {
                        ContactsHelper(this@EditContactActivity).addFavorites(contacts)
                    } else {
                        ContactsHelper(this@EditContactActivity).removeFavorites(contacts)
                    }
                }
                contact!!.starred = newIsStarred
                val favoriteIcon = getStarDrawable(contact!!.starred == 1)
                favoriteIcon.setTint(iconColor)
                findItem(R.id.favorite).icon = favoriteIcon
                true
            }

            findItem(R.id.save).setOnMenuItemClickListener {
                saveContact()
                true
            }

            findItem(R.id.share).setOnMenuItemClickListener {
                shareContact(contact!!)
                true
            }

            findItem(R.id.open_with).setOnMenuItemClickListener {
                openWith()
                true
            }

            findItem(R.id.delete).setOnMenuItemClickListener {
                deleteContact()
                true
            }

            findItem(R.id.manage_visible_fields).setOnMenuItemClickListener {
                ManageVisibleFieldsDialog(this@EditContactActivity) {
                    initContact()
                }
                true
            }
        }

        binding.contactToolbar.setNavigationIconTint(iconColor)
        binding.contactToolbar.setNavigationOnClickListener {
            hideKeyboard()
            finish()
        }
    }

    private fun hasContactChanged() = contact != null && contact != fillContactValues() || originalRingtone != contact?.ringtone

    private fun openWith() {
        Intent().apply {
            action = Intent.ACTION_EDIT
            data = getContactPublicUri(contact!!)
            launchActivityIntent(this)
        }
    }

    private fun startCropPhotoIntent(primaryUri: Uri?, backupUri: Uri?) {
        if (primaryUri == null) {
            toast(com.goodwy.commons.R.string.unknown_error_occurred)
            return
        }

        var imageUri = primaryUri
        var bitmap = MediaStore.Images.Media.getBitmap(contentResolver, primaryUri)
        if (bitmap == null) {
            imageUri = backupUri
            try {
                bitmap = MediaStore.Images.Media.getBitmap(contentResolver, backupUri) ?: return
            } catch (e: Exception) {
                showErrorToast(e)
                return
            }

            // we might have received an URI which we have no permission to send further, so just copy the received image in a new uri (for example from Google Photos)
            val newFile = getCachePhoto()
            val fos = newFile.outputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            imageUri = getCachePhotoUri(newFile)
        }

        hideKeyboard()
        lastPhotoIntentUri = getCachePhotoUri()
        Intent("com.android.camera.action.CROP").apply {
            setDataAndType(imageUri, "image/*")
            putExtra(MediaStore.EXTRA_OUTPUT, lastPhotoIntentUri)
            putExtra("outputX", 512)
            putExtra("outputY", 512)
            putExtra("aspectX", 1)
            putExtra("aspectY", 1)
            putExtra("crop", "true")
            putExtra("scale", "true")
            putExtra("scaleUpIfNeeded", "true")
            clipData = ClipData("Attachment", arrayOf("text/primaryUri-list"), ClipData.Item(lastPhotoIntentUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            try {
                startActivityForResult(this, INTENT_CROP_PHOTO)
            } catch (e: ActivityNotFoundException) {
                toast(com.goodwy.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun setupFieldVisibility() {
        val showFields = config.showContactFields

        binding.contactPrefix.beVisibleIf(showFields and SHOW_PREFIX_FIELD != 0)
        binding.contactFirstName.beVisibleIf(showFields and SHOW_FIRST_NAME_FIELD != 0)
        binding.contactMiddleName.beVisibleIf(showFields and SHOW_MIDDLE_NAME_FIELD != 0)
        binding.contactSurname.beVisibleIf(showFields and SHOW_SURNAME_FIELD != 0)
        binding.contactSuffix.beVisibleIf(showFields and SHOW_SUFFIX_FIELD != 0)
        binding.contactNickname.beVisibleIf(showFields and SHOW_NICKNAME_FIELD != 0)

        val isOrganizationVisible = showFields and SHOW_ORGANIZATION_FIELD != 0
        binding.contactOrganizationCompany.beVisibleIf(isOrganizationVisible)
        binding.contactOrganizationJobPosition.beVisibleIf(isOrganizationVisible)
        binding.dividerContactOrganizationCompany.beVisibleIf(isOrganizationVisible)

        binding.dividerContactPrefix.beVisibleIf(showFields and SHOW_PREFIX_FIELD != 0 &&
            (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0
                || showFields and SHOW_SURNAME_FIELD != 0 || showFields and SHOW_MIDDLE_NAME_FIELD != 0 || showFields and SHOW_FIRST_NAME_FIELD != 0))
        binding.dividerContactFirstName.beVisibleIf(showFields and SHOW_FIRST_NAME_FIELD != 0 &&
            (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0
                || showFields and SHOW_SURNAME_FIELD != 0 || showFields and SHOW_MIDDLE_NAME_FIELD != 0))
        binding.dividerContactMiddleName.beVisibleIf(showFields and SHOW_MIDDLE_NAME_FIELD != 0 &&
            (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0 || showFields and SHOW_SURNAME_FIELD != 0))
        binding.dividerContactSurname.beVisibleIf(showFields and SHOW_SURNAME_FIELD != 0 &&
            (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0))
        binding.dividerContactSuffix.beVisibleIf(showFields and SHOW_SUFFIX_FIELD != 0 && (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0))
        binding.dividerContactNickname.beVisibleIf(showFields and SHOW_NICKNAME_FIELD != 0 && isOrganizationVisible)

        binding.contactSourceHolder.beVisibleIf(showFields and SHOW_CONTACT_SOURCE_FIELD != 0)
        binding.contactSourceTitleHolder.beVisibleIf(showFields and SHOW_CONTACT_SOURCE_FIELD != 0)

        val arePhoneNumbersVisible = showFields and SHOW_PHONE_NUMBERS_FIELD != 0
        binding.contactNumbersTitleHolder.beVisibleIf(arePhoneNumbersVisible)
        binding.contactNumbersHolder.beVisibleIf(arePhoneNumbersVisible)
        binding.contactNumbersAddNewHolder.beVisibleIf(arePhoneNumbersVisible)

        val areEmailsVisible = showFields and SHOW_EMAILS_FIELD != 0
        binding.contactEmailsTitleHolder.beVisibleIf(areEmailsVisible)
        binding.contactEmailsHolder.beVisibleIf(areEmailsVisible)
        binding.contactEmailsAddNewHolder.beVisibleIf(areEmailsVisible)

        val areAddressesVisible = showFields and SHOW_ADDRESSES_FIELD != 0
        binding.contactAddressesTitleHolder.beVisibleIf(areAddressesVisible)
        binding.contactAddressesHolder.beVisibleIf(areAddressesVisible)
        binding.contactAddressesAddNewHolder.beVisibleIf(areAddressesVisible)

        val areIMsVisible = showFields and SHOW_IMS_FIELD != 0
        binding.contactImsTitleHolder.beVisibleIf(areIMsVisible)
        binding.contactImsHolder.beVisibleIf(areIMsVisible)
        binding.contactImsAddNewHolder.beVisibleIf(areIMsVisible)

        val areEventsVisible = showFields and SHOW_EVENTS_FIELD != 0
        binding.contactEventsTitleHolder.beVisibleIf(areEventsVisible)
        binding.contactEventsHolder.beVisibleIf(areEventsVisible)
        binding.contactEventsAddNewHolder.beVisibleIf(areEventsVisible)

        val areWebsitesVisible = showFields and SHOW_WEBSITES_FIELD != 0
        binding.contactWebsitesTitleHolder.beVisibleIf(areWebsitesVisible)
        binding.contactWebsitesHolder.beVisibleIf(areWebsitesVisible)
        binding.contactWebsitesAddNewHolder.beVisibleIf(areWebsitesVisible)

        val areRelationsVisible = showFields and SHOW_RELATIONS_FIELD != 0
        binding.contactRelationsTitleHolder.beVisibleIf(areRelationsVisible)
        binding.contactRelationsHolder.beVisibleIf(areRelationsVisible)
        binding.contactRelationsAddNewHolder.beVisibleIf(areRelationsVisible)

        val areGroupsVisible = showFields and SHOW_GROUPS_FIELD != 0
        binding.contactGroupsTitleHolder.beVisibleIf(areGroupsVisible)
        binding.contactGroupsHolder.beVisibleIf(areGroupsVisible)
        //binding.contactGroupsAddNewHolder.beVisibleIf(areGroupsVisible)

        val areNotesVisible = showFields and SHOW_NOTES_FIELD != 0
        binding.contactNotes.beVisibleIf(areNotesVisible)
        binding.contactNotesTitleHolder.beVisibleIf(areNotesVisible)

        val isRingtoneVisible = showFields and SHOW_RINGTONE_FIELD != 0
        binding.contactRingtoneHolder.beVisibleIf(isRingtoneVisible)
        binding.contactRingtoneTitleHolder.beVisibleIf(isRingtoneVisible)
    }

    private fun setupEditContact() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setupNames()
        setupPhoneNumbers()
        setupEmails()
        setupAddresses()
        setupIMs()
        setupNotes()
        setupOrganization()
        setupWebsites()
        setupRelations()
        setupEvents()
        setupGroups()
        setupContactSource()
    }

    private fun setupNames() {
        contact!!.apply {
            binding.contactPrefix.setText(prefix)
            binding.contactFirstName.setText(firstName)
            binding.contactMiddleName.setText(middleName)
            binding.contactSurname.setText(surname)
            binding.contactSuffix.setText(suffix)
            binding.contactNickname.setText(nickname)
        }

//        val backgroundColor = if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) white else getProperTextColor()
//        arrayOf(
//            binding.contactPrefix,
//            binding.contactFirstName,
//            binding.contactMiddleName,
//            binding.contactSurname,
//            binding.contactSuffix,
//            binding.contactNickname
//        ).forEach {
//            it.setBackgroundColor(backgroundColor)
//        }
    }

    private fun setupOrganization() {
        binding.contactOrganizationCompany.setText(contact!!.organization.company)
        binding.contactOrganizationJobPosition.setText(contact!!.organization.jobPosition)

        binding.dividerContactOrganizationCompany.setBackgroundColor(getProperTextColor())

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactOrganizationCompany.setBackgroundColor(white)
            binding.contactOrganizationJobPosition.setBackgroundColor(white)
        }
    }

    private fun setupPhoneNumbers() {
        val phoneNumbers = contact!!.phoneNumbers
        //binding.contactNumbersHolder.removeAllViews()
        phoneNumbers.forEachIndexed { index, number ->
            val numberHolderView = binding.contactNumbersHolder.getChildAt(index)
            val numberHolder = if (numberHolderView == null) {
                ItemEditPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false).apply {
                    binding.contactNumbersHolder.addView(root)
                }
            } else {
                ItemEditPhoneNumberBinding.bind(numberHolderView)
            }

            numberHolder.apply {
                contactNumber.setText(number.value)
                contactNumber.tag = number.normalizedNumber
                setupPhoneNumberTypePicker(contactNumberType, number.type, number.label)
                if (highlightLastPhoneNumber && index == phoneNumbers.size - 1) {
                    numberViewToColor = contactNumber
                }

                defaultToggleIcon.tag = if (number.isPrimary) 1 else 0

                val getProperTextColor = getProperTextColor()
                dividerVerticalContactNumber.setBackgroundColor(getProperTextColor)
                dividerContactNumber.setBackgroundColor(getProperTextColor)
                contactNumberType.setTextColor(getProperPrimaryColor())
                contactNumberRemove.apply {
                    beVisible()
                    setOnClickListener {
                        binding.contactNumbersHolder.removeView(numberHolder.root)
                    }
                }
            }
        }

        initNumberHolders()

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactNumbersHolder.setBackgroundColor(white)
            binding.contactNumbersAddNewHolder.setBackgroundColor(white)
        }
    }

    private fun setDefaultNumber(selected: ImageView) {
        val numbersCount = binding.contactNumbersHolder.childCount
        for (i in 0 until numbersCount) {
            val toggleIcon = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(i)).defaultToggleIcon
            if (toggleIcon != selected) {
                toggleIcon.tag = 0
            }
        }

        selected.tag = if (selected.tag == 1) 0 else 1

        initNumberHolders()
    }

    private fun initNumberHolders() {
        val numbersCount = binding.contactNumbersHolder.childCount

        if (numbersCount == 1) {
            ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(0)).defaultToggleIcon.beGone()
            return
        }

        for (i in 0 until numbersCount) {
            val toggleIcon = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(i)).defaultToggleIcon
            val isPrimary = toggleIcon.tag == 1

            val drawableId = if (isPrimary) {
                com.goodwy.commons.R.drawable.ic_star_vector
            } else {
                com.goodwy.commons.R.drawable.ic_star_outline_vector
            }

            val drawable = ContextCompat.getDrawable(this@EditContactActivity, drawableId)
            drawable?.apply {
                mutate()
                setTint(getProperTextColor())
            }

            toggleIcon.setImageDrawable(drawable)
            toggleIcon.beVisible()
            toggleIcon.setOnClickListener {
                setDefaultNumber(toggleIcon)
            }
        }
    }

    private fun setupEmails() {
        //binding.contactEmailsHolder.removeAllViews()
        contact!!.emails.forEachIndexed { index, email ->
            val emailHolderView = binding.contactEmailsHolder.getChildAt(index)
            val emailHolder = if (emailHolderView == null) {
                ItemEditEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false).apply {
                    binding.contactEmailsHolder.addView(root)
                }
            } else {
                ItemEditEmailBinding.bind(emailHolderView)
            }

            emailHolder.apply {
                contactEmail.setText(email.value)
                setupEmailTypePicker(contactEmailType, email.type, email.label)
                if (highlightLastEmail && index == contact!!.emails.size - 1) {
                    emailViewToColor = contactEmail
                }

                val getProperTextColor = getProperTextColor()
                dividerVerticalContactEmail.setBackgroundColor(getProperTextColor)
                dividerContactEmail.setBackgroundColor(getProperTextColor)
                contactEmailType.setTextColor(getProperPrimaryColor())
                contactEmailRemove.apply {
                    beVisible()
                    setOnClickListener {
                        binding.contactEmailsHolder.removeView(emailHolder.root)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactEmailsHolder.setBackgroundColor(white)
            binding.contactEmailsAddNewHolder.setBackgroundColor(white)
        }
    }

    private fun setupAddresses() {
        //binding.contactAddressesHolder.removeAllViews()
        contact!!.addresses.forEachIndexed { index, address ->
            val addressHolderView = binding.contactAddressesHolder.getChildAt(index)
            val addressHolder = if (addressHolderView == null) {
                ItemEditAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false).apply {
                    binding.contactAddressesHolder.addView(root)
                }
            } else {
                ItemEditAddressBinding.bind(addressHolderView)
            }

            addressHolder.apply {
                contactAddress.setText(address.value)
                setupAddressTypePicker(contactAddressType, address.type, address.label)

                val getProperTextColor = getProperTextColor()
                dividerVerticalContactAddress.setBackgroundColor(getProperTextColor)
                dividerContactAddress.setBackgroundColor(getProperTextColor)
                contactAddressType.setTextColor(getProperPrimaryColor())
                contactAddressRemove.apply {
                    beVisible()
                    setOnClickListener {
                        binding.contactAddressesHolder.removeView(addressHolder.root)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactAddressesHolder.setBackgroundColor(white)
            binding.contactAddressesAddNewHolder.setBackgroundColor(white)
        }
    }

    private fun setupIMs() {
        //binding.contactImsHolder.removeAllViews()
        contact!!.IMs.forEachIndexed { index, IM ->
            val imHolderView = binding.contactImsHolder.getChildAt(index)
            val imHolder = if (imHolderView == null) {
                ItemEditImBinding.inflate(layoutInflater, binding.contactImsHolder, false).apply {
                    binding.contactImsHolder.addView(root)
                }
            } else {
                ItemEditImBinding.bind(imHolderView)
            }

            imHolder.apply {
                contactIm.setText(IM.value)
                setupIMTypePicker(contactImType, IM.type, IM.label)

                val getProperTextColor = getProperTextColor()
                dividerVerticalContactIm.setBackgroundColor(getProperTextColor)
                dividerContactIm.setBackgroundColor(getProperTextColor)
                contactImType.setTextColor(getProperPrimaryColor())
                contactImRemove.apply {
                    beVisible()
                    setOnClickListener {
                        binding.contactImsHolder.removeView(imHolder.root)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactImsHolder.setBackgroundColor(white)
            binding.contactImsAddNewHolder.setBackgroundColor(white)
        }
    }

    private fun setupNotes() {
        binding.contactNotes.setText(contact!!.notes)
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactNotes.setBackgroundColor(white)
        }
    }

    private fun setupRingtone() {
        binding.contactRingtone.setOnClickListener {
            hideKeyboard()
            val ringtonePickerIntent = getRingtonePickerIntent()
            try {
                startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
            } catch (e: Exception) {
                val currentRingtone = contact!!.ringtone ?: getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE).uri
                SelectAlarmSoundDialog(this, currentRingtone, AudioManager.STREAM_RING, PICK_RINGTONE_INTENT_ID, RingtoneManager.TYPE_RINGTONE, true,
                    onAlarmPicked = {
                        contact!!.ringtone = it?.uri
                        binding.contactRingtone.text = it?.title
                    }, onAlarmSoundDeleted = {}
                )
            }
        }

        val ringtone = contact!!.ringtone
        if (ringtone?.isEmpty() == true) {
            binding.contactRingtone.text = getString(com.goodwy.commons.R.string.no_sound)
        } else if (ringtone?.isNotEmpty() == true) {
            if (ringtone == SILENT) {
                binding.contactRingtone.text = getString(com.goodwy.commons.R.string.no_sound)
            } else {
                systemRingtoneSelected(Uri.parse(ringtone))
            }
        } else {
            val default = getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE)
            binding.contactRingtone.text = default.title
        }

        binding.contactRingtone.setTextColor(getProperPrimaryColor())
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactRingtoneHolder.setBackgroundColor(white)
        }
        binding.contactRingtoneChevron.setColorFilter(getProperTextColor())
    }

    private fun setupWebsites() {
        //binding.contactWebsitesHolder.removeAllViews()
        contact!!.websites.forEachIndexed { index, website ->
            val websitesHolderView = binding.contactWebsitesHolder.getChildAt(index)
            val websitesHolder = if (websitesHolderView == null) {
                ItemEditWebsiteBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false).apply {
                    binding.contactWebsitesHolder.addView(root)
                }
            } else {
                ItemEditWebsiteBinding.bind(websitesHolderView)
            }

            websitesHolder.contactWebsite.setText(website)

            websitesHolder.apply {
                dividerContactWebsite.setBackgroundColor(getProperTextColor())
                contactWebsiteRemove.apply {
                    beVisible()
                    setOnClickListener {
                        binding.contactWebsitesHolder.removeView(websitesHolder.root)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactWebsitesHolder.setBackgroundColor(white)
            binding.contactWebsitesAddNewHolder.setBackgroundColor(white)
        }
    }

    private fun setupEvents() {
        contact!!.events.forEachIndexed { index, event ->
            val eventHolderView = binding.contactEventsHolder.getChildAt(index)
            val eventHolder = if (eventHolderView == null) {
                ItemEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false).apply {
                    binding.contactEventsHolder.addView(root)
                }
            } else {
                ItemEventBinding.bind(eventHolderView)
            }

            eventHolder.apply {
                val contactEvent = contactEvent.apply {
                    event.value.getDateTimeFromDateString(true, this)
                    tag = event.value
                    alpha = 1f
                }

                setupEventTypePicker(this, event.type)

                val getProperTextColor = getProperTextColor()
                dividerVerticalContactEvent.setBackgroundColor(getProperTextColor)
                dividerContactEvent.setBackgroundColor(getProperTextColor)
                contactEventType.setTextColor(getProperPrimaryColor())
                contactEventRemove.apply {
                    beVisible()
                    setOnClickListener {
                        resetContactEvent(contactEvent, this)
                        binding.contactEventsHolder.removeView(eventHolder.root)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactEventsHolder.setBackgroundColor(white)
            binding.contactEventsAddNewHolder.setBackgroundColor(white)
        }
    }

    private fun setupRelations() {
        //binding.contactRelationsHolder.removeAllViews()
        contact!!.relations.forEachIndexed { index, relation ->
            val relationHolderView = binding.contactRelationsHolder.getChildAt(index)
            val relationHolder = if (relationHolderView == null) {
                ItemEditRelationBinding.inflate(layoutInflater, binding.contactRelationsHolder, false).apply {
                    binding.contactRelationsHolder.addView(root)
                }
            } else {
                ItemEditRelationBinding.bind(relationHolderView)
            }

            relationHolder.apply {
                contactRelation.setText(relation.name)
                setupRelationTypePicker(contactRelationType, relation.type, relation.label)

                val getProperTextColor = getProperTextColor()
                dividerVerticalContactRelation.setBackgroundColor(getProperTextColor)
                dividerContactRelation.setBackgroundColor(getProperTextColor)
                contactRelationType.setTextColor(getProperPrimaryColor())
                contactRelationRemove.apply {
                    beVisible()
                    setOnClickListener {
                        binding.contactRelationsHolder.removeView(relationHolder.root)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactRelationsHolder.setBackgroundColor(white)
            binding.contactRelationsAddNewHolder.setBackgroundColor(white)
        }
    }

    private fun setupGroups() {
        binding.contactGroupsHolder.removeAllViews()
        val groups = contact!!.groups
        groups.forEachIndexed { index, group ->
            val groupHolderView = binding.contactGroupsHolder.getChildAt(index)
            val groupHolder = if (groupHolderView == null) {
                ItemEditGroupBinding.inflate(layoutInflater, binding.contactGroupsHolder, false).apply {
                    binding.contactGroupsHolder.addView(root)
                }
            } else {
                ItemEditGroupBinding.bind(groupHolderView)
            }

            groupHolder.apply {
                contactGroup.apply {
                    text = group.title
                    setTextColor(getProperPrimaryColor())
                    tag = group.id
                    alpha = 1f
                }

                root.setOnClickListener {
                    showSelectGroupsDialog()
                }

                contactGroupRemove.apply {
                    beVisible()
                    //applyColorFilter(getProperPrimaryColor())
                    //background.applyColorFilter(getProperTextColor())
                    setOnClickListener {
                        removeGroup(group.id!!)
                    }
                }
                contactGroupAdd.beGone()
                val showFields = config.showContactFields
                binding.contactGroupsAddNewHolder.beVisibleIf(showFields and SHOW_GROUPS_FIELD != 0)

                dividerContactGroup.setBackgroundColor(getProperTextColor())
            }
        }

        if (groups.isEmpty()) {
            ItemEditGroupBinding.inflate(layoutInflater, binding.contactGroupsHolder, false).apply {
                contactGroup.apply {
                    alpha = 0.5f
                    text = getString(R.string.no_groups)
                    setTextColor(getProperTextColor())
                }

                binding.contactGroupsHolder.addView(root)
                contactGroupRemove.beGone()
                binding.contactGroupsAddNewHolder.beGone()
                contactGroupAdd.beVisible()
                root.setOnClickListener {
                    showSelectGroupsDialog()
                }
                dividerContactGroup.beGone()
            }
        }
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactGroupsHolder.setBackgroundColor(white)
            binding.contactGroupsAddNewHolder.setBackgroundColor(white)
        }
    }

    private fun setupContactSource() {
        originalContactSource = contact!!.source
        getPublicContactSource(contact!!.source) {
            binding.contactSource.text = if (it == "") getString(R.string.phone_storage) else it
            setupContactSourceImage(it)
        }
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactSourceHolder.setBackgroundColor(white)
        }
        binding.contactSource.setTextColor(getProperPrimaryColor())
    }

    private fun setupNewContact() {
        originalContactSource = if (hasContactPermissions()) config.lastUsedContactSource else SMT_PRIVATE
        contact = getEmptyContact()
        getPublicContactSource(contact!!.source) {
            binding.contactSource.text = if (it == "") getString(R.string.phone_storage) else it
            setupContactSourceImage(it)
        }

        // if the last used contact source is not available anymore, use the first available one. Could happen at ejecting SIM card
        ContactsHelper(this).getSaveableContactSources { sources ->
            val sourceNames = sources.map { it.name }
            if (!sourceNames.contains(originalContactSource)) {
                originalContactSource = sourceNames.first()
                contact?.source = originalContactSource
                getPublicContactSource(contact!!.source) {
                    binding.contactSource.text = if (it == "") getString(R.string.phone_storage) else it
                    setupContactSourceImage(it)
                }
            }
        }
        binding.contactSource.setTextColor(getProperPrimaryColor())
    }

    private fun setupContactSourceImage(source: String) {
        binding.contactSourceImage.beGone()

        if (source.contains("gmail.com", true) || source.contains("googlemail.com", true)) {
            binding.contactSourceImage.setImageDrawable(getPackageDrawable("google"))
            binding.contactSourceImage.beVisible()
        }

        if (source == SMT_PRIVATE) {
            binding.contactSourceImage.setImageDrawable(getPackageDrawable(SMT_PRIVATE))
            binding.contactSourceImage.beVisible()
        }

        if (source.lowercase(Locale.getDefault()) == WHATSAPP) {
            binding.contactSourceImage.setImageDrawable(getPackageDrawable(WHATSAPP_PACKAGE))
            binding.contactSourceImage.beVisible()
        }

        if (source.lowercase(Locale.getDefault()) == SIGNAL) {
            binding.contactSourceImage.setImageDrawable(getPackageDrawable(SIGNAL_PACKAGE))
            binding.contactSourceImage.beVisible()
        }

        if (source.lowercase(Locale.getDefault()) == VIBER) {
            binding.contactSourceImage.setImageDrawable(getPackageDrawable(VIBER_PACKAGE))
            binding.contactSourceImage.beVisible()
        }

        if (source.lowercase(Locale.getDefault()) == TELEGRAM) {
            binding.contactSourceImage.setImageDrawable(getPackageDrawable(TELEGRAM_PACKAGE))
            binding.contactSourceImage.beVisible()
        }

        if (source.lowercase(Locale.getDefault()) == THREEMA) {
            binding.contactSourceImage.setImageDrawable(getPackageDrawable(THREEMA_PACKAGE))
            binding.contactSourceImage.beVisible()
        }
    }

    private fun setupTypePickers() {
        val getProperTextColor = getProperTextColor()
        val getProperPrimaryColor = getProperPrimaryColor()

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            binding.contactPrefix.setBackgroundColor(white)
            binding.dividerContactPrefix.setBackgroundColor(getProperTextColor)
            binding.contactFirstName.setBackgroundColor(white)
            binding.dividerContactFirstName.setBackgroundColor(getProperTextColor)
            binding.contactMiddleName.setBackgroundColor(white)
            binding.dividerContactMiddleName.setBackgroundColor(getProperTextColor)
            binding.contactSurname.setBackgroundColor(white)
            binding.dividerContactSurname.setBackgroundColor(getProperTextColor)
            binding.contactSuffix.setBackgroundColor(white)
            binding.dividerContactSuffix.setBackgroundColor(getProperTextColor)
            binding.contactNickname.setBackgroundColor(white)
            binding.dividerContactNickname.setBackgroundColor(getProperTextColor)
            binding.contactOrganizationCompany.setBackgroundColor(white)
            binding.dividerContactOrganizationCompany.setBackgroundColor(getProperTextColor)
            binding.contactOrganizationJobPosition.setBackgroundColor(white)
            binding.contactSourceHolder.setBackgroundColor(white)
        }

        if (contact!!.phoneNumbers.isEmpty()) {
            //binding.contactNumbersHolder.removeAllViews()
            val numberHolder = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(0))
            numberHolder.contactNumberType.apply {
                setTextColor(getProperPrimaryColor)
                setupPhoneNumberTypePicker(this, DEFAULT_PHONE_NUMBER_TYPE, "")
            }
            numberHolder.dividerVerticalContactNumber.setBackgroundColor(getProperTextColor)
            numberHolder.dividerContactNumber.setBackgroundColor(getProperTextColor)

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                binding.contactNumbersHolder.setBackgroundColor(white)
                binding.contactNumbersAddNewHolder.setBackgroundColor(white)
            }
        }

        if (contact!!.emails.isEmpty()) {
            //binding.contactEmailsHolder.removeAllViews()
            val emailHolder = ItemEditEmailBinding.bind(binding.contactEmailsHolder.getChildAt(0))
            emailHolder.contactEmailType.apply {
                setTextColor(getProperPrimaryColor)
                setupEmailTypePicker(this, DEFAULT_EMAIL_TYPE, "")
            }
            emailHolder.dividerVerticalContactEmail.setBackgroundColor(getProperTextColor)
            emailHolder.dividerContactEmail.setBackgroundColor(getProperTextColor)

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                binding.contactEmailsHolder.setBackgroundColor(white)
                binding.contactEmailsAddNewHolder.setBackgroundColor(white)
            }
        }

        if (contact!!.addresses.isEmpty()) {
            //binding.contactAddressesHolder.removeAllViews()
            val addressHolder = ItemEditAddressBinding.bind(binding.contactAddressesHolder.getChildAt(0))
            addressHolder.contactAddressType.apply {
                setTextColor(getProperPrimaryColor)
                setupAddressTypePicker(this, DEFAULT_ADDRESS_TYPE, "")
            }
            addressHolder.dividerVerticalContactAddress.setBackgroundColor(getProperTextColor)
            addressHolder.dividerContactAddress.setBackgroundColor(getProperTextColor)

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                binding.contactAddressesHolder.setBackgroundColor(white)
                binding.contactAddressesAddNewHolder.setBackgroundColor(white)
            }
        }

        if (contact!!.IMs.isEmpty()) {
            //binding.contactImsHolder.removeAllViews()
            val IMHolder = ItemEditImBinding.bind(binding.contactImsHolder.getChildAt(0))
            IMHolder.contactImType.apply {
                setTextColor(getProperPrimaryColor)
                setupIMTypePicker(this, DEFAULT_IM_TYPE, "")
            }
            IMHolder.dividerVerticalContactIm.setBackgroundColor(getProperTextColor)
            IMHolder.dividerContactIm.setBackgroundColor(getProperTextColor)

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                binding.contactImsHolder.setBackgroundColor(white)
                binding.contactImsAddNewHolder.setBackgroundColor(white)
            }
        }

        if (contact!!.events.isEmpty()) {
            //binding.contactEventsHolder.removeAllViews()
            val eventHolder = ItemEventBinding.bind(binding.contactEventsHolder.getChildAt(0))
            eventHolder.apply {
                setupEventTypePicker(this)
            }
            eventHolder.contactEventType.setTextColor(getProperPrimaryColor)
            eventHolder.dividerVerticalContactEvent.setBackgroundColor(getProperTextColor)
            eventHolder.dividerContactEvent.setBackgroundColor(getProperTextColor)

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                binding.contactEventsHolder.setBackgroundColor(white)
                binding.contactEventsAddNewHolder.setBackgroundColor(white)
            }
        }

        if (contact!!.relations.isEmpty()) {
            //binding.contactRelationsHolder.removeAllViews()
            val relationHolder = ItemEditRelationBinding.bind(binding.contactRelationsHolder.getChildAt(0))
            relationHolder.contactRelationType.apply {
                setTextColor(getProperPrimaryColor)
                setupRelationTypePicker(this, DEFAULT_RELATION_TYPE, "")
            }
            relationHolder.dividerVerticalContactRelation.setBackgroundColor(getProperTextColor)
            relationHolder.dividerContactRelation.setBackgroundColor(getProperTextColor)

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                binding.contactRelationsHolder.setBackgroundColor(white)
                binding.contactRelationsAddNewHolder.setBackgroundColor(white)
            }
        }

        if (contact!!.notes.isEmpty()) {
            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                binding.contactNotes.setBackgroundColor(white)
            }
        }

        if (contact!!.websites.isEmpty()) {
            binding.contactWebsitesHolder.removeAllViews()

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                binding.contactWebsitesHolder.setBackgroundColor(white)
                binding.contactWebsitesAddNewHolder.setBackgroundColor(white)
            }
        }

        if (contact!!.groups.isEmpty()) {
            val groupsHolder = ItemEditGroupBinding.bind(binding.contactGroupsHolder.getChildAt(0))
            groupsHolder.apply {
                contactGroup.setOnClickListener {
                    setupGroupsPicker(contactGroup)
                }
                contactGroupRemove.beGone()
                dividerContactGroup.beGone()
                contactGroupAdd.beVisible()
                //dividerContactGroup.setBackgroundColor(getProperTextColor)
            }
            binding.contactGroupsAddNewHolder.beGone()

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                binding.contactGroupsHolder.setBackgroundColor(white)
            }
        }
    }

    private fun setupPhoneNumberTypePicker(numberTypeField: TextView, type: Int, label: String) {
        numberTypeField.apply {
            text = getPhoneNumberTypeText(type, label)
            setOnClickListener {
                showNumberTypePicker(it as TextView)
            }
        }
    }

    private fun setupEmailTypePicker(emailTypeField: TextView, type: Int, label: String) {
        emailTypeField.apply {
            text = getEmailTypeText(type, label)
            setOnClickListener {
                showEmailTypePicker(it as TextView)
            }
        }
    }

    private fun setupAddressTypePicker(addressTypeField: TextView, type: Int, label: String) {
        addressTypeField.apply {
            text = getAddressTypeText(type, label)
            setOnClickListener {
                showAddressTypePicker(it as TextView)
            }
        }
    }

    private fun setupIMTypePicker(imTypeField: TextView, type: Int, label: String) {
        imTypeField.apply {
            text = getIMTypeText(type, label)
            setOnClickListener {
                showIMTypePicker(it as TextView)
            }
        }
    }

    private fun setupEventTypePicker(eventHolder: ItemEventBinding, type: Int = DEFAULT_EVENT_TYPE) {
        eventHolder.contactEventType.apply {
            setText(getEventTextId(type))
            setOnClickListener {
                showEventTypePicker(it as TextView)
            }
        }

        val eventField = eventHolder.contactEvent
        eventField.setOnClickListener {
            MyDatePickerDialog(this, eventField.tag?.toString() ?: "") { dateTag ->
                eventField.apply {
                    dateTag.getDateTimeFromDateString(true, this)
                    tag = dateTag
                    alpha = 1f
                }
            }
        }

        eventHolder.contactEventRemove.apply {
            setOnClickListener {
                resetContactEvent(eventField, this@apply)
                binding.contactEventsHolder.removeView(eventHolder.root)
            }
        }
    }

    private fun setupRelationTypePicker(relationTypeField: TextView, type: Int, label: String) {
        relationTypeField.apply {
            text = getRelationTypeText(type, label)
            setOnClickListener {
                showRelationTypePicker(it as TextView)
            }
        }
    }

    private fun showRelationTypePicker(relationTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(Relation.TYPE_CUSTOM, getString(com.goodwy.commons.R.string.custom)),
            RadioItem(Relation.TYPE_FRIEND, getString(com.goodwy.commons.R.string.relation_friend_g)), // 6
            RadioItem(Relation.TYPE_SPOUSE, getString(com.goodwy.commons.R.string.relation_spouse_g)), // 14
            RadioItem(ContactRelation.TYPE_HUSBAND, getString(com.goodwy.commons.R.string.relation_husband_g)), // 103
            RadioItem(ContactRelation.TYPE_WIFE, getString(com.goodwy.commons.R.string.relation_wife_g)), // 104
            RadioItem(Relation.TYPE_DOMESTIC_PARTNER, getString(com.goodwy.commons.R.string.relation_domestic_partner_g)), // 4
            RadioItem(Relation.TYPE_PARTNER, getString(com.goodwy.commons.R.string.relation_partner_g)), // 10
            RadioItem(ContactRelation.TYPE_CO_RESIDENT, getString(com.goodwy.commons.R.string.relation_co_resident_g)), // 56
            RadioItem(ContactRelation.TYPE_NEIGHBOR, getString(com.goodwy.commons.R.string.relation_neighbor_g)), // 57
            RadioItem(Relation.TYPE_PARENT, getString(com.goodwy.commons.R.string.relation_parent_g)), // 9
            RadioItem(Relation.TYPE_FATHER, getString(com.goodwy.commons.R.string.relation_father_g)), // 5
            RadioItem(Relation.TYPE_MOTHER, getString(com.goodwy.commons.R.string.relation_mother_g)), // 8
            RadioItem(Relation.TYPE_CHILD, getString(com.goodwy.commons.R.string.relation_child_g)), // 3
            RadioItem(ContactRelation.TYPE_SON, getString(com.goodwy.commons.R.string.relation_son_g)), // 105
            RadioItem(ContactRelation.TYPE_DAUGHTER, getString(com.goodwy.commons.R.string.relation_daughter_g)), // 106
            RadioItem(ContactRelation.TYPE_SIBLING, getString(com.goodwy.commons.R.string.relation_sibling_g)), // 58
            RadioItem(Relation.TYPE_BROTHER, getString(com.goodwy.commons.R.string.relation_brother_g)), // 2
            RadioItem(Relation.TYPE_SISTER, getString(com.goodwy.commons.R.string.relation_sister_g)), // 13
            RadioItem(ContactRelation.TYPE_GRANDPARENT, getString(com.goodwy.commons.R.string.relation_grandparent_g)), // 107
            RadioItem(ContactRelation.TYPE_GRANDFATHER, getString(com.goodwy.commons.R.string.relation_grandfather_g)), // 108
            RadioItem(ContactRelation.TYPE_GRANDMOTHER, getString(com.goodwy.commons.R.string.relation_grandmother_g)), // 109
            RadioItem(ContactRelation.TYPE_GRANDCHILD, getString(com.goodwy.commons.R.string.relation_grandchild_g)), // 110
            RadioItem(ContactRelation.TYPE_GRANDSON, getString(com.goodwy.commons.R.string.relation_grandson_g)), // 111
            RadioItem(ContactRelation.TYPE_GRANDDAUGHTER, getString(com.goodwy.commons.R.string.relation_granddaughter_g)), // 112
            RadioItem(ContactRelation.TYPE_UNCLE, getString(com.goodwy.commons.R.string.relation_uncle_g)), // 113
            RadioItem(ContactRelation.TYPE_AUNT, getString(com.goodwy.commons.R.string.relation_aunt_g)), // 114
            RadioItem(ContactRelation.TYPE_NEPHEW, getString(com.goodwy.commons.R.string.relation_nephew_g)), // 115
            RadioItem(ContactRelation.TYPE_NIECE, getString(com.goodwy.commons.R.string.relation_niece_g)), // 116
            RadioItem(ContactRelation.TYPE_FATHER_IN_LAW, getString(com.goodwy.commons.R.string.relation_father_in_law_g)), // 117
            RadioItem(ContactRelation.TYPE_MOTHER_IN_LAW, getString(com.goodwy.commons.R.string.relation_mother_in_law_g)), // 118
            RadioItem(ContactRelation.TYPE_SON_IN_LAW, getString(com.goodwy.commons.R.string.relation_son_in_law_g)), // 119
            RadioItem(ContactRelation.TYPE_DAUGHTER_IN_LAW, getString(com.goodwy.commons.R.string.relation_daughter_in_law_g)), // 120
            RadioItem(ContactRelation.TYPE_BROTHER_IN_LAW, getString(com.goodwy.commons.R.string.relation_brother_in_law_g)), // 121
            RadioItem(ContactRelation.TYPE_SISTER_IN_LAW, getString(com.goodwy.commons.R.string.relation_sister_in_law_g)), // 122
            RadioItem(Relation.TYPE_RELATIVE, getString(com.goodwy.commons.R.string.relation_relative_g)), // 12
            RadioItem(ContactRelation.TYPE_KIN, getString(com.goodwy.commons.R.string.relation_kin_g)), // 59

            RadioItem(ContactRelation.TYPE_MUSE, getString(com.goodwy.commons.R.string.relation_muse_g)), // 60
            RadioItem(ContactRelation.TYPE_CRUSH, getString(com.goodwy.commons.R.string.relation_crush_g)), // 61
            RadioItem(ContactRelation.TYPE_DATE, getString(com.goodwy.commons.R.string.relation_date_g)), // 62
            RadioItem(ContactRelation.TYPE_SWEETHEART, getString(com.goodwy.commons.R.string.relation_sweetheart_g)), // 63

            RadioItem(ContactRelation.TYPE_CONTACT, getString(com.goodwy.commons.R.string.relation_contact_g)), // 51
            RadioItem(ContactRelation.TYPE_ACQUAINTANCE, getString(com.goodwy.commons.R.string.relation_acquaintance_g)), // 52
            RadioItem(ContactRelation.TYPE_MET, getString(com.goodwy.commons.R.string.relation_met_g)), // 53
            RadioItem(Relation.TYPE_REFERRED_BY, getString(com.goodwy.commons.R.string.relation_referred_by_g)), // 11
            RadioItem(ContactRelation.TYPE_AGENT, getString(com.goodwy.commons.R.string.relation_agent_g)), // 64

            RadioItem(ContactRelation.TYPE_COLLEAGUE, getString(com.goodwy.commons.R.string.relation_colleague_g)), // 55
            RadioItem(ContactRelation.TYPE_CO_WORKER, getString(com.goodwy.commons.R.string.relation_co_worker_g)), // 54
            RadioItem(ContactRelation.TYPE_SUPERIOR, getString(com.goodwy.commons.R.string.relation_superior_g)), // 101
            RadioItem(ContactRelation.TYPE_SUBORDINATE, getString(com.goodwy.commons.R.string.relation_subordinate_g)), // 102
            RadioItem(Relation.TYPE_MANAGER, getString(com.goodwy.commons.R.string.relation_manager_g)), // 7
            RadioItem(Relation.TYPE_ASSISTANT, getString(com.goodwy.commons.R.string.relation_assistant_g)), // 1

            RadioItem(ContactRelation.TYPE_ME, getString(com.goodwy.commons.R.string.relation_me_g)), // 66
            RadioItem(ContactRelation.TYPE_EMERGENCY, getString(com.goodwy.commons.R.string.relation_emergency_g)) // 65
        )
        val currentRelationTypeId = getRelationTypeId(relationTypeField.value)
        RadioGroupDialog(this, items, currentRelationTypeId) {
            if (it as Int == CommonDataKinds.Relation.TYPE_CUSTOM) {
                CustomLabelDialog(this) {
                    relationTypeField.text = it
                }
            } else {
                relationTypeField.text = getRelationTypeText(it, "")
            }
        }
    }

    private fun setupGroupsPicker(groupTitleField: TextView, group: Group? = null) {
        groupTitleField.apply {
            text = group?.title ?: getString(R.string.no_groups)
            alpha = if (group == null) 0.5f else 1f
//            setOnClickListener {
//                showSelectGroupsDialog()
//            }
            showSelectGroupsDialog()
        }
    }

    private fun resetContactEvent(contactEvent: TextView, removeContactEventButton: ImageView) {
        contactEvent.apply {
            text = getString(com.goodwy.commons.R.string.unknown)
            tag = ""
            alpha = 0.5f
        }
        removeContactEventButton.beGone()
    }

    private fun removeGroup(id: Long) {
        contact!!.groups = contact!!.groups.filter { it.id != id } as ArrayList<Group>
        setupGroups()
    }

    private fun showNumberTypePicker(numberTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(Phone.TYPE_CUSTOM, getString(Phone.getTypeLabelResource(Phone.TYPE_CUSTOM))),
            RadioItem(Phone.TYPE_MOBILE, getString(com.goodwy.commons.R.string.mobile)),
            RadioItem(Phone.TYPE_HOME, getString(com.goodwy.commons.R.string.home)),
            RadioItem(Phone.TYPE_WORK, getString(com.goodwy.commons.R.string.work)),
            RadioItem(Phone.TYPE_MAIN, getString(com.goodwy.commons.R.string.main_number)),
            RadioItem(Phone.TYPE_FAX_WORK, getString(com.goodwy.commons.R.string.work_fax)),
            RadioItem(Phone.TYPE_FAX_HOME, getString(com.goodwy.commons.R.string.home_fax)),
            RadioItem(Phone.TYPE_PAGER, getString(com.goodwy.commons.R.string.pager)),
            RadioItem(Phone.TYPE_OTHER, getString(com.goodwy.commons.R.string.other))
        )

        val currentNumberTypeId = getPhoneNumberTypeId(numberTypeField.value)
        RadioGroupDialog(this, items, currentNumberTypeId) {
            if (it as Int == Phone.TYPE_CUSTOM) {
                CustomLabelDialog(this) {
                    numberTypeField.text = it
                }
            } else {
                numberTypeField.text = getPhoneNumberTypeText(it, "")
            }
        }
    }

    private fun showEmailTypePicker(emailTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(CommonDataKinds.Email.TYPE_CUSTOM, getString(CommonDataKinds.Email.getTypeLabelResource(CommonDataKinds.Email.TYPE_CUSTOM))),
            RadioItem(CommonDataKinds.Email.TYPE_HOME, getString(com.goodwy.commons.R.string.home)),
            RadioItem(CommonDataKinds.Email.TYPE_WORK, getString(com.goodwy.commons.R.string.work)),
            RadioItem(CommonDataKinds.Email.TYPE_MOBILE, getString(com.goodwy.commons.R.string.mobile)),
            RadioItem(CommonDataKinds.Email.TYPE_OTHER, getString(com.goodwy.commons.R.string.other))
        )

        val currentEmailTypeId = getEmailTypeId(emailTypeField.value)
        RadioGroupDialog(this, items, currentEmailTypeId) {
            if (it as Int == CommonDataKinds.Email.TYPE_CUSTOM) {
                CustomLabelDialog(this) {
                    emailTypeField.text = it
                }
            } else {
                emailTypeField.text = getEmailTypeText(it, "")
            }
        }
    }

    private fun showAddressTypePicker(addressTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(StructuredPostal.TYPE_CUSTOM, getString(StructuredPostal.getTypeLabelResource(StructuredPostal.TYPE_CUSTOM))),
            RadioItem(StructuredPostal.TYPE_HOME, getString(com.goodwy.commons.R.string.home)),
            RadioItem(StructuredPostal.TYPE_WORK, getString(com.goodwy.commons.R.string.work)),
            RadioItem(StructuredPostal.TYPE_OTHER, getString(com.goodwy.commons.R.string.other))
        )

        val currentAddressTypeId = getAddressTypeId(addressTypeField.value)
        RadioGroupDialog(this, items, currentAddressTypeId) {
            if (it as Int == StructuredPostal.TYPE_CUSTOM) {
                CustomLabelDialog(this) {
                    addressTypeField.text = it
                }
            } else {
                addressTypeField.text = getAddressTypeText(it, "")
            }
        }
    }

    private fun showIMTypePicker(imTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(Im.PROTOCOL_AIM, getString(R.string.aim)),
            RadioItem(Im.PROTOCOL_MSN, getString(R.string.windows_live)),
            RadioItem(Im.PROTOCOL_YAHOO, getString(R.string.yahoo)),
            RadioItem(Im.PROTOCOL_SKYPE, getString(R.string.skype)),
            RadioItem(Im.PROTOCOL_QQ, getString(R.string.qq)),
            RadioItem(Im.PROTOCOL_GOOGLE_TALK, getString(R.string.hangouts)),
            RadioItem(Im.PROTOCOL_ICQ, getString(R.string.icq)),
            RadioItem(Im.PROTOCOL_JABBER, getString(R.string.jabber)),
            RadioItem(Im.PROTOCOL_CUSTOM, getString(com.goodwy.commons.R.string.custom))
        )

        val currentIMTypeId = getIMTypeId(imTypeField.value)
        RadioGroupDialog(this, items, currentIMTypeId) {
            if (it as Int == Im.PROTOCOL_CUSTOM) {
                CustomLabelDialog(this) {
                    imTypeField.text = it
                }
            } else {
                imTypeField.text = getIMTypeText(it, "")
            }
        }
    }

    private fun showEventTypePicker(eventTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(CommonDataKinds.Event.TYPE_ANNIVERSARY, getString(com.goodwy.commons.R.string.anniversary)),
            RadioItem(CommonDataKinds.Event.TYPE_BIRTHDAY, getString(com.goodwy.commons.R.string.birthday)),
            RadioItem(CommonDataKinds.Event.TYPE_OTHER, getString(com.goodwy.commons.R.string.other))
        )

        val currentEventTypeId = getEventTypeId(eventTypeField.value)
        RadioGroupDialog(this, items, currentEventTypeId) {
            eventTypeField.setText(getEventTextId(it as Int))
        }
    }

    private fun showSelectGroupsDialog() {
        SelectGroupsDialog(this@EditContactActivity, contact!!.groups) {
            contact!!.groups = it
            setupGroups()
        }
    }

    private fun showSelectContactSourceDialog() {
        showContactSourcePicker(contact!!.source) { it ->
            contact!!.source = if (it == getString(R.string.phone_storage_hidden)) SMT_PRIVATE else it
            getPublicContactSource(it) {
                binding.contactSource.text = if (it == "") getString(R.string.phone_storage) else it
                setupContactSourceImage(it)
            }
        }
    }

    private fun saveContact() {
        if (isSaving || contact == null) {
            return
        }

        val contactFields = arrayListOf(
            binding.contactPrefix, binding.contactFirstName, binding.contactMiddleName, binding.contactSurname, binding.contactSuffix, binding.contactNickname,
            binding.contactNotes, binding.contactOrganizationCompany, binding.contactOrganizationJobPosition
        )

        if (contactFields.all { it.value.isEmpty() }) {
            if (currentContactPhotoPath.isEmpty() &&
                getFilledPhoneNumbers().isEmpty() &&
                getFilledEmails().isEmpty() &&
                getFilledAddresses().isEmpty() &&
                getFilledIMs().isEmpty() &&
                getFilledEvents().isEmpty() &&
                getFilledRelations().isEmpty() &&
                getFilledWebsites().isEmpty()
            ) {
                toast(R.string.fields_empty)
                return
            }
        }

        val contactValues = fillContactValues()

        val oldPhotoUri = contact!!.photoUri
        val oldPrimary = contact!!.phoneNumbers.find { it.isPrimary }
        val newPrimary = contactValues.phoneNumbers.find { it.isPrimary }
        val primaryState = Pair(oldPrimary, newPrimary)

        contact = contactValues

        ensureBackgroundThread {
            config.lastUsedContactSource = contact!!.source
            when {
                contact!!.id == 0 -> insertNewContact(false)
                originalContactSource != contact!!.source -> insertNewContact(true)
                else -> {
                    val photoUpdateStatus = getPhotoUpdateStatus(oldPhotoUri, contact!!.photoUri)
                    updateContact(photoUpdateStatus, primaryState)
                }
            }
        }
    }

    private fun fillContactValues(): Contact {
        val filledPhoneNumbers = getFilledPhoneNumbers()
        val filledEmails = getFilledEmails()
        val filledAddresses = getFilledAddresses()
        val filledIMs = getFilledIMs()
        val filledEvents = getFilledEvents()
        val filledWebsites = getFilledWebsites()
        val filledRelations = getFilledRelations()

        val newContact = contact!!.copy(
            prefix = binding.contactPrefix.value,
            firstName = binding.contactFirstName.value,
            middleName = binding.contactMiddleName.value,
            surname = binding.contactSurname.value,
            suffix = binding.contactSuffix.value,
            nickname = binding.contactNickname.value,
            photoUri = currentContactPhotoPath,
            phoneNumbers = filledPhoneNumbers,
            emails = filledEmails,
            addresses = filledAddresses,
            IMs = filledIMs,
            events = filledEvents,
            starred = if (isContactStarred()) 1 else 0,
            notes = binding.contactNotes.value,
            websites = filledWebsites,
            relations = filledRelations,
        )

        val company = binding.contactOrganizationCompany.value
        val jobPosition = binding.contactOrganizationJobPosition.value
        newContact.organization = Organization(company, jobPosition)
        return newContact
    }

    private fun getFilledPhoneNumbers(): ArrayList<PhoneNumber> {
        val phoneNumbers = ArrayList<PhoneNumber>()
        val numbersCount = binding.contactNumbersHolder.childCount
        for (i in 0 until numbersCount) {
            val numberHolder = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(i))
            val number = numberHolder.contactNumber.value
            val numberType = getPhoneNumberTypeId(numberHolder.contactNumberType.value)
            val numberLabel = if (numberType == Phone.TYPE_CUSTOM) numberHolder.contactNumberType.value else ""

            if (number.isNotEmpty()) {
                var normalizedNumber = number.normalizePhoneNumber()

                // fix a glitch when onBackPressed the app thinks that a number changed because we fetched
                // normalized number +421903123456, then at getting it from the input field we get 0903123456, can happen at WhatsApp contacts
                val fetchedNormalizedNumber = numberHolder.contactNumber.tag?.toString() ?: ""
                if (PhoneNumberUtils.compare(number.normalizePhoneNumber(), fetchedNormalizedNumber)) {
                    normalizedNumber = fetchedNormalizedNumber
                }

                val isPrimary = numberHolder.defaultToggleIcon.tag == 1
                phoneNumbers.add(PhoneNumber(number, numberType, numberLabel, normalizedNumber, isPrimary))
            }
        }
        return phoneNumbers
    }

    private fun getFilledEmails(): ArrayList<Email> {
        val emails = ArrayList<Email>()
        val emailsCount = binding.contactEmailsHolder.childCount
        for (i in 0 until emailsCount) {
            val emailHolder = ItemEditEmailBinding.bind(binding.contactEmailsHolder.getChildAt(i))
            val email = emailHolder.contactEmail.value
            val emailType = getEmailTypeId(emailHolder.contactEmailType.value)
            val emailLabel = if (emailType == CommonDataKinds.Email.TYPE_CUSTOM) emailHolder.contactEmailType.value else ""

            if (email.isNotEmpty()) {
                emails.add(Email(email, emailType, emailLabel))
            }
        }
        return emails
    }

    private fun getFilledAddresses(): ArrayList<Address> {
        val addresses = ArrayList<Address>()
        val addressesCount = binding.contactAddressesHolder.childCount
        for (i in 0 until addressesCount) {
            val addressHolder = ItemEditAddressBinding.bind(binding.contactAddressesHolder.getChildAt(i))
            val address = addressHolder.contactAddress.value
            val addressType = getAddressTypeId(addressHolder.contactAddressType.value)
            val addressLabel = if (addressType == StructuredPostal.TYPE_CUSTOM) addressHolder.contactAddressType.value else ""

            if (address.isNotEmpty()) {
                addresses.add(Address(address, addressType, addressLabel))
            }
        }
        return addresses
    }

    private fun getFilledIMs(): ArrayList<IM> {
        val IMs = ArrayList<IM>()
        val IMsCount = binding.contactImsHolder.childCount
        for (i in 0 until IMsCount) {
            val IMsHolder = ItemEditImBinding.bind(binding.contactImsHolder.getChildAt(i))
            val IM = IMsHolder.contactIm.value
            val IMType = getIMTypeId(IMsHolder.contactImType.value)
            val IMLabel = if (IMType == Im.PROTOCOL_CUSTOM) IMsHolder.contactImType.value else ""

            if (IM.isNotEmpty()) {
                IMs.add(IM(IM, IMType, IMLabel))
            }
        }
        return IMs
    }

    private fun getFilledEvents(): ArrayList<Event> {
        val unknown = getString(com.goodwy.commons.R.string.unknown)
        val events = ArrayList<Event>()
        val eventsCount = binding.contactEventsHolder.childCount
        for (i in 0 until eventsCount) {
            val eventHolder = ItemEventBinding.bind(binding.contactEventsHolder.getChildAt(i))
            val event = eventHolder.contactEvent.value
            val eventType = getEventTypeId(eventHolder.contactEventType.value)

            if (event.isNotEmpty() && event != unknown) {
                events.add(Event(eventHolder.contactEvent.tag.toString(), eventType))
            }
        }
        return events
    }

    private fun getFilledRelations(): ArrayList<ContactRelation> {
        val relations = ArrayList<ContactRelation>()
        val relationsCount = binding.contactRelationsHolder.childCount
        for (i in 0 until relationsCount) {
            val relationHolder = ItemEditRelationBinding.bind(binding.contactRelationsHolder.getChildAt(i))
            val name: String = relationHolder.contactRelation.value
            if (name.isNotEmpty()) {
                var label = relationHolder.contactRelationType.value.trim()
                val type = getRelationTypeId(label)
                if (type != ContactRelation.TYPE_CUSTOM) {
                    label = ""
                }
                relations.add(ContactRelation(name, type, label))
            }
        }
        return relations
    }

    private fun getFilledWebsites(): ArrayList<String> {
        val websites = ArrayList<String>()
        val websitesCount = binding.contactWebsitesHolder.childCount
        for (i in 0 until websitesCount) {
            val websiteHolder = ItemEditWebsiteBinding.bind(binding.contactWebsitesHolder.getChildAt(i))
            val website = websiteHolder.contactWebsite.value
            if (website.isNotEmpty()) {
                websites.add(website)
            }
        }
        return websites
    }

    private fun insertNewContact(deleteCurrentContact: Boolean) {
        isSaving = true
        if (!deleteCurrentContact) {
            toast(R.string.inserting)
        }

        if (ContactsHelper(this@EditContactActivity).insertContact(contact!!)) {
            if (deleteCurrentContact) {
                contact!!.source = originalContactSource
                ContactsHelper(this).deleteContact(contact!!, false) {
                    setResult(Activity.RESULT_OK)
                    hideKeyboard()
                    finish()
                }
            } else {
                setResult(Activity.RESULT_OK)
                hideKeyboard()
                finish()
            }
        } else {
            toast(com.goodwy.commons.R.string.unknown_error_occurred)
        }
    }

    private fun updateContact(photoUpdateStatus: Int, primaryState: Pair<PhoneNumber?, PhoneNumber?>) {
        isSaving = true
        if (ContactsHelper(this@EditContactActivity).updateContact(contact!!, photoUpdateStatus)) {
            val status = getPrimaryNumberStatus(primaryState.first, primaryState.second)
            if (status != PrimaryNumberStatus.UNCHANGED) {
                updateDefaultNumberForDuplicateContacts(primaryState, status) {
                    setResult(Activity.RESULT_OK)
                    hideKeyboard()
                    finish()
                }
            } else {
                setResult(Activity.RESULT_OK)
                hideKeyboard()
                finish()
            }
        } else {
            toast(com.goodwy.commons.R.string.unknown_error_occurred)
        }
    }

    private fun updateDefaultNumberForDuplicateContacts(
        toggleState: Pair<PhoneNumber?, PhoneNumber?>,
        primaryStatus: PrimaryNumberStatus,
        callback: () -> Unit
    ) {
        val contactsHelper = ContactsHelper(this)

        contactsHelper.getDuplicatesOfContact(contact!!, false) { contacts ->
            ensureBackgroundThread {
                val displayContactSources = getVisibleContactSources()
                contacts.filter { displayContactSources.contains(it.source) }.forEach { contact ->
                    val duplicate = contactsHelper.getContactWithId(contact.id, contact.isPrivate())
                    if (duplicate != null) {
                        if (primaryStatus == PrimaryNumberStatus.UNSTARRED) {
                            val number = duplicate.phoneNumbers.find { it.normalizedNumber == toggleState.first!!.normalizedNumber }
                            number?.isPrimary = false
                        } else if (primaryStatus == PrimaryNumberStatus.STARRED) {
                            val number = duplicate.phoneNumbers.find { it.normalizedNumber == toggleState.second!!.normalizedNumber }
                            if (number != null) {
                                duplicate.phoneNumbers.forEach {
                                    it.isPrimary = false
                                }
                                number.isPrimary = true
                            }
                        }

                        contactsHelper.updateContact(duplicate, PHOTO_UNCHANGED)
                    }
                }

                runOnUiThread {
                    callback.invoke()
                }
            }
        }
    }

    private fun getPrimaryNumberStatus(oldPrimary: PhoneNumber?, newPrimary: PhoneNumber?): PrimaryNumberStatus {
        return if (oldPrimary != null && newPrimary != null && oldPrimary != newPrimary) {
            PrimaryNumberStatus.STARRED
        } else if (oldPrimary == null && newPrimary != null) {
            PrimaryNumberStatus.STARRED
        } else if (oldPrimary != null && newPrimary == null) {
            PrimaryNumberStatus.UNSTARRED
        } else {
            PrimaryNumberStatus.UNCHANGED
        }
    }

    private fun getPhotoUpdateStatus(oldUri: String, newUri: String): Int {
        return if (oldUri.isEmpty() && newUri.isNotEmpty()) {
            PHOTO_ADDED
        } else if (oldUri.isNotEmpty() && newUri.isEmpty()) {
            PHOTO_REMOVED
        } else if (oldUri != newUri) {
            PHOTO_CHANGED
        } else {
            PHOTO_UNCHANGED
        }
    }

    private fun addNewPhoneNumberField() {
        val numberHolder = ItemEditPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false)
        updateTextColors(numberHolder.root)
        setupPhoneNumberTypePicker(numberHolder.contactNumberType, DEFAULT_PHONE_NUMBER_TYPE, "")
        binding.contactNumbersHolder.addView(numberHolder.root)
        binding.contactNumbersHolder.onGlobalLayout {
            numberHolder.contactNumber.requestFocus()
            showKeyboard(numberHolder.contactNumber)
        }

        numberHolder.apply {
            val getProperTextColor = getProperTextColor()
            dividerVerticalContactNumber.setBackgroundColor(getProperTextColor)
            dividerContactNumber.setBackgroundColor(getProperTextColor)
            contactNumberType.setTextColor(getProperPrimaryColor())
            contactNumberRemove.apply {
                beVisible()
                setOnClickListener {
                    binding.contactNumbersHolder.removeView(numberHolder.root)
                    hideKeyboard()
                }
            }
        }
        numberHolder.defaultToggleIcon.tag = 0
        initNumberHolders()
    }

    private fun addNewEmailField() {
        val emailHolder = ItemEditEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false)
        updateTextColors(emailHolder.root)
        setupEmailTypePicker(emailHolder.contactEmailType, DEFAULT_EMAIL_TYPE, "")
        binding.contactEmailsHolder.addView(emailHolder.root)
        binding.contactEmailsHolder.onGlobalLayout {
            emailHolder.contactEmail.requestFocus()
            showKeyboard(emailHolder.contactEmail)
        }

        emailHolder.apply {
            val getProperTextColor = getProperTextColor()
            dividerVerticalContactEmail.setBackgroundColor(getProperTextColor)
            dividerContactEmail.setBackgroundColor(getProperTextColor)
            contactEmailType.setTextColor(getProperPrimaryColor())
            contactEmailRemove.apply {
                beVisible()
                setOnClickListener {
                    binding.contactEmailsHolder.removeView(emailHolder.root)
                    hideKeyboard()
                }
            }
        }
    }

    private fun addNewAddressField() {
        val addressHolder = ItemEditAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false)
        updateTextColors(addressHolder.root)
        setupAddressTypePicker(addressHolder.contactAddressType, DEFAULT_ADDRESS_TYPE, "")
        binding.contactAddressesHolder.addView(addressHolder.root)
        binding.contactAddressesHolder.onGlobalLayout {
            addressHolder.contactAddress.requestFocus()
            showKeyboard(addressHolder.contactAddress)
        }

        addressHolder.apply {
            val getProperTextColor = getProperTextColor()
            dividerVerticalContactAddress.setBackgroundColor(getProperTextColor)
            dividerContactAddress.setBackgroundColor(getProperTextColor)
            contactAddressType.setTextColor(getProperPrimaryColor())
            contactAddressRemove.apply {
                beVisible()
                setOnClickListener {
                    binding.contactAddressesHolder.removeView(addressHolder.root)
                    hideKeyboard()
                }
            }
        }
    }

    private fun addNewIMField() {
        val IMHolder = ItemEditImBinding.inflate(layoutInflater, binding.contactImsHolder, false)
        updateTextColors(IMHolder.root)
        setupIMTypePicker(IMHolder.contactImType, DEFAULT_IM_TYPE, "")
        binding.contactImsHolder.addView(IMHolder.root)
        binding.contactImsHolder.onGlobalLayout {
            IMHolder.contactIm.requestFocus()
            showKeyboard(IMHolder.contactIm)
        }

        IMHolder.apply {
            val getProperTextColor = getProperTextColor()
            dividerVerticalContactIm.setBackgroundColor(getProperTextColor)
            dividerContactIm.setBackgroundColor(getProperTextColor)
            contactImType.setTextColor(getProperPrimaryColor())
            contactImRemove.apply {
                beVisible()
                setOnClickListener {
                    binding.contactImsHolder.removeView(IMHolder.root)
                    hideKeyboard()
                }
            }
        }
    }

    private fun addNewEventField() {
        val eventHolder = ItemEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false)
        updateTextColors(eventHolder.root)
        setupEventTypePicker(eventHolder)
        binding.contactEventsHolder.addView(eventHolder.root)

        eventHolder.apply {
            val getProperTextColor = getProperTextColor()
            dividerVerticalContactEvent.setBackgroundColor(getProperTextColor)
            dividerContactEvent.setBackgroundColor(getProperTextColor)
            contactEventType.setTextColor(getProperPrimaryColor())
            contactEventRemove.apply {
                beVisible()
                setOnClickListener {
                    binding.contactEventsHolder.removeView(eventHolder.root)
                }
            }
        }
    }

    private fun addNewRelationField() {
        val relationHolder = ItemEditRelationBinding.inflate(layoutInflater, binding.contactRelationsHolder, false)
        updateTextColors(relationHolder.root)
        setupRelationTypePicker(relationHolder.contactRelationType, DEFAULT_RELATION_TYPE, "")
        binding.contactRelationsHolder.addView(relationHolder.root)
        binding.contactRelationsHolder.onGlobalLayout {
            relationHolder.contactRelation.requestFocus()
            showKeyboard(relationHolder.contactRelation)
        }

        relationHolder.apply {
            val getProperTextColor = getProperTextColor()
            dividerVerticalContactRelation.setBackgroundColor(getProperTextColor)
            dividerContactRelation.setBackgroundColor(getProperTextColor)
            contactRelationType.setTextColor(getProperPrimaryColor())
            contactRelationRemove.apply {
                beVisible()
                setOnClickListener {
                    binding.contactRelationsHolder.removeView(relationHolder.root)
                    hideKeyboard()
                }
            }
        }
    }

    private fun toggleFavorite() {
        val isStarred = isContactStarred()
        binding.contactToggleFavorite.apply {
            setImageDrawable(getStarDrawable(!isStarred))
            tag = if (isStarred) 0 else 1

            setOnLongClickListener { toast(R.string.toggle_favorite); true; }
        }
    }

    private fun addNewWebsiteField() {
        val websitesHolder = ItemEditWebsiteBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false)
        updateTextColors(websitesHolder.root)
        binding.contactWebsitesHolder.addView(websitesHolder.root)
        binding.contactWebsitesHolder.onGlobalLayout {
            websitesHolder.contactWebsite.requestFocus()
            showKeyboard(websitesHolder.contactWebsite)
        }
        websitesHolder.apply {
            dividerContactWebsite.setBackgroundColor(getProperTextColor())
            contactWebsiteRemove.apply {
                beVisible()
                setOnClickListener {
                    binding.contactWebsitesHolder.removeView(websitesHolder.root)
                    hideKeyboard()
                }
            }
        }
    }

    private fun isContactStarred() = binding.contactToggleFavorite.tag == 1

    private fun getStarDrawable(on: Boolean) =
        resources.getDrawable(if (on) com.goodwy.commons.R.drawable.ic_star_vector else com.goodwy.commons.R.drawable.ic_star_outline_vector)

    private fun trySetPhotoRecommendation() {
        val simpleGallery = "com.goodwy.gallery"
        val simpleGalleryDebug = "com.goodwy.gallery.debug"
        if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleGallery) && !isPackageInstalled(simpleGalleryDebug))) {
            NewAppDialog(this, simpleGallery, getString(com.goodwy.commons.R.string.recommendation_dialog_gallery_g), getString(com.goodwy.commons.R.string.right_gallery),
                AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_gallery)) {
                trySetPhoto()
            }
        } else {
            trySetPhoto()
        }
    }

    private fun trySetPhoto() {
        val items = arrayListOf(
            RadioItem(TAKE_PHOTO, getString(com.goodwy.commons.R.string.take_photo)),
            RadioItem(CHOOSE_PHOTO, getString(com.goodwy.commons.R.string.choose_photo))
        )

        if (currentContactPhotoPath.isNotEmpty() || contact!!.photo != null) {
            items.add(RadioItem(REMOVE_PHOTO, getString(R.string.remove_photo)))
        }

        RadioGroupDialog(this, items) {
            when (it as Int) {
                TAKE_PHOTO -> startTakePhotoIntent()
                CHOOSE_PHOTO -> startChoosePhotoIntent()
                else -> {
                    showPhotoPlaceholder(binding.topDetails.contactPhoto)
                    binding.contactPhotoBottomShadow.beGone()
                }
            }
        }
    }

    private fun parseIntentData(data: ArrayList<ContentValues>) {
        data.forEach {
            when (it.get(StructuredName.MIMETYPE)) {
                CommonDataKinds.Email.CONTENT_ITEM_TYPE -> parseEmail(it)
                StructuredPostal.CONTENT_ITEM_TYPE -> parseAddress(it)
                CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> parseOrganization(it)
                CommonDataKinds.Event.CONTENT_ITEM_TYPE -> parseEvent(it)
                Relation.CONTENT_ITEM_TYPE -> parseRelation(it)
                Website.CONTENT_ITEM_TYPE -> parseWebsite(it)
                Note.CONTENT_ITEM_TYPE -> parseNote(it)
            }
        }
    }

    private fun parseEmail(contentValues: ContentValues) {
        val type = contentValues.getAsInteger(CommonDataKinds.Email.DATA2) ?: DEFAULT_EMAIL_TYPE
        val emailValue = contentValues.getAsString(CommonDataKinds.Email.DATA1) ?: return
        val email = Email(emailValue, type, "")
        contact!!.emails.add(email)
    }

    private fun parseAddress(contentValues: ContentValues) {
        val type = contentValues.getAsInteger(StructuredPostal.DATA2) ?: DEFAULT_ADDRESS_TYPE
        val addressValue = contentValues.getAsString(StructuredPostal.DATA4)
            ?: contentValues.getAsString(StructuredPostal.DATA1) ?: return
        val address = Address(addressValue, type, "")
        contact!!.addresses.add(address)
    }

    private fun parseOrganization(contentValues: ContentValues) {
        val company = contentValues.getAsString(CommonDataKinds.Organization.DATA1) ?: ""
        val jobPosition = contentValues.getAsString(CommonDataKinds.Organization.DATA4) ?: ""
        contact!!.organization = Organization(company, jobPosition)
    }

    private fun parseEvent(contentValues: ContentValues) {
        val type = contentValues.getAsInteger(CommonDataKinds.Event.DATA2) ?: DEFAULT_EVENT_TYPE
        val eventValue = contentValues.getAsString(CommonDataKinds.Event.DATA1) ?: return
        val event = Event(eventValue, type)
        contact!!.events.add(event)
    }

    private fun parseRelation(contentValues: ContentValues) {
        val type = contentValues.getAsInteger(Relation.DATA2) ?: DEFAULT_RELATION_TYPE
        val relationValue = contentValues.getAsString(Relation.DATA1) ?: return
        val relation = ContactRelation(relationValue, type, "")
        contact!!.relations.add(relation)
    }

    private fun parseWebsite(contentValues: ContentValues) {
        val website = contentValues.getAsString(Website.DATA1) ?: return
        contact!!.websites.add(website)
    }

    private fun parseNote(contentValues: ContentValues) {
        val note = contentValues.getAsString(Note.DATA1) ?: return
        contact!!.notes = note
    }

    private fun startTakePhotoIntent() {
        hideKeyboard()
        val uri = getCachePhotoUri()
        lastPhotoIntentUri = uri
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)

            try {
                startActivityForResult(this, INTENT_TAKE_PHOTO)
            } catch (e: ActivityNotFoundException) {
                toast(com.goodwy.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun startChoosePhotoIntent() {
        hideKeyboard()
        val uri = getCachePhotoUri()
        lastPhotoIntentUri = uri
        Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
            clipData = ClipData("Attachment", arrayOf("text/uri-list"), ClipData.Item(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            putExtra(MediaStore.EXTRA_OUTPUT, uri)

            try {
                startActivityForResult(this, INTENT_CHOOSE_PHOTO)
            } catch (e: ActivityNotFoundException) {
                toast(com.goodwy.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    override fun customRingtoneSelected(ringtonePath: String) {
        contact!!.ringtone = ringtonePath
        binding.contactRingtone.text = ringtonePath.getFilenameFromPath()
    }

    override fun systemRingtoneSelected(uri: Uri?) {
        contact!!.ringtone = uri?.toString() ?: ""
        val contactRingtone = RingtoneManager.getRingtone(this, uri)
        binding.contactRingtone.text = contactRingtone.getTitle(this)
    }

    private fun getPhoneNumberTypeId(value: String) = when (value) {
        getString(com.goodwy.commons.R.string.mobile) -> Phone.TYPE_MOBILE
        getString(com.goodwy.commons.R.string.home) -> Phone.TYPE_HOME
        getString(com.goodwy.commons.R.string.work) -> Phone.TYPE_WORK
        getString(com.goodwy.commons.R.string.main_number) -> Phone.TYPE_MAIN
        getString(com.goodwy.commons.R.string.work_fax) -> Phone.TYPE_FAX_WORK
        getString(com.goodwy.commons.R.string.home_fax) -> Phone.TYPE_FAX_HOME
        getString(com.goodwy.commons.R.string.pager) -> Phone.TYPE_PAGER
        getString(com.goodwy.commons.R.string.other) -> Phone.TYPE_OTHER
        else -> Phone.TYPE_CUSTOM
    }

    private fun getEmailTypeId(value: String) = when (value) {
        getString(com.goodwy.commons.R.string.home) -> CommonDataKinds.Email.TYPE_HOME
        getString(com.goodwy.commons.R.string.work) -> CommonDataKinds.Email.TYPE_WORK
        getString(com.goodwy.commons.R.string.mobile) -> CommonDataKinds.Email.TYPE_MOBILE
        getString(com.goodwy.commons.R.string.other) -> CommonDataKinds.Email.TYPE_OTHER
        else -> CommonDataKinds.Email.TYPE_CUSTOM
    }

    private fun getEventTypeId(value: String) = when (value) {
        getString(com.goodwy.commons.R.string.anniversary) -> CommonDataKinds.Event.TYPE_ANNIVERSARY
        getString(com.goodwy.commons.R.string.birthday) -> CommonDataKinds.Event.TYPE_BIRTHDAY
        else -> CommonDataKinds.Event.TYPE_OTHER
    }

    private fun getRelationTypeId(value: String) = when (value) {
        getString(com.goodwy.commons.R.string.relation_assistant_g) -> Relation.TYPE_ASSISTANT
        getString(com.goodwy.commons.R.string.relation_brother_g) -> Relation.TYPE_BROTHER
        getString(com.goodwy.commons.R.string.relation_child_g) -> Relation.TYPE_CHILD
        getString(com.goodwy.commons.R.string.relation_domestic_partner_g) -> Relation.TYPE_DOMESTIC_PARTNER
        getString(com.goodwy.commons.R.string.relation_father_g) -> Relation.TYPE_FATHER
        getString(com.goodwy.commons.R.string.relation_friend_g) -> Relation.TYPE_FRIEND
        getString(com.goodwy.commons.R.string.relation_manager_g) -> Relation.TYPE_MANAGER
        getString(com.goodwy.commons.R.string.relation_mother_g) -> Relation.TYPE_MOTHER
        getString(com.goodwy.commons.R.string.relation_parent_g) -> Relation.TYPE_PARENT
        getString(com.goodwy.commons.R.string.relation_partner_g) -> Relation.TYPE_PARTNER
        getString(com.goodwy.commons.R.string.relation_referred_by_g) -> Relation.TYPE_REFERRED_BY
        getString(com.goodwy.commons.R.string.relation_relative_g) -> Relation.TYPE_RELATIVE
        getString(com.goodwy.commons.R.string.relation_sister_g) -> Relation.TYPE_SISTER
        getString(com.goodwy.commons.R.string.relation_spouse_g) -> Relation.TYPE_SPOUSE

        // Relation types defined in vCard 4.0
        getString(com.goodwy.commons.R.string.relation_contact_g) -> ContactRelation.TYPE_CONTACT
        getString(com.goodwy.commons.R.string.relation_acquaintance_g) -> ContactRelation.TYPE_ACQUAINTANCE
        // getString(com.goodwy.commons.R.string.relation_friend) -> ContactRelation.TYPE_FRIEND
        getString(com.goodwy.commons.R.string.relation_met_g) -> ContactRelation.TYPE_MET
        getString(com.goodwy.commons.R.string.relation_co_worker_g) -> ContactRelation.TYPE_CO_WORKER
        getString(com.goodwy.commons.R.string.relation_colleague_g) -> ContactRelation.TYPE_COLLEAGUE
        getString(com.goodwy.commons.R.string.relation_co_resident_g) -> ContactRelation.TYPE_CO_RESIDENT
        getString(com.goodwy.commons.R.string.relation_neighbor_g) -> ContactRelation.TYPE_NEIGHBOR
        // getString(com.goodwy.commons.R.string.relation_child) -> ContactRelation.TYPE_CHILD
        // getString(com.goodwy.commons.R.string.relation_parent) -> ContactRelation.TYPE_PARENT
        getString(com.goodwy.commons.R.string.relation_sibling_g) -> ContactRelation.TYPE_SIBLING
        // getString(com.goodwy.commons.R.string.relation_spouse) -> ContactRelation.TYPE_SPOUSE
        getString(com.goodwy.commons.R.string.relation_kin_g) -> ContactRelation.TYPE_KIN
        getString(com.goodwy.commons.R.string.relation_muse_g) -> ContactRelation.TYPE_MUSE
        getString(com.goodwy.commons.R.string.relation_crush_g) -> ContactRelation.TYPE_CRUSH
        getString(com.goodwy.commons.R.string.relation_date_g) -> ContactRelation.TYPE_DATE
        getString(com.goodwy.commons.R.string.relation_sweetheart_g) -> ContactRelation.TYPE_SWEETHEART
        getString(com.goodwy.commons.R.string.relation_me_g) -> ContactRelation.TYPE_ME
        getString(com.goodwy.commons.R.string.relation_agent_g) -> ContactRelation.TYPE_AGENT
        getString(com.goodwy.commons.R.string.relation_emergency_g) -> ContactRelation.TYPE_EMERGENCY

        getString(com.goodwy.commons.R.string.relation_superior_g) -> ContactRelation.TYPE_SUPERIOR
        getString(com.goodwy.commons.R.string.relation_subordinate_g) -> ContactRelation.TYPE_SUBORDINATE
        getString(com.goodwy.commons.R.string.relation_husband_g) -> ContactRelation.TYPE_HUSBAND
        getString(com.goodwy.commons.R.string.relation_wife_g) -> ContactRelation.TYPE_WIFE
        getString(com.goodwy.commons.R.string.relation_son_g) -> ContactRelation.TYPE_SON
        getString(com.goodwy.commons.R.string.relation_daughter_g) -> ContactRelation.TYPE_DAUGHTER
        getString(com.goodwy.commons.R.string.relation_grandparent_g) -> ContactRelation.TYPE_GRANDPARENT
        getString(com.goodwy.commons.R.string.relation_grandfather_g) -> ContactRelation.TYPE_GRANDFATHER
        getString(com.goodwy.commons.R.string.relation_grandmother_g) -> ContactRelation.TYPE_GRANDMOTHER
        getString(com.goodwy.commons.R.string.relation_grandchild_g) -> ContactRelation.TYPE_GRANDCHILD
        getString(com.goodwy.commons.R.string.relation_grandson_g) -> ContactRelation.TYPE_GRANDSON
        getString(com.goodwy.commons.R.string.relation_granddaughter_g) -> ContactRelation.TYPE_GRANDDAUGHTER
        getString(com.goodwy.commons.R.string.relation_uncle_g) -> ContactRelation.TYPE_UNCLE
        getString(com.goodwy.commons.R.string.relation_aunt_g) -> ContactRelation.TYPE_AUNT
        getString(com.goodwy.commons.R.string.relation_nephew_g) -> ContactRelation.TYPE_NEPHEW
        getString(com.goodwy.commons.R.string.relation_niece_g) -> ContactRelation.TYPE_NIECE
        getString(com.goodwy.commons.R.string.relation_father_in_law_g) -> ContactRelation.TYPE_FATHER_IN_LAW
        getString(com.goodwy.commons.R.string.relation_mother_in_law_g) -> ContactRelation.TYPE_MOTHER_IN_LAW
        getString(com.goodwy.commons.R.string.relation_son_in_law_g) -> ContactRelation.TYPE_SON_IN_LAW
        getString(com.goodwy.commons.R.string.relation_daughter_in_law_g) -> ContactRelation.TYPE_DAUGHTER_IN_LAW
        getString(com.goodwy.commons.R.string.relation_brother_in_law_g) -> ContactRelation.TYPE_BROTHER_IN_LAW
        getString(com.goodwy.commons.R.string.relation_sister_in_law_g) -> ContactRelation.TYPE_SISTER_IN_LAW

        else -> Relation.TYPE_CUSTOM
    }

    private fun getAddressTypeId(value: String) = when (value) {
        getString(com.goodwy.commons.R.string.home) -> StructuredPostal.TYPE_HOME
        getString(com.goodwy.commons.R.string.work) -> StructuredPostal.TYPE_WORK
        getString(com.goodwy.commons.R.string.other) -> StructuredPostal.TYPE_OTHER
        else -> StructuredPostal.TYPE_CUSTOM
    }

    private fun getIMTypeId(value: String) = when (value) {
        getString(R.string.aim) -> Im.PROTOCOL_AIM
        getString(R.string.windows_live) -> Im.PROTOCOL_MSN
        getString(R.string.yahoo) -> Im.PROTOCOL_YAHOO
        getString(R.string.skype) -> Im.PROTOCOL_SKYPE
        getString(R.string.qq) -> Im.PROTOCOL_QQ
        getString(R.string.hangouts) -> Im.PROTOCOL_GOOGLE_TALK
        getString(R.string.icq) -> Im.PROTOCOL_ICQ
        getString(R.string.jabber) -> Im.PROTOCOL_JABBER
        else -> Im.PROTOCOL_CUSTOM
    }

    private fun setupAutoComplete(nameTextViews: List<MyAutoCompleteTextView>) {
        ContactsHelper(this).getContacts { contacts ->
            val adapter = AutoCompleteTextViewAdapter(this, contacts)
            val handler = Handler(mainLooper)
            nameTextViews.forEach { view ->
                view.setAdapter(adapter)
                view.setOnItemClickListener { _, _, position, _ ->
                    val selectedContact = adapter.resultList[position]

                    if (binding.contactFirstName.isVisible()) {
                        binding.contactFirstName.setText(selectedContact.firstName)
                    }
                    if (binding.contactMiddleName.isVisible()) {
                        binding.contactMiddleName.setText(selectedContact.middleName)
                    }
                    if (binding.contactSurname.isVisible()) {
                        binding.contactSurname.setText(selectedContact.surname)
                    }
                }
                view.doAfterTextChanged {
                    handler.postDelayed({
                        adapter.autoComplete = true
                        adapter.filter.filter(it)
                    }, AUTO_COMPLETE_DELAY)
                }
            }
        }
    }
}
