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
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.*
import android.provider.MediaStore
import android.telephony.PhoneNumberUtils
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.dialogs.SelectAlarmSoundDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.*
import com.goodwy.commons.models.contacts.Email
import com.goodwy.commons.models.contacts.Event
import com.goodwy.commons.models.contacts.Organization
import com.goodwy.commons.models.contacts.ContactRelation
import com.goodwy.contacts.R
import com.goodwy.contacts.dialogs.CustomLabelDialog
import com.goodwy.contacts.dialogs.ManageVisibleFieldsDialog
import com.goodwy.contacts.dialogs.MyDatePickerDialog
import com.goodwy.contacts.dialogs.SelectGroupsDialog
import com.goodwy.contacts.extensions.*
import com.goodwy.contacts.helpers.*
import kotlinx.android.synthetic.main.activity_edit_contact.*
import kotlinx.android.synthetic.main.activity_edit_contact.contact_addresses_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_emails_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_events_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_groups_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_ims_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_notes
import kotlinx.android.synthetic.main.activity_edit_contact.contact_numbers_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_organization_company
import kotlinx.android.synthetic.main.activity_edit_contact.contact_organization_job_position
import kotlinx.android.synthetic.main.activity_edit_contact.contact_photo_bottom_shadow
import kotlinx.android.synthetic.main.activity_edit_contact.contact_relations_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_ringtone
import kotlinx.android.synthetic.main.activity_edit_contact.contact_ringtone_chevron
import kotlinx.android.synthetic.main.activity_edit_contact.contact_ringtone_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_scrollview
import kotlinx.android.synthetic.main.activity_edit_contact.contact_toggle_favorite
import kotlinx.android.synthetic.main.activity_edit_contact.contact_toolbar
import kotlinx.android.synthetic.main.activity_edit_contact.contact_websites_holder
import kotlinx.android.synthetic.main.activity_edit_contact.contact_wrapper
import kotlinx.android.synthetic.main.activity_view_contact.*
import kotlinx.android.synthetic.main.item_edit_address.*
import kotlinx.android.synthetic.main.item_edit_address.view.*
import kotlinx.android.synthetic.main.item_edit_email.*
import kotlinx.android.synthetic.main.item_edit_email.view.*
import kotlinx.android.synthetic.main.item_edit_group.*
import kotlinx.android.synthetic.main.item_edit_group.view.*
import kotlinx.android.synthetic.main.item_edit_im.*
import kotlinx.android.synthetic.main.item_edit_im.view.*
import kotlinx.android.synthetic.main.item_edit_phone_number.*
import kotlinx.android.synthetic.main.item_edit_phone_number.view.*
import kotlinx.android.synthetic.main.item_edit_relation.*
import kotlinx.android.synthetic.main.item_edit_relation.view.*
import kotlinx.android.synthetic.main.item_edit_website.view.*
import kotlinx.android.synthetic.main.item_event.*
import kotlinx.android.synthetic.main.item_event.view.*
import kotlinx.android.synthetic.main.top_edit_view.contact_photo

class EditContactActivity : ContactActivity() {
    private val INTENT_TAKE_PHOTO = 1
    private val INTENT_CHOOSE_PHOTO = 2
    private val INTENT_CROP_PHOTO = 3

    private val TAKE_PHOTO = 1
    private val CHOOSE_PHOTO = 2
    private val REMOVE_PHOTO = 3

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
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFFEBEBEB.toInt()

    enum class PrimaryNumberStatus {
        UNCHANGED, STARRED, UNSTARRED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_contact)

        if (checkAppSideloading()) {
            return
        }

        updateMaterialActivityViews(contact_wrapper, contact_holder, useTransparentNavigation = false, useTopSearchMenu = false)
        setWindowTransparency(true) { _, _, leftNavigationBarSize, rightNavigationBarSize ->
            contact_wrapper.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
            updateNavigationBarColor(getProperBackgroundColor())
        }

        contact_wrapper.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
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
                            toast(R.string.no_contacts_permission)
                            hideKeyboard()
                            finish()
                        }
                    }
                } else {
                    toast(R.string.no_contacts_permission)
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
                INTENT_CROP_PHOTO -> updateContactPhoto(lastPhotoIntentUri.toString(), contact_photo, contact_photo_bottom_shadow)
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
                    toast(R.string.unknown_error_occurred)
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
        contact_scrollview.beVisible()
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
            showPhotoPlaceholder(contact_photo)
        } else {
            updateContactPhoto(contact!!.photoUri, contact_photo, contact_photo_bottom_shadow, contact!!.photo)
        }

        val textColor = getProperTextColor()
        arrayOf(
            contact_numbers_icon, contact_emails_icon, contact_addresses_icon, contact_ims_icon, contact_events_icon, contact_relations_icon,
            contact_notes_icon, contact_ringtone_icon, contact_ringtone_chevron, contact_websites_icon, contact_groups_title_icon, contact_source_title_icon
        ).forEach {
            it.applyColorFilter(textColor)
        }

        contact_toggle_favorite.setOnClickListener { toggleFavorite() }
        contact_photo.setOnClickListener { trySetPhotoRecommendation() }
        contact_change_photo.setOnClickListener { trySetPhotoRecommendation() }
        contact_numbers_add_new_holder.setOnClickListener { addNewPhoneNumberField() }
        contact_emails_add_new_holder.setOnClickListener { addNewEmailField() }
        contact_addresses_add_new_holder.setOnClickListener { addNewAddressField() }
        contact_ims_add_new_holder.setOnClickListener { addNewIMField() }
        contact_events_add_new_holder.setOnClickListener { addNewEventField() }
        contact_relations_add_new_holder.setOnClickListener { addNewRelationField() }
        contact_websites_add_new_holder.setOnClickListener { addNewWebsiteField() }
        contact_groups_add_new_holder.setOnClickListener { showSelectGroupsDialog() }
        contact_source.setOnClickListener { showSelectContactSourceDialog() }

        contact_change_photo.setOnLongClickListener { toast(R.string.change_photo); true; }

        setupFieldVisibility()

        contact_toggle_favorite.apply {
            setImageDrawable(getStarDrawable(contact!!.starred == 1))
            tag = contact!!.starred
            setOnLongClickListener { toast(R.string.toggle_favorite); true; }
        }

        val properPrimaryColor = getProperPrimaryColor()
        updateTextColors(contact_scrollview)
        numberViewToColor?.setTextColor(properPrimaryColor)
        emailViewToColor?.setTextColor(properPrimaryColor)
        wasActivityInitialized = true

        contact_toolbar.menu.apply {
            findItem(R.id.delete).isVisible = contact?.id != 0
            findItem(R.id.share).isVisible = contact?.id != 0
            findItem(R.id.open_with).isVisible = contact?.id != 0 && contact?.isPrivate() == false

            val favoriteIcon = getStarDrawable(contact!!.starred == 1)
            favoriteIcon.setTint(getProperBackgroundColor().getContrastColor())
            findItem(R.id.favorite).icon = favoriteIcon
        }
    }

    override fun onBackPressed() {
        if (System.currentTimeMillis() - mLastSavePromptTS > SAVE_DISCARD_PROMPT_INTERVAL && hasContactChanged()) {
            mLastSavePromptTS = System.currentTimeMillis()
            ConfirmationAdvancedDialog(this, "", R.string.save_before_closing, R.string.save, R.string.discard) {
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

    private fun setupMenu() {
        //(contact_appbar.layoutParams as RelativeLayout.LayoutParams).topMargin = statusBarHeight
        (contact_wrapper.layoutParams as FrameLayout.LayoutParams).topMargin = statusBarHeight
        contact_toolbar.overflowIcon = resources.getColoredDrawableWithColor(R.drawable.ic_three_dots_vector, getProperBackgroundColor().getContrastColor())
        contact_toolbar.menu.apply {
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
                favoriteIcon.setTint(getProperBackgroundColor().getContrastColor())
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

        val color = getProperBackgroundColor().getContrastColor()
        contact_toolbar.setNavigationIconTint(color)
        contact_toolbar.setNavigationOnClickListener {
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
            toast(R.string.unknown_error_occurred)
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
                toast(R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun setupFieldVisibility() {
        val showFields = config.showContactFields

        contact_prefix.beVisibleIf(showFields and SHOW_PREFIX_FIELD != 0)
        contact_first_name.beVisibleIf(showFields and SHOW_FIRST_NAME_FIELD != 0)
        contact_middle_name.beVisibleIf(showFields and SHOW_MIDDLE_NAME_FIELD != 0)
        contact_surname.beVisibleIf(showFields and SHOW_SURNAME_FIELD != 0)
        contact_suffix.beVisibleIf(showFields and SHOW_SUFFIX_FIELD != 0)
        contact_nickname.beVisibleIf(showFields and SHOW_NICKNAME_FIELD != 0)

        val isOrganizationVisible = showFields and SHOW_ORGANIZATION_FIELD != 0
        contact_organization_company.beVisibleIf(isOrganizationVisible)
        contact_organization_job_position.beVisibleIf(isOrganizationVisible)

        divider_contact_prefix.beVisibleIf(showFields and SHOW_PREFIX_FIELD != 0 &&
            (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0
                || showFields and SHOW_SURNAME_FIELD != 0 || showFields and SHOW_MIDDLE_NAME_FIELD != 0 || showFields and SHOW_FIRST_NAME_FIELD != 0))
        divider_contact_first_name.beVisibleIf(showFields and SHOW_FIRST_NAME_FIELD != 0 &&
            (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0
                || showFields and SHOW_SURNAME_FIELD != 0 || showFields and SHOW_MIDDLE_NAME_FIELD != 0))
        divider_contact_middle_name.beVisibleIf(showFields and SHOW_MIDDLE_NAME_FIELD != 0 &&
            (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0 || showFields and SHOW_SURNAME_FIELD != 0))
        divider_contact_surname.beVisibleIf(showFields and SHOW_SURNAME_FIELD != 0 &&
            (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0))
        divider_contact_suffix.beVisibleIf(showFields and SHOW_SUFFIX_FIELD != 0 && (isOrganizationVisible || showFields and SHOW_NICKNAME_FIELD != 0))
        divider_contact_nickname.beVisibleIf(showFields and SHOW_NICKNAME_FIELD != 0 && isOrganizationVisible)
        divider_contact_organization_company.beVisibleIf(isOrganizationVisible)

        contact_source_holder.beVisibleIf(showFields and SHOW_CONTACT_SOURCE_FIELD != 0)
        contact_source_title_holder.beVisibleIf(showFields and SHOW_CONTACT_SOURCE_FIELD != 0)

        val arePhoneNumbersVisible = showFields and SHOW_PHONE_NUMBERS_FIELD != 0
        contact_numbers_title_holder.beVisibleIf(arePhoneNumbersVisible)
        contact_numbers_holder.beVisibleIf(arePhoneNumbersVisible)
        contact_numbers_add_new_holder.beVisibleIf(arePhoneNumbersVisible)

        val areEmailsVisible = showFields and SHOW_EMAILS_FIELD != 0
        contact_emails_title_holder.beVisibleIf(areEmailsVisible)
        contact_emails_holder.beVisibleIf(areEmailsVisible)
        contact_emails_add_new_holder.beVisibleIf(areEmailsVisible)

        val areAddressesVisible = showFields and SHOW_ADDRESSES_FIELD != 0
        contact_addresses_title_holder.beVisibleIf(areAddressesVisible)
        contact_addresses_holder.beVisibleIf(areAddressesVisible)
        contact_addresses_add_new_holder.beVisibleIf(areAddressesVisible)

        val areIMsVisible = showFields and SHOW_IMS_FIELD != 0
        contact_ims_title_holder.beVisibleIf(areIMsVisible)
        contact_ims_holder.beVisibleIf(areIMsVisible)
        contact_ims_add_new_holder.beVisibleIf(areIMsVisible)

        val areEventsVisible = showFields and SHOW_EVENTS_FIELD != 0
        contact_events_title_holder.beVisibleIf(areEventsVisible)
        contact_events_holder.beVisibleIf(areEventsVisible)
        contact_events_add_new_holder.beVisibleIf(areEventsVisible)

        val areWebsitesVisible = showFields and SHOW_WEBSITES_FIELD != 0
        contact_websites_title_holder.beVisibleIf(areWebsitesVisible)
        contact_websites_holder.beVisibleIf(areWebsitesVisible)
        contact_websites_add_new_holder.beVisibleIf(areWebsitesVisible)

        val areRelationsVisible = showFields and SHOW_RELATIONS_FIELD != 0
        contact_relations_title_holder.beVisibleIf(areRelationsVisible)
        contact_relations_holder.beVisibleIf(areRelationsVisible)
        contact_relations_add_new_holder.beVisibleIf(areRelationsVisible)

        val areGroupsVisible = showFields and SHOW_GROUPS_FIELD != 0
        contact_groups_title_holder.beVisibleIf(areGroupsVisible)
        contact_groups_holder.beVisibleIf(areGroupsVisible)
        //contact_groups_add_new_holder.beVisibleIf(areGroupsVisible)

        val areNotesVisible = showFields and SHOW_NOTES_FIELD != 0
        contact_notes.beVisibleIf(areNotesVisible)
        contact_notes_title_holder.beVisibleIf(areNotesVisible)

        val isRingtoneVisible = showFields and SHOW_RINGTONE_FIELD != 0
        contact_ringtone_holder.beVisibleIf(isRingtoneVisible)
        contact_ringtone_title_holder.beVisibleIf(isRingtoneVisible)
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
            contact_prefix.setText(prefix)
            contact_first_name.setText(firstName)
            contact_middle_name.setText(middleName)
            contact_surname.setText(surname)
            contact_suffix.setText(suffix)
            contact_nickname.setText(nickname)
        }

        divider_contact_prefix.setBackgroundColor(getProperTextColor())
        divider_contact_first_name.setBackgroundColor(getProperTextColor())
        divider_contact_middle_name.setBackgroundColor(getProperTextColor())
        divider_contact_surname.setBackgroundColor(getProperTextColor())
        divider_contact_suffix.setBackgroundColor(getProperTextColor())
        divider_contact_nickname.setBackgroundColor(getProperTextColor())

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_prefix.setBackgroundColor(white)
            contact_first_name.setBackgroundColor(white)
            contact_middle_name.setBackgroundColor(white)
            contact_surname.setBackgroundColor(white)
            contact_suffix.setBackgroundColor(white)
            contact_nickname.setBackgroundColor(white)
        }
    }

    private fun setupOrganization() {
        contact_organization_company.setText(contact!!.organization.company)
        contact_organization_job_position.setText(contact!!.organization.jobPosition)

        divider_contact_organization_company.setBackgroundColor(getProperTextColor())

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_organization_company.setBackgroundColor(white)
            contact_organization_job_position.setBackgroundColor(white)
        }
    }

    private fun setupPhoneNumbers() {
        val phoneNumbers = contact!!.phoneNumbers
        contact_numbers_holder.removeAllViews()
        phoneNumbers.forEachIndexed { index, number ->
            var numberHolder = contact_numbers_holder.getChildAt(index)
            if (numberHolder == null) {
                numberHolder = layoutInflater.inflate(R.layout.item_edit_phone_number, contact_numbers_holder, false)
                contact_numbers_holder.addView(numberHolder)
            }

            numberHolder!!.apply {
                contact_number.setText(number.value)
                contact_number.tag = number.normalizedNumber
                setupPhoneNumberTypePicker(contact_number_type, number.type, number.label)
                if (highlightLastPhoneNumber && index == phoneNumbers.size - 1) {
                    numberViewToColor = contact_number
                }

                default_toggle_icon.tag = if (number.isPrimary) 1 else 0

                divider_contact_number.setBackgroundColor(getProperTextColor())
                contact_number_type.setTextColor(getProperPrimaryColor())
                contact_number_remove.apply {
                    beVisible()
                    setOnClickListener {
                        contact_numbers_holder.removeView(numberHolder)
                    }
                }
            }
        }

        initNumberHolders()

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_numbers_holder.setBackgroundColor(white)
            contact_numbers_add_new_holder.setBackgroundColor(white)
        }
    }

    private fun setDefaultNumber(selected: ImageView) {
        val numbersCount = contact_numbers_holder.childCount
        for (i in 0 until numbersCount) {
            val toggleIcon = contact_numbers_holder.getChildAt(i).default_toggle_icon
            if (toggleIcon != selected) {
                toggleIcon.tag = 0
            }
        }

        selected.tag = if (selected.tag == 1) 0 else 1

        initNumberHolders()
    }

    private fun initNumberHolders() {
        val numbersCount = contact_numbers_holder.childCount

        if (numbersCount == 1) {
            contact_numbers_holder.getChildAt(0).default_toggle_icon.beGone()
            return
        }

        for (i in 0 until numbersCount) {
            val toggleIcon = contact_numbers_holder.getChildAt(i).default_toggle_icon
            val isPrimary = toggleIcon.tag == 1

            val drawableId = if (isPrimary) {
                R.drawable.ic_star_vector
            } else {
                R.drawable.ic_star_outline_vector
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
        contact_emails_holder.removeAllViews()
        contact!!.emails.forEachIndexed { index, email ->
            var emailHolder = contact_emails_holder.getChildAt(index)
            if (emailHolder == null) {
                emailHolder = layoutInflater.inflate(R.layout.item_edit_email, contact_emails_holder, false)
                contact_emails_holder.addView(emailHolder)
            }

            emailHolder!!.apply {
                contact_email.setText(email.value)
                setupEmailTypePicker(contact_email_type, email.type, email.label)
                if (highlightLastEmail && index == contact!!.emails.size - 1) {
                    emailViewToColor = contact_email
                }

                divider_contact_email.setBackgroundColor(getProperTextColor())
                contact_email_type.setTextColor(getProperPrimaryColor())
                contact_email_remove.apply {
                    beVisible()
                    setOnClickListener {
                        contact_emails_holder.removeView(emailHolder)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_emails_holder.setBackgroundColor(white)
            contact_emails_add_new_holder.setBackgroundColor(white)
        }
    }

    private fun setupAddresses() {
        contact_addresses_holder.removeAllViews()
        contact!!.addresses.forEachIndexed { index, address ->
            var addressHolder = contact_addresses_holder.getChildAt(index)
            if (addressHolder == null) {
                addressHolder = layoutInflater.inflate(R.layout.item_edit_address, contact_addresses_holder, false)
                contact_addresses_holder.addView(addressHolder)
            }

            addressHolder!!.apply {
                contact_address.setText(address.value)
                setupAddressTypePicker(contact_address_type, address.type, address.label)

                divider_contact_address.setBackgroundColor(getProperTextColor())
                contact_address_type.setTextColor(getProperPrimaryColor())
                contact_address_remove.apply {
                    beVisible()
                    setOnClickListener {
                        contact_addresses_holder.removeView(addressHolder)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_addresses_holder.setBackgroundColor(white)
            contact_addresses_add_new_holder.setBackgroundColor(white)
        }
    }

    private fun setupIMs() {
        contact_ims_holder.removeAllViews()
        contact!!.IMs.forEachIndexed { index, IM ->
            var imHolder = contact_ims_holder.getChildAt(index)
            if (imHolder == null) {
                imHolder = layoutInflater.inflate(R.layout.item_edit_im, contact_ims_holder, false)
                contact_ims_holder.addView(imHolder)
            }

            imHolder!!.apply {
                contact_im.setText(IM.value)
                setupIMTypePicker(contact_im_type, IM.type, IM.label)

                divider_contact_im.setBackgroundColor(getProperTextColor())
                contact_im_type.setTextColor(getProperPrimaryColor())
                contact_im_remove.apply {
                    beVisible()
                    setOnClickListener {
                        contact_ims_holder.removeView(imHolder)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_ims_holder.setBackgroundColor(white)
            contact_ims_add_new_holder.setBackgroundColor(white)
        }
    }

    private fun setupNotes() {
        contact_notes.setText(contact!!.notes)
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_notes.setBackgroundColor(white)
        }
    }

    private fun setupRingtone() {
        contact_ringtone.setOnClickListener {
            hideKeyboard()
            val ringtonePickerIntent = getRingtonePickerIntent()
            try {
                startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
            } catch (e: Exception) {
                val currentRingtone = contact!!.ringtone ?: getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE).uri
                SelectAlarmSoundDialog(this, currentRingtone, AudioManager.STREAM_RING, PICK_RINGTONE_INTENT_ID, RingtoneManager.TYPE_RINGTONE, true,
                    onAlarmPicked = {
                        contact!!.ringtone = it?.uri
                        contact_ringtone.text = it?.title
                    }, onAlarmSoundDeleted = {}
                )
            }
        }

        val ringtone = contact!!.ringtone
        if (ringtone?.isEmpty() == true) {
            contact_ringtone.text = getString(R.string.no_sound)
        } else if (ringtone?.isNotEmpty() == true) {
            if (ringtone == SILENT) {
                contact_ringtone.text = getString(R.string.no_sound)
            } else {
                systemRingtoneSelected(Uri.parse(ringtone))
            }
        } else {
            val default = getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE)
            contact_ringtone.text = default.title
        }

        contact_ringtone.setTextColor(getProperPrimaryColor())
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_ringtone_holder.setBackgroundColor(white)
        }
    }

    private fun setupWebsites() {
        contact_websites_holder.removeAllViews()
        contact!!.websites.forEachIndexed { index, website ->
            var websitesHolder = contact_websites_holder.getChildAt(index)
            if (websitesHolder == null) {
                websitesHolder = layoutInflater.inflate(R.layout.item_edit_website, contact_websites_holder, false)
                contact_websites_holder.addView(websitesHolder)
            }

            websitesHolder!!.contact_website.setText(website)

            (websitesHolder as ViewGroup).apply {
                divider_contact_website.setBackgroundColor(getProperTextColor())
                contact_website_remove.apply {
                    beVisible()
                    setOnClickListener {
                        contact_websites_holder.removeView(websitesHolder)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_websites_holder.setBackgroundColor(white)
            contact_websites_add_new_holder.setBackgroundColor(white)
        }
    }

    private fun setupEvents() {
        contact!!.events.forEachIndexed { index, event ->
            var eventHolder = contact_events_holder.getChildAt(index)
            if (eventHolder == null) {
                eventHolder = layoutInflater.inflate(R.layout.item_event, contact_events_holder, false)
                contact_events_holder.addView(eventHolder)
            }

            (eventHolder as ViewGroup).apply {
                val contactEvent = contact_event.apply {
                    event.value.getDateTimeFromDateString(true, this)
                    tag = event.value
                    alpha = 1f
                }

                setupEventTypePicker(this, event.type)

                divider_contact_event.setBackgroundColor(getProperTextColor())
                contact_event_type.setTextColor(getProperPrimaryColor())
                contact_event_remove.apply {
                    beVisible()
                    setOnClickListener {
                        resetContactEvent(contactEvent, this)
                        contact_events_holder.removeView(eventHolder)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_events_holder.setBackgroundColor(white)
            contact_events_add_new_holder.setBackgroundColor(white)
        }
    }

    private fun setupRelations() {
        contact_relations_holder.removeAllViews()
        contact!!.relations.forEachIndexed { index, relation ->
            var relationHolder = contact_relations_holder.getChildAt(index)
            if (relationHolder == null) {
                relationHolder = layoutInflater.inflate(R.layout.item_edit_relation, contact_relations_holder, false)
                contact_relations_holder.addView(relationHolder)
            }

            relationHolder!!.apply {
                contact_relation.setText(relation.name)
                setupRelationTypePicker(contact_relation_type, relation.type, relation.label)

                divider_contact_relation.setBackgroundColor(getProperTextColor())
                contact_relation_type.setTextColor(getProperPrimaryColor())
                contact_relation_remove.apply {
                    beVisible()
                    setOnClickListener {
                        contact_relations_holder.removeView(relationHolder)
                    }
                }
            }
        }

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_relations_holder.setBackgroundColor(white)
            contact_relations_add_new_holder.setBackgroundColor(white)
        }
    }

    private fun setupGroups() {
        contact_groups_holder.removeAllViews()
        val groups = contact!!.groups
        groups.forEachIndexed { index, group ->
            var groupHolder = contact_groups_holder.getChildAt(index)
            if (groupHolder == null) {
                groupHolder = layoutInflater.inflate(R.layout.item_edit_group, contact_groups_holder, false)
                contact_groups_holder.addView(groupHolder)
            }

            (groupHolder as ViewGroup).apply {
                contact_group.apply {
                    text = group.title
                    setTextColor(getProperTextColor())
                    tag = group.id
                    alpha = 1f
                }

                setOnClickListener {
                    showSelectGroupsDialog()
                }

                contact_group_remove.apply {
                    beVisible()
                    //applyColorFilter(getProperPrimaryColor())
                    //background.applyColorFilter(getProperTextColor())
                    setOnClickListener {
                        removeGroup(group.id!!)
                    }
                }
                contact_group_add.beGone()
                val showFields = config.showContactFields
                contact_groups_add_new_holder.beVisibleIf(showFields and SHOW_GROUPS_FIELD != 0)

                divider_contact_group.setBackgroundColor(getProperTextColor())
            }
        }

        if (groups.isEmpty()) {
            layoutInflater.inflate(R.layout.item_edit_group, contact_groups_holder, false).apply {
                contact_group.apply {
                    alpha = 0.5f
                    text = getString(R.string.no_groups)
                    setTextColor(getProperTextColor())
                }

                contact_groups_holder.addView(this)
                contact_group_remove.beGone()
                contact_groups_add_new_holder.beGone()
                contact_group_add.beVisible()
                setOnClickListener {
                    showSelectGroupsDialog()
                }
            }
            divider_contact_group.beGone()
        }
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_groups_holder.setBackgroundColor(white)
            contact_groups_add_new_holder.setBackgroundColor(white)
        }
    }

    private fun setupContactSource() {
        originalContactSource = contact!!.source
        getPublicContactSource(contact!!.source) {
            contact_source.text = if (it == "") getString(R.string.phone_storage) else it
        }
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_source_holder.setBackgroundColor(white)
        }
    }

    private fun setupNewContact() {
        originalContactSource = if (hasContactPermissions()) config.lastUsedContactSource else SMT_PRIVATE
        contact = getEmptyContact()
        getPublicContactSource(contact!!.source) {
            contact_source.text = if (it == "") getString(R.string.phone_storage) else it
        }

        // if the last used contact source is not available anymore, use the first available one. Could happen at ejecting SIM card
        ContactsHelper(this).getSaveableContactSources { sources ->
            val sourceNames = sources.map { it.name }
            if (!sourceNames.contains(originalContactSource)) {
                originalContactSource = sourceNames.first()
                contact?.source = originalContactSource
                getPublicContactSource(contact!!.source) {
                    contact_source.text = if (it == "") getString(R.string.phone_storage) else it
                }
            }
        }
    }

    private fun setupTypePickers() {

        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            contact_prefix.setBackgroundColor(white)
            divider_contact_prefix.setBackgroundColor(getProperTextColor())
            contact_first_name.setBackgroundColor(white)
            divider_contact_first_name.setBackgroundColor(getProperTextColor())
            contact_middle_name.setBackgroundColor(white)
            divider_contact_middle_name.setBackgroundColor(getProperTextColor())
            contact_surname.setBackgroundColor(white)
            divider_contact_surname.setBackgroundColor(getProperTextColor())
            contact_suffix.setBackgroundColor(white)
            divider_contact_suffix.setBackgroundColor(getProperTextColor())
            contact_nickname.setBackgroundColor(white)
            divider_contact_nickname.setBackgroundColor(getProperTextColor())
            contact_organization_company.setBackgroundColor(white)
            divider_contact_organization_company.setBackgroundColor(getProperTextColor())
            contact_organization_job_position.setBackgroundColor(white)
            contact_source_holder.setBackgroundColor(white)
        }

        if (contact!!.phoneNumbers.isEmpty()) {
            contact_numbers_holder.removeAllViews()
            divider_contact_number?.setBackgroundColor(getProperTextColor())
            val numberHolder = contact_numbers_holder.getChildAt(0)
            (numberHolder as? ViewGroup)?.contact_number_type?.apply {
                setTextColor(getProperPrimaryColor())
                setupPhoneNumberTypePicker(this, DEFAULT_PHONE_NUMBER_TYPE, "")
            }

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_numbers_holder.setBackgroundColor(white)
                contact_numbers_add_new_holder.setBackgroundColor(white)
            }
        }

        if (contact!!.emails.isEmpty()) {
            contact_emails_holder.removeAllViews()
            divider_contact_email?.setBackgroundColor(getProperTextColor())
            val emailHolder = contact_emails_holder.getChildAt(0)
            (emailHolder as? ViewGroup)?.contact_email_type?.apply {
                setTextColor(getProperPrimaryColor())
                setupEmailTypePicker(this, DEFAULT_EMAIL_TYPE, "")
            }

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_emails_holder.setBackgroundColor(white)
                contact_emails_add_new_holder.setBackgroundColor(white)
            }
        }

        if (contact!!.addresses.isEmpty()) {
            contact_addresses_holder.removeAllViews()
            divider_contact_address?.setBackgroundColor(getProperTextColor())
            val addressHolder = contact_addresses_holder.getChildAt(0)
            (addressHolder as? ViewGroup)?.contact_address_type?.apply {
                setTextColor(getProperPrimaryColor())
                setupAddressTypePicker(this, DEFAULT_ADDRESS_TYPE, "")
            }

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_addresses_holder.setBackgroundColor(white)
                contact_addresses_add_new_holder.setBackgroundColor(white)
            }
        }

        if (contact!!.IMs.isEmpty()) {
            contact_ims_holder.removeAllViews()
            divider_contact_im?.setBackgroundColor(getProperTextColor())
            val IMHolder = contact_ims_holder.getChildAt(0)
            (IMHolder as? ViewGroup)?.contact_im_type?.apply {
                setTextColor(getProperPrimaryColor())
                setupIMTypePicker(this, DEFAULT_IM_TYPE, "")
            }

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_ims_holder.setBackgroundColor(white)
                contact_ims_add_new_holder.setBackgroundColor(white)
            }
        }

        if (contact!!.events.isEmpty()) {
            contact_events_holder.removeAllViews()
            divider_contact_event?.setBackgroundColor(getProperTextColor())
            val eventHolder = contact_events_holder.getChildAt(0)
            (eventHolder as? ViewGroup)?.apply {
                setupEventTypePicker(this)
            }

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_events_holder.setBackgroundColor(white)
                contact_events_add_new_holder.setBackgroundColor(white)
            }
        }

        if (contact!!.relations.isEmpty()) {
            contact_relations_holder.removeAllViews()
            divider_contact_relation?.setBackgroundColor(getProperTextColor())
            val relationHolder = contact_relations_holder.getChildAt(0)
            (relationHolder as? ViewGroup)?.contact_relation_type?.apply {
                setTextColor(getProperPrimaryColor())
                setupRelationTypePicker(this, DEFAULT_RELATION_TYPE, "")
            }

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_relations_holder.setBackgroundColor(white)
                contact_relations_add_new_holder.setBackgroundColor(white)
            }
        }

        if (contact!!.notes.isEmpty()) {
            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_notes.setBackgroundColor(white)
            }
        }

        if (contact!!.websites.isEmpty()) {
            contact_websites_holder.removeAllViews()

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_websites_holder.setBackgroundColor(white)
                contact_websites_add_new_holder.setBackgroundColor(white)
            }
        }

        if (contact!!.groups.isEmpty()) {
            divider_contact_group?.setBackgroundColor(getProperTextColor())
            val groupsHolder = contact_groups_holder.getChildAt(0)
            (groupsHolder as? ViewGroup)?.apply {
                contact_group?.setOnClickListener {
                    setupGroupsPicker(contact_group)
                }
                contact_group_remove?.beGone()
                divider_contact_group?.beGone()
                contact_group_add?.beVisible()
            }
            contact_groups_add_new_holder?.beGone()

            if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
                contact_groups_holder?.setBackgroundColor(white)
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

    private fun setupEventTypePicker(eventHolder: ViewGroup, type: Int = DEFAULT_EVENT_TYPE) {
        eventHolder.contact_event_type.apply {
            setText(getEventTextId(type))
            setOnClickListener {
                showEventTypePicker(it as TextView)
            }
        }

        val eventField = eventHolder.contact_event
        eventField.setOnClickListener {
            MyDatePickerDialog(this, eventField.tag?.toString() ?: "") { dateTag ->
                eventField.apply {
                    dateTag.getDateTimeFromDateString(true, this)
                    tag = dateTag
                    alpha = 1f
                }
            }
        }

        eventHolder.contact_event_remove.apply {
            setOnClickListener {
                resetContactEvent(eventField, this@apply)
                contact_events_holder.removeView(eventHolder)
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
            RadioItem(CommonDataKinds.Relation.TYPE_CUSTOM, getString(R.string.custom)),

            RadioItem(Relation.TYPE_FRIEND, getString(R.string.relation_friend_g)), // 6

            RadioItem(Relation.TYPE_SPOUSE, getString(R.string.relation_spouse_g)), // 14
            RadioItem(ContactRelation.TYPE_HUSBAND, getString(R.string.relation_husband_g)), // 103
            RadioItem(ContactRelation.TYPE_WIFE, getString(R.string.relation_wife_g)), // 104
            RadioItem(Relation.TYPE_DOMESTIC_PARTNER, getString(R.string.relation_domestic_partner_g)), // 4
            RadioItem(Relation.TYPE_PARTNER, getString(R.string.relation_partner_g)), // 10
            RadioItem(ContactRelation.TYPE_CO_RESIDENT, getString(R.string.relation_co_resident_g)), // 56
            RadioItem(ContactRelation.TYPE_NEIGHBOR, getString(R.string.relation_neighbor_g)), // 57
            RadioItem(Relation.TYPE_PARENT, getString(R.string.relation_parent_g)), // 9
            RadioItem(Relation.TYPE_FATHER, getString(R.string.relation_father_g)), // 5
            RadioItem(Relation.TYPE_MOTHER, getString(R.string.relation_mother_g)), // 8
            RadioItem(Relation.TYPE_CHILD, getString(R.string.relation_child_g)), // 3
            RadioItem(ContactRelation.TYPE_SON, getString(R.string.relation_son_g)), // 105
            RadioItem(ContactRelation.TYPE_DAUGHTER, getString(R.string.relation_daughter_g)), // 106
            RadioItem(ContactRelation.TYPE_SIBLING, getString(R.string.relation_sibling_g)), // 58
            RadioItem(Relation.TYPE_BROTHER, getString(R.string.relation_brother_g)), // 2
            RadioItem(Relation.TYPE_SISTER, getString(R.string.relation_sister_g)), // 13
            RadioItem(ContactRelation.TYPE_GRANDPARENT, getString(R.string.relation_grandparent_g)), // 107
            RadioItem(ContactRelation.TYPE_GRANDFATHER, getString(R.string.relation_grandfather_g)), // 108
            RadioItem(ContactRelation.TYPE_GRANDMOTHER, getString(R.string.relation_grandmother_g)), // 109
            RadioItem(ContactRelation.TYPE_GRANDCHILD, getString(R.string.relation_grandchild_g)), // 110
            RadioItem(ContactRelation.TYPE_GRANDSON, getString(R.string.relation_grandson_g)), // 111
            RadioItem(ContactRelation.TYPE_GRANDDAUGHTER, getString(R.string.relation_granddaughter_g)), // 112
            RadioItem(ContactRelation.TYPE_UNCLE, getString(R.string.relation_uncle_g)), // 113
            RadioItem(ContactRelation.TYPE_AUNT, getString(R.string.relation_aunt_g)), // 114
            RadioItem(ContactRelation.TYPE_NEPHEW, getString(R.string.relation_nephew_g)), // 115
            RadioItem(ContactRelation.TYPE_NIECE, getString(R.string.relation_niece_g)), // 116
            RadioItem(ContactRelation.TYPE_FATHER_IN_LAW, getString(R.string.relation_father_in_law_g)), // 117
            RadioItem(ContactRelation.TYPE_MOTHER_IN_LAW, getString(R.string.relation_mother_in_law_g)), // 118
            RadioItem(ContactRelation.TYPE_SON_IN_LAW, getString(R.string.relation_son_in_law_g)), // 119
            RadioItem(ContactRelation.TYPE_DAUGHTER_IN_LAW, getString(R.string.relation_daughter_in_law_g)), // 120
            RadioItem(ContactRelation.TYPE_BROTHER_IN_LAW, getString(R.string.relation_brother_in_law_g)), // 121
            RadioItem(ContactRelation.TYPE_SISTER_IN_LAW, getString(R.string.relation_sister_in_law_g)), // 122
            RadioItem(Relation.TYPE_RELATIVE, getString(R.string.relation_relative_g)), // 12
            RadioItem(ContactRelation.TYPE_KIN, getString(R.string.relation_kin_g)), // 59

            RadioItem(ContactRelation.TYPE_MUSE, getString(R.string.relation_muse_g)), // 60
            RadioItem(ContactRelation.TYPE_CRUSH, getString(R.string.relation_crush_g)), // 61
            RadioItem(ContactRelation.TYPE_DATE, getString(R.string.relation_date_g)), // 62
            RadioItem(ContactRelation.TYPE_SWEETHEART, getString(R.string.relation_sweetheart_g)), // 63

            RadioItem(ContactRelation.TYPE_CONTACT, getString(R.string.relation_contact_g)), // 51
            RadioItem(ContactRelation.TYPE_ACQUAINTANCE, getString(R.string.relation_acquaintance_g)), // 52
            RadioItem(ContactRelation.TYPE_MET, getString(R.string.relation_met_g)), // 53
            RadioItem(Relation.TYPE_REFERRED_BY, getString(R.string.relation_referred_by_g)), // 11
            RadioItem(ContactRelation.TYPE_AGENT, getString(R.string.relation_agent_g)), // 64

            RadioItem(ContactRelation.TYPE_COLLEAGUE, getString(R.string.relation_colleague_g)), // 55
            RadioItem(ContactRelation.TYPE_CO_WORKER, getString(R.string.relation_co_worker_g)), // 54
            RadioItem(ContactRelation.TYPE_SUPERIOR, getString(R.string.relation_superior_g)), // 101
            RadioItem(ContactRelation.TYPE_SUBORDINATE, getString(R.string.relation_subordinate_g)), // 102
            RadioItem(Relation.TYPE_MANAGER, getString(R.string.relation_manager_g)), // 7
            RadioItem(Relation.TYPE_ASSISTANT, getString(R.string.relation_assistant_g)), // 1

            RadioItem(ContactRelation.TYPE_ME, getString(R.string.relation_me_g)), // 66
            RadioItem(ContactRelation.TYPE_EMERGENCY, getString(R.string.relation_emergency_g)) // 65
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
            setOnClickListener {
                showSelectGroupsDialog()
            }
        }
    }

    private fun resetContactEvent(contactEvent: TextView, removeContactEventButton: ImageView) {
        contactEvent.apply {
            text = getString(R.string.unknown)
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
            RadioItem(Phone.TYPE_CUSTOM, getString(CommonDataKinds.Phone.getTypeLabelResource(Phone.TYPE_CUSTOM))),
            RadioItem(Phone.TYPE_MOBILE, getString(R.string.mobile)),
            RadioItem(Phone.TYPE_HOME, getString(R.string.home)),
            RadioItem(Phone.TYPE_WORK, getString(R.string.work)),
            RadioItem(Phone.TYPE_MAIN, getString(R.string.main_number)),
            RadioItem(Phone.TYPE_FAX_WORK, getString(R.string.work_fax)),
            RadioItem(Phone.TYPE_FAX_HOME, getString(R.string.home_fax)),
            RadioItem(Phone.TYPE_PAGER, getString(R.string.pager)),
            RadioItem(Phone.TYPE_OTHER, getString(R.string.other))
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
            RadioItem(CommonDataKinds.Email.TYPE_HOME, getString(R.string.home)),
            RadioItem(CommonDataKinds.Email.TYPE_WORK, getString(R.string.work)),
            RadioItem(CommonDataKinds.Email.TYPE_MOBILE, getString(R.string.mobile)),
            RadioItem(CommonDataKinds.Email.TYPE_OTHER, getString(R.string.other))
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
            RadioItem(StructuredPostal.TYPE_HOME, getString(R.string.home)),
            RadioItem(StructuredPostal.TYPE_WORK, getString(R.string.work)),
            RadioItem(StructuredPostal.TYPE_OTHER, getString(R.string.other))
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
            RadioItem(Im.PROTOCOL_CUSTOM, getString(R.string.custom))
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
            RadioItem(CommonDataKinds.Event.TYPE_ANNIVERSARY, getString(R.string.anniversary)),
            RadioItem(CommonDataKinds.Event.TYPE_BIRTHDAY, getString(R.string.birthday)),
            RadioItem(CommonDataKinds.Event.TYPE_OTHER, getString(R.string.other))
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
        showContactSourcePicker(contact!!.source) {
            contact!!.source = if (it == getString(R.string.phone_storage_hidden)) SMT_PRIVATE else it
            getPublicContactSource(it) {
                contact_source.text = if (it == "") getString(R.string.phone_storage) else it
            }
        }
    }

    private fun saveContact() {
        if (isSaving || contact == null) {
            return
        }

        val contactFields = arrayListOf(
            contact_prefix, contact_first_name, contact_middle_name, contact_surname, contact_suffix, contact_nickname,
            contact_notes, contact_organization_company, contact_organization_job_position
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
            prefix = contact_prefix.value,
            firstName = contact_first_name.value,
            middleName = contact_middle_name.value,
            surname = contact_surname.value,
            suffix = contact_suffix.value,
            nickname = contact_nickname.value,
            photoUri = currentContactPhotoPath,
            phoneNumbers = filledPhoneNumbers,
            emails = filledEmails,
            addresses = filledAddresses,
            IMs = filledIMs,
            events = filledEvents,
            starred = if (isContactStarred()) 1 else 0,
            notes = contact_notes.value,
            websites = filledWebsites,
            relations = filledRelations,
        )

        val company = contact_organization_company.value
        val jobPosition = contact_organization_job_position.value
        newContact.organization = Organization(company, jobPosition)
        return newContact
    }

    private fun getFilledPhoneNumbers(): ArrayList<PhoneNumber> {
        val phoneNumbers = ArrayList<PhoneNumber>()
        val numbersCount = contact_numbers_holder.childCount
        for (i in 0 until numbersCount) {
            val numberHolder = contact_numbers_holder.getChildAt(i)
            val number = numberHolder.contact_number.value
            val numberType = getPhoneNumberTypeId(numberHolder.contact_number_type.value)
            val numberLabel = if (numberType == Phone.TYPE_CUSTOM) numberHolder.contact_number_type.value else ""

            if (number.isNotEmpty()) {
                var normalizedNumber = number.normalizePhoneNumber()

                // fix a glitch when onBackPressed the app thinks that a number changed because we fetched
                // normalized number +421903123456, then at getting it from the input field we get 0903123456, can happen at WhatsApp contacts
                val fetchedNormalizedNumber = numberHolder.contact_number.tag?.toString() ?: ""
                if (PhoneNumberUtils.compare(number.normalizePhoneNumber(), fetchedNormalizedNumber)) {
                    normalizedNumber = fetchedNormalizedNumber
                }

                val isPrimary = numberHolder.default_toggle_icon.tag == 1
                phoneNumbers.add(PhoneNumber(number, numberType, numberLabel, normalizedNumber, isPrimary))
            }
        }
        return phoneNumbers
    }

    private fun getFilledEmails(): ArrayList<Email> {
        val emails = ArrayList<Email>()
        val emailsCount = contact_emails_holder.childCount
        for (i in 0 until emailsCount) {
            val emailHolder = contact_emails_holder.getChildAt(i)
            val email = emailHolder.contact_email.value
            val emailType = getEmailTypeId(emailHolder.contact_email_type.value)
            val emailLabel = if (emailType == CommonDataKinds.Email.TYPE_CUSTOM) emailHolder.contact_email_type.value else ""

            if (email.isNotEmpty()) {
                emails.add(Email(email, emailType, emailLabel))
            }
        }
        return emails
    }

    private fun getFilledAddresses(): ArrayList<Address> {
        val addresses = ArrayList<Address>()
        val addressesCount = contact_addresses_holder.childCount
        for (i in 0 until addressesCount) {
            val addressHolder = contact_addresses_holder.getChildAt(i)
            val address = addressHolder.contact_address.value
            val addressType = getAddressTypeId(addressHolder.contact_address_type.value)
            val addressLabel = if (addressType == StructuredPostal.TYPE_CUSTOM) addressHolder.contact_address_type.value else ""

            if (address.isNotEmpty()) {
                addresses.add(Address(address, addressType, addressLabel))
            }
        }
        return addresses
    }

    private fun getFilledIMs(): ArrayList<IM> {
        val IMs = ArrayList<IM>()
        val IMsCount = contact_ims_holder.childCount
        for (i in 0 until IMsCount) {
            val IMsHolder = contact_ims_holder.getChildAt(i)
            val IM = IMsHolder.contact_im.value
            val IMType = getIMTypeId(IMsHolder.contact_im_type.value)
            val IMLabel = if (IMType == Im.PROTOCOL_CUSTOM) IMsHolder.contact_im_type.value else ""

            if (IM.isNotEmpty()) {
                IMs.add(IM(IM, IMType, IMLabel))
            }
        }
        return IMs
    }

    private fun getFilledEvents(): ArrayList<Event> {
        val unknown = getString(R.string.unknown)
        val events = ArrayList<Event>()
        val eventsCount = contact_events_holder.childCount
        for (i in 0 until eventsCount) {
            val eventHolder = contact_events_holder.getChildAt(i)
            val event = eventHolder.contact_event.value
            val eventType = getEventTypeId(eventHolder.contact_event_type.value)

            if (event.isNotEmpty() && event != unknown) {
                events.add(Event(eventHolder.contact_event.tag.toString(), eventType))
            }
        }
        return events
    }

    private fun getFilledRelations(): ArrayList<ContactRelation> {
        val relations = ArrayList<ContactRelation>()
        val relationsCount = contact_relations_holder.childCount
        for (i in 0 until relationsCount) {
            val relationHolder = contact_relations_holder.getChildAt(i)
            val name: String = relationHolder.contact_relation.value
            if (name.isNotEmpty()) {
                var label = relationHolder.contact_relation_type.value.trim()
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
        val websitesCount = contact_websites_holder.childCount
        for (i in 0 until websitesCount) {
            val websiteHolder = contact_websites_holder.getChildAt(i)
            val website = websiteHolder.contact_website.value
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
            toast(R.string.unknown_error_occurred)
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
            toast(R.string.unknown_error_occurred)
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
        val numberHolder = layoutInflater.inflate(R.layout.item_edit_phone_number, contact_numbers_holder, false) as ViewGroup
        updateTextColors(numberHolder)
        setupPhoneNumberTypePicker(numberHolder.contact_number_type, DEFAULT_PHONE_NUMBER_TYPE, "")
        contact_numbers_holder.addView(numberHolder)
        contact_numbers_holder.onGlobalLayout {
            numberHolder.contact_number.requestFocus()
            showKeyboard(numberHolder.contact_number)
        }

        numberHolder.apply {
            divider_contact_number.setBackgroundColor(getProperTextColor())
            contact_number_type.setTextColor(getProperPrimaryColor())
            contact_number_remove.apply {
                beVisible()
                setOnClickListener {
                    contact_numbers_holder.removeView(numberHolder)
                    hideKeyboard()
                }
            }
        }
        numberHolder.default_toggle_icon.tag = 0
        initNumberHolders()
    }

    private fun addNewEmailField() {
        val emailHolder = layoutInflater.inflate(R.layout.item_edit_email, contact_emails_holder, false) as ViewGroup
        updateTextColors(emailHolder)
        setupEmailTypePicker(emailHolder.contact_email_type, DEFAULT_EMAIL_TYPE, "")
        contact_emails_holder.addView(emailHolder)
        contact_emails_holder.onGlobalLayout {
            emailHolder.contact_email.requestFocus()
            showKeyboard(emailHolder.contact_email)
        }

        emailHolder.apply {
            divider_contact_email.setBackgroundColor(getProperTextColor())
            contact_email_type.setTextColor(getProperPrimaryColor())
            contact_email_remove.apply {
                beVisible()
                setOnClickListener {
                    contact_emails_holder.removeView(emailHolder)
                    hideKeyboard()
                }
            }
        }
    }

    private fun addNewAddressField() {
        val addressHolder = layoutInflater.inflate(R.layout.item_edit_address, contact_addresses_holder, false) as ViewGroup
        updateTextColors(addressHolder)
        setupAddressTypePicker(addressHolder.contact_address_type, DEFAULT_ADDRESS_TYPE, "")
        contact_addresses_holder.addView(addressHolder)
        contact_addresses_holder.onGlobalLayout {
            addressHolder.contact_address.requestFocus()
            showKeyboard(addressHolder.contact_address)
        }

        addressHolder.apply {
            divider_contact_address.setBackgroundColor(getProperTextColor())
            contact_address_type.setTextColor(getProperPrimaryColor())
            contact_address_remove.apply {
                beVisible()
                setOnClickListener {
                    contact_addresses_holder.removeView(addressHolder)
                    hideKeyboard()
                }
            }
        }
    }

    private fun addNewIMField() {
        val IMHolder = layoutInflater.inflate(R.layout.item_edit_im, contact_ims_holder, false) as ViewGroup
        updateTextColors(IMHolder)
        setupIMTypePicker(IMHolder.contact_im_type, DEFAULT_IM_TYPE, "")
        contact_ims_holder.addView(IMHolder)
        contact_ims_holder.onGlobalLayout {
            IMHolder.contact_im.requestFocus()
            showKeyboard(IMHolder.contact_im)
        }

        IMHolder.apply {
            divider_contact_im.setBackgroundColor(getProperTextColor())
            contact_im_type.setTextColor(getProperPrimaryColor())
            contact_im_remove.apply {
                beVisible()
                setOnClickListener {
                    contact_ims_holder.removeView(IMHolder)
                    hideKeyboard()
                }
            }
        }
    }

    private fun addNewEventField() {
        val eventHolder = layoutInflater.inflate(R.layout.item_event, contact_events_holder, false) as ViewGroup
        updateTextColors(eventHolder)
        setupEventTypePicker(eventHolder)
        contact_events_holder.addView(eventHolder)

        eventHolder.apply {
            divider_contact_event.setBackgroundColor(getProperTextColor())
            contact_event_type.setTextColor(getProperPrimaryColor())
            contact_event_remove.apply {
                beVisible()
                setOnClickListener {
                    contact_events_holder.removeView(eventHolder)
                }
            }
        }
    }

    private fun addNewRelationField() {
        val relationHolder = layoutInflater.inflate(R.layout.item_edit_relation, contact_relations_holder, false) as ViewGroup
        updateTextColors(relationHolder)
        setupRelationTypePicker(relationHolder.contact_relation_type, DEFAULT_RELATION_TYPE, "")
        contact_relations_holder.addView(relationHolder)
        contact_relations_holder.onGlobalLayout {
            relationHolder.contact_relation.requestFocus()
            showKeyboard(relationHolder.contact_relation)
        }

        relationHolder.apply {
            divider_contact_relation.setBackgroundColor(getProperTextColor())
            contact_relation_type.setTextColor(getProperPrimaryColor())
            contact_relation_remove.apply {
                beVisible()
                setOnClickListener {
                    contact_relations_holder.removeView(relationHolder)
                    hideKeyboard()
                }
            }
        }
    }

    private fun toggleFavorite() {
        val isStarred = isContactStarred()
        contact_toggle_favorite.apply {
            setImageDrawable(getStarDrawable(!isStarred))
            tag = if (isStarred) 0 else 1

            setOnLongClickListener { toast(R.string.toggle_favorite); true; }
        }
    }

    private fun addNewWebsiteField() {
        val websitesHolder = layoutInflater.inflate(R.layout.item_edit_website, contact_websites_holder, false) as ViewGroup
        updateTextColors(websitesHolder)
        contact_websites_holder.addView(websitesHolder)
        websitesHolder.apply {
            divider_contact_website.setBackgroundColor(getProperTextColor())
            contact_website_remove.apply {
                beVisible()
                setOnClickListener {
                    contact_websites_holder.removeView(websitesHolder)
                    hideKeyboard()
                }
            }
        }
        contact_websites_holder.onGlobalLayout {
            websitesHolder.contact_website.requestFocus()
            showKeyboard(websitesHolder.contact_website)
        }
    }

    private fun isContactStarred() = contact_toggle_favorite.tag == 1

    private fun getStarDrawable(on: Boolean) = resources.getDrawable(if (on) R.drawable.ic_star_vector else R.drawable.ic_star_outline_vector)

    private fun trySetPhotoRecommendation() {
        val simpleGallery = "com.goodwy.gallery"
        val simpleGalleryDebug = "com.goodwy.gallery.debug"
        if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleGallery) && !isPackageInstalled(simpleGalleryDebug))) {
            NewAppDialog(this, simpleGallery, getString(R.string.recommendation_dialog_gallery_g), getString(R.string.right_gallery),
                AppCompatResources.getDrawable(this, R.mipmap.ic_gallery)) {
                trySetPhoto()
            }
        } else {
            trySetPhoto()
        }
    }

    private fun trySetPhoto() {
        val items = arrayListOf(
            RadioItem(TAKE_PHOTO, getString(R.string.take_photo)),
            RadioItem(CHOOSE_PHOTO, getString(R.string.choose_photo))
        )

        if (currentContactPhotoPath.isNotEmpty() || contact!!.photo != null) {
            items.add(RadioItem(REMOVE_PHOTO, getString(R.string.remove_photo)))
        }

        RadioGroupDialog(this, items) {
            when (it as Int) {
                TAKE_PHOTO -> startTakePhotoIntent()
                CHOOSE_PHOTO -> startChoosePhotoIntent()
                else -> {
                    showPhotoPlaceholder(contact_photo)
                    contact_photo_bottom_shadow.beGone()
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
                CommonDataKinds.Relation.CONTENT_ITEM_TYPE -> parseRelation(it)
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
        val type = contentValues.getAsInteger(CommonDataKinds.Relation.DATA2) ?: DEFAULT_RELATION_TYPE
        val relationValue = contentValues.getAsString(CommonDataKinds.Relation.DATA1) ?: return
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
                toast(R.string.no_app_found)
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
                toast(R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    override fun customRingtoneSelected(ringtonePath: String) {
        contact!!.ringtone = ringtonePath
        contact_ringtone.text = ringtonePath.getFilenameFromPath()
    }

    override fun systemRingtoneSelected(uri: Uri?) {
        contact!!.ringtone = uri?.toString() ?: ""
        val contactRingtone = RingtoneManager.getRingtone(this, uri)
        contact_ringtone.text = contactRingtone.getTitle(this)
    }

    private fun getPhoneNumberTypeId(value: String) = when (value) {
        getString(R.string.mobile) -> Phone.TYPE_MOBILE
        getString(R.string.home) -> Phone.TYPE_HOME
        getString(R.string.work) -> Phone.TYPE_WORK
        getString(R.string.main_number) -> Phone.TYPE_MAIN
        getString(R.string.work_fax) -> Phone.TYPE_FAX_WORK
        getString(R.string.home_fax) -> Phone.TYPE_FAX_HOME
        getString(R.string.pager) -> Phone.TYPE_PAGER
        getString(R.string.other) -> Phone.TYPE_OTHER
        else -> Phone.TYPE_CUSTOM
    }

    private fun getEmailTypeId(value: String) = when (value) {
        getString(R.string.home) -> CommonDataKinds.Email.TYPE_HOME
        getString(R.string.work) -> CommonDataKinds.Email.TYPE_WORK
        getString(R.string.mobile) -> CommonDataKinds.Email.TYPE_MOBILE
        getString(R.string.other) -> CommonDataKinds.Email.TYPE_OTHER
        else -> CommonDataKinds.Email.TYPE_CUSTOM
    }

    private fun getEventTypeId(value: String) = when (value) {
        getString(R.string.anniversary) -> CommonDataKinds.Event.TYPE_ANNIVERSARY
        getString(R.string.birthday) -> CommonDataKinds.Event.TYPE_BIRTHDAY
        else -> CommonDataKinds.Event.TYPE_OTHER
    }

    private fun getRelationTypeId(value: String) = when (value) {
        getString(R.string.relation_assistant_g) -> Relation.TYPE_ASSISTANT
        getString(R.string.relation_brother_g) -> Relation.TYPE_BROTHER
        getString(R.string.relation_child_g) -> Relation.TYPE_CHILD
        getString(R.string.relation_domestic_partner_g) -> Relation.TYPE_DOMESTIC_PARTNER
        getString(R.string.relation_father_g) -> Relation.TYPE_FATHER
        getString(R.string.relation_friend_g) -> Relation.TYPE_FRIEND
        getString(R.string.relation_manager_g) -> Relation.TYPE_MANAGER
        getString(R.string.relation_mother_g) -> Relation.TYPE_MOTHER
        getString(R.string.relation_parent_g) -> Relation.TYPE_PARENT
        getString(R.string.relation_partner_g) -> Relation.TYPE_PARTNER
        getString(R.string.relation_referred_by_g) -> Relation.TYPE_REFERRED_BY
        getString(R.string.relation_relative_g) -> Relation.TYPE_RELATIVE
        getString(R.string.relation_sister_g) -> Relation.TYPE_SISTER
        getString(R.string.relation_spouse_g) -> Relation.TYPE_SPOUSE

        // Relation types defined in vCard 4.0
        getString(R.string.relation_contact_g) -> ContactRelation.TYPE_CONTACT
        getString(R.string.relation_acquaintance_g) -> ContactRelation.TYPE_ACQUAINTANCE
        // getString(R.string.relation_friend) -> ContactRelation.TYPE_FRIEND
        getString(R.string.relation_met_g) -> ContactRelation.TYPE_MET
        getString(R.string.relation_co_worker_g) -> ContactRelation.TYPE_CO_WORKER
        getString(R.string.relation_colleague_g) -> ContactRelation.TYPE_COLLEAGUE
        getString(R.string.relation_co_resident_g) -> ContactRelation.TYPE_CO_RESIDENT
        getString(R.string.relation_neighbor_g) -> ContactRelation.TYPE_NEIGHBOR
        // getString(R.string.relation_child) -> ContactRelation.TYPE_CHILD
        // getString(R.string.relation_parent) -> ContactRelation.TYPE_PARENT
        getString(R.string.relation_sibling_g) -> ContactRelation.TYPE_SIBLING
        // getString(R.string.relation_spouse) -> ContactRelation.TYPE_SPOUSE
        getString(R.string.relation_kin_g) -> ContactRelation.TYPE_KIN
        getString(R.string.relation_muse_g) -> ContactRelation.TYPE_MUSE
        getString(R.string.relation_crush_g) -> ContactRelation.TYPE_CRUSH
        getString(R.string.relation_date_g) -> ContactRelation.TYPE_DATE
        getString(R.string.relation_sweetheart_g) -> ContactRelation.TYPE_SWEETHEART
        getString(R.string.relation_me_g) -> ContactRelation.TYPE_ME
        getString(R.string.relation_agent_g) -> ContactRelation.TYPE_AGENT
        getString(R.string.relation_emergency_g) -> ContactRelation.TYPE_EMERGENCY

        getString(R.string.relation_superior_g) -> ContactRelation.TYPE_SUPERIOR
        getString(R.string.relation_subordinate_g) -> ContactRelation.TYPE_SUBORDINATE
        getString(R.string.relation_husband_g) -> ContactRelation.TYPE_HUSBAND
        getString(R.string.relation_wife_g) -> ContactRelation.TYPE_WIFE
        getString(R.string.relation_son_g) -> ContactRelation.TYPE_SON
        getString(R.string.relation_daughter_g) -> ContactRelation.TYPE_DAUGHTER
        getString(R.string.relation_grandparent_g) -> ContactRelation.TYPE_GRANDPARENT
        getString(R.string.relation_grandfather_g) -> ContactRelation.TYPE_GRANDFATHER
        getString(R.string.relation_grandmother_g) -> ContactRelation.TYPE_GRANDMOTHER
        getString(R.string.relation_grandchild_g) -> ContactRelation.TYPE_GRANDCHILD
        getString(R.string.relation_grandson_g) -> ContactRelation.TYPE_GRANDSON
        getString(R.string.relation_granddaughter_g) -> ContactRelation.TYPE_GRANDDAUGHTER
        getString(R.string.relation_uncle_g) -> ContactRelation.TYPE_UNCLE
        getString(R.string.relation_aunt_g) -> ContactRelation.TYPE_AUNT
        getString(R.string.relation_nephew_g) -> ContactRelation.TYPE_NEPHEW
        getString(R.string.relation_niece_g) -> ContactRelation.TYPE_NIECE
        getString(R.string.relation_father_in_law_g) -> ContactRelation.TYPE_FATHER_IN_LAW
        getString(R.string.relation_mother_in_law_g) -> ContactRelation.TYPE_MOTHER_IN_LAW
        getString(R.string.relation_son_in_law_g) -> ContactRelation.TYPE_SON_IN_LAW
        getString(R.string.relation_daughter_in_law_g) -> ContactRelation.TYPE_DAUGHTER_IN_LAW
        getString(R.string.relation_brother_in_law_g) -> ContactRelation.TYPE_BROTHER_IN_LAW
        getString(R.string.relation_sister_in_law_g) -> ContactRelation.TYPE_SISTER_IN_LAW

        else -> Relation.TYPE_CUSTOM
    }

    private fun getAddressTypeId(value: String) = when (value) {
        getString(R.string.home) -> StructuredPostal.TYPE_HOME
        getString(R.string.work) -> StructuredPostal.TYPE_WORK
        getString(R.string.other) -> StructuredPostal.TYPE_OTHER
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
}
