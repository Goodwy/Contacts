package com.goodwy.contacts.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.goodwy.commons.dialogs.FilePickerDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.dialogs.SettingsIconDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.contacts.App.Companion.isProVersion
import com.goodwy.contacts.BuildConfig
import com.goodwy.contacts.R
import com.goodwy.contacts.dialogs.ExportContactsDialog
import com.goodwy.contacts.dialogs.ImportContactsDialog
import com.goodwy.contacts.dialogs.ManageVisibleFieldsDialog
import com.goodwy.contacts.dialogs.ManageVisibleTabsDialog
import com.goodwy.contacts.extensions.config
import com.goodwy.contacts.extensions.getTempFile
import com.goodwy.contacts.helpers.*
import kotlinx.android.synthetic.main.activity_settings.*
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*

class SettingsActivity : SimpleActivity() {

    private val PICK_IMPORT_SOURCE_INTENT = 1
    private val PICK_EXPORT_FILE_INTENT = 2

    private var ignoredExportContactSources = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(settings_toolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupDefaultTab()
        setupManageShownTabs()
        setupBottomNavigationBar()
        setupScreenSlideAnimation()
        setupUseIconTabs()
        setupUseColoredContacts()
        setupShowDialpadButton()
        setupMaterialDesign3()
        setupSettingsIcon()

        setupImportContacts()
        setupExportContacts()
        setupManageShownContactFields()
        setupMergeDuplicateContacts()
        setupShowCallConfirmation()
        setupFontSize()
        setupUseEnglish()
        setupLanguage()

        setupShowDividers()
        setupShowContactThumbnails()
        setupShowPhoneNumbers()
        setupShowContactsWithNumbers()
        setupStartNameWithSurname()
        setupShowPrivateContacts()
        setupOnContactClick()

        setupTipJar()
        setupAbout()
        updateTextColors(settings_holder)

        arrayOf(divider_general, divider_list_view, divider_other).forEach {
            it.setBackgroundColor(getProperTextColor())
        }
        arrayOf(settings_appearance_label, settings_general_label, settings_list_view_label, settings_other_label).forEach {
            it.setTextColor(getProperPrimaryColor())
        }

        /*arrayOf(
            settings_color_customization_holder,
            settings_general_settings_holder,
            settings_main_screen_holder,
            settings_list_view_holder
        ).forEach {
            it.background.applyColorFilter(getProperBackgroundColor().getContrastColor())
        }*/
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(/*isOrWasThankYouInstalled() || */isProVersion())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchase() //launchPurchaseThankYouIntent()
        }
        moreButton.setOnClickListener {
            launchPurchase()
        }
        val appDrawable = resources.getColoredDrawableWithColor(R.drawable.ic_plus_support, getProperPrimaryColor())
        purchase_logo.setImageDrawable(appDrawable)
        val drawable = resources.getColoredDrawableWithColor(R.drawable.button_gray_bg, getProperPrimaryColor())
        moreButton.background = drawable
        moreButton.setTextColor(getProperBackgroundColor())
        moreButton.setPadding(2,2,2,2)
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_chevron.applyColorFilter(getProperTextColor())
        settings_customize_colors_label.text = if (isOrWasThankYouInstalled() || isProVersion()) {
            getString(R.string.customize_colors)
        } else {
            getString(R.string.customize_colors_locked)
        }
        settings_customize_colors_holder.setOnClickListener {
            //handleCustomizeColorsClick()
            if (isOrWasThankYouInstalled() || isProVersion()) {
                startCustomizationActivity(false)
            } else {
                launchPurchase()
            }
        }
    }

    private fun setupManageShownContactFields() {
        settings_manage_contact_fields_chevron.applyColorFilter(getProperTextColor())
        settings_manage_contact_fields_holder.setOnClickListener {
            ManageVisibleFieldsDialog(this) {}
        }
    }

    private fun setupManageShownTabs() {
        settings_manage_shown_tabs_chevron.applyColorFilter(getProperTextColor())
        settings_manage_shown_tabs_holder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupScreenSlideAnimation() {
        settings_screen_slide_animation.text = getScreenSlideAnimationText()
        settings_screen_slide_animation_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, getString(R.string.no)),
                RadioItem(1, getString(R.string.screen_slide_animation_zoomout)),
                RadioItem(2, getString(R.string.screen_slide_animation_depth)))

            RadioGroupDialog(this@SettingsActivity, items, config.screenSlideAnimation) {
                config.screenSlideAnimation = it as Int
                config.tabsChanged = true
                settings_screen_slide_animation.text = getScreenSlideAnimationText()
            }
        }
    }

    private fun setupDefaultTab() {
        settings_default_tab.text = getDefaultTabText()
        settings_default_tab_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_LAST_USED, getString(R.string.last_used_tab)),
                RadioItem(TAB_FAVORITES, getString(R.string.favorites_tab)),
                RadioItem(TAB_CONTACTS, getString(R.string.contacts_tab)),
                RadioItem(TAB_GROUPS, getString(R.string.groups_tab)))

            RadioGroupDialog(this@SettingsActivity, items, config.defaultTab) {
                config.defaultTab = it as Int
                settings_default_tab.text = getDefaultTabText()
            }
        }
    }

    private fun getDefaultTabText() = getString(
        when (baseConfig.defaultTab) {
            TAB_FAVORITES -> R.string.favorites_tab
            TAB_CONTACTS -> R.string.contacts_tab
            TAB_GROUPS -> R.string.groups_tab
            else -> R.string.last_used_tab
        }
    )

    private fun setupBottomNavigationBar() {
        settings_bottom_navigation_bar.isChecked = config.bottomNavigationBar
        settings_bottom_navigation_bar_holder.setOnClickListener {
            settings_bottom_navigation_bar.toggle()
            config.bottomNavigationBar = settings_bottom_navigation_bar.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupFontSize() {
        settings_font_size.text = getFontSizeText()
        settings_font_size_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settings_font_size.text = getFontSizeText()
            }
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish

        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            System.exit(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())

        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupShowContactThumbnails() {
        settings_show_contact_thumbnails.isChecked = config.showContactThumbnails
        settings_show_contact_thumbnails_holder.setOnClickListener {
            settings_show_contact_thumbnails.toggle()
            config.showContactThumbnails = settings_show_contact_thumbnails.isChecked
        }
    }

    private fun setupShowPhoneNumbers() {
        settings_show_phone_numbers.isChecked = config.showPhoneNumbers
        settings_show_phone_numbers_holder.setOnClickListener {
            settings_show_phone_numbers.toggle()
            config.showPhoneNumbers = settings_show_phone_numbers.isChecked
        }
    }

    private fun setupShowContactsWithNumbers() {
        settings_show_only_contacts_with_numbers.isChecked = config.showOnlyContactsWithNumbers
        settings_show_only_contacts_with_numbers_holder.setOnClickListener {
            settings_show_only_contacts_with_numbers.toggle()
            config.showOnlyContactsWithNumbers = settings_show_only_contacts_with_numbers.isChecked
        }
    }

    private fun setupStartNameWithSurname() {
        settings_start_name_with_surname.isChecked = config.startNameWithSurname
        settings_start_name_with_surname_holder.setOnClickListener {
            settings_start_name_with_surname.toggle()
            config.startNameWithSurname = settings_start_name_with_surname.isChecked
        }
    }

    private fun setupShowDialpadButton() {
        settings_show_dialpad_button.isChecked = config.showDialpadButton
        settings_show_dialpad_button_holder.setOnClickListener {
            settings_show_dialpad_button.toggle()
            config.showDialpadButton = settings_show_dialpad_button.isChecked
        }
    }

    private fun setupShowPrivateContacts() {
        //val simpleDialer = "com.goodwy.dialer"
        //val simpleDialerDebug = "com.goodwy.dialer.debug"
        //settings_show_private_contacts_holder.beVisibleIf(isPackageInstalled(simpleDialer) && isPackageInstalled(simpleDialerDebug))
        settings_show_private_contacts.isChecked = config.showPrivateContacts
        settings_show_private_contacts_holder.setOnClickListener {
            settings_show_private_contacts.toggle()
            config.showPrivateContacts = settings_show_private_contacts.isChecked
        }
    }

    private fun setupOnContactClick() {
        settings_on_contact_click.text = getOnContactClickText()
        settings_on_contact_click_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ON_CLICK_CALL_CONTACT, getString(R.string.call_contact)),
                RadioItem(ON_CLICK_VIEW_CONTACT, getString(R.string.view_contact)),
                RadioItem(ON_CLICK_EDIT_CONTACT, getString(R.string.edit_contact))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.onContactClick) {
                config.onContactClick = it as Int
                settings_on_contact_click.text = getOnContactClickText()
            }
        }
    }

    private fun getOnContactClickText() = getString(
        when (config.onContactClick) {
            ON_CLICK_CALL_CONTACT -> R.string.call_contact
            ON_CLICK_VIEW_CONTACT -> R.string.view_contact
            else -> R.string.edit_contact
        }
    )

    private fun setupShowCallConfirmation() {
        settings_show_call_confirmation.isChecked = config.showCallConfirmation
        settings_show_call_confirmation_holder.setOnClickListener {
            settings_show_call_confirmation.toggle()
            config.showCallConfirmation = settings_show_call_confirmation.isChecked
        }
    }

    private fun setupMergeDuplicateContacts() {
        settings_merge_duplicate_contacts.isChecked = config.mergeDuplicateContacts
        settings_merge_duplicate_contacts_holder.setOnClickListener {
            settings_merge_duplicate_contacts.toggle()
            config.mergeDuplicateContacts = settings_merge_duplicate_contacts.isChecked
        }
    }

    private fun setupMaterialDesign3() {
        settings_material_design_3.isChecked = config.materialDesign3
        settings_material_design_3_holder.setOnClickListener {
            settings_material_design_3.toggle()
            config.materialDesign3 = settings_material_design_3.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupSettingsIcon() {
        settings_icon.applyColorFilter(getProperTextColor())
        settings_icon.setImageResource(getSettingsIcon(config.settingsIcon))
        settings_icon_holder.setOnClickListener {
            SettingsIconDialog(this) {
                config.settingsIcon = it as Int
                settings_icon.setImageResource(getSettingsIcon(it))
            }
        }
    }

    private fun setupImportContacts() {
        settings_import_contacts_chevron.applyColorFilter(getProperTextColor())
        settings_import_contacts_holder.setOnClickListener { tryImportContacts() }
    }

    private fun setupExportContacts() {
        settings_export_contacts_chevron.applyColorFilter(getProperTextColor())
        settings_export_contacts_holder.setOnClickListener { tryExportContacts() }
    }

    private fun tryImportContacts() {
        if (isQPlus()) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/x-vcard"

                try {
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) {
                if (it) {
                    importContacts()
                }
            }
        }
    }

    private fun importContacts() {
        FilePickerDialog(this) {
            showImportContactsDialog(it)
        }
    }

    private fun showImportContactsDialog(path: String) {
        ImportContactsDialog(this, path) {
            if (it) {
                runOnUiThread {
                    //refreshContacts(ALL_TABS_MASK)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            tryImportContactsFromFile(resultData.data!!)
        } else if (requestCode == PICK_EXPORT_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            try {
                val outputStream = contentResolver.openOutputStream(resultData.data!!)
                exportContactsTo(ignoredExportContactSources, outputStream)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun tryImportContactsFromFile(uri: Uri) {
        when {
            uri.scheme == "file" -> showImportContactsDialog(uri.path!!)
            uri.scheme == "content" -> {
                val tempFile = getTempFile()
                if (tempFile == null) {
                    toast(R.string.unknown_error_occurred)
                    return
                }

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val out = FileOutputStream(tempFile)
                    inputStream!!.copyTo(out)
                    showImportContactsDialog(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
            else -> toast(R.string.invalid_file_format)
        }
    }

    private fun tryExportContacts() {
        if (isQPlus()) {
            ExportContactsDialog(this, config.lastExportPath, true) { file, ignoredContactSources ->
                ignoredExportContactSources = ignoredContactSources

                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "text/x-vcard"
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        startActivityForResult(this, PICK_EXPORT_FILE_INTENT)
                    } catch (e: ActivityNotFoundException) {
                        toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    ExportContactsDialog(this, config.lastExportPath, false) { file, ignoredContactSources ->
                        getFileOutputStream(file.toFileDirItem(this), true) {
                            exportContactsTo(ignoredContactSources, it)
                        }
                    }
                }
            }
        }
    }

    private fun exportContactsTo(ignoredContactSources: HashSet<String>, outputStream: OutputStream?) {
        ContactsHelper(this).getContacts(true, false, ignoredContactSources) { contacts ->
            if (contacts.isEmpty()) {
                toast(R.string.no_entries_for_exporting)
            } else {
                VcfExporter().exportContacts(this, outputStream, contacts, true) { result ->
                    toast(
                        when (result) {
                            VcfExporter.ExportResult.EXPORT_OK -> R.string.exporting_successful
                            VcfExporter.ExportResult.EXPORT_PARTIAL -> R.string.exporting_some_entries_failed
                            else -> R.string.exporting_failed
                        }
                    )
                }
            }
        }
    }

    private fun setupUseIconTabs() {
        settings_use_icon_tabs.isChecked = config.useIconTabs
        settings_use_icon_tabs_holder.setOnClickListener {
            settings_use_icon_tabs.toggle()
            config.useIconTabs = settings_use_icon_tabs.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupShowDividers() {
        settings_show_dividers.isChecked = config.useDividers
        settings_show_dividers_holder.setOnClickListener {
            settings_show_dividers.toggle()
            config.useDividers = settings_show_dividers.isChecked
        }
    }

    private fun setupUseColoredContacts() {
        settings_colored_contacts.isChecked = config.useColoredContacts
        settings_colored_contacts_holder.setOnClickListener {
            settings_colored_contacts.toggle()
            config.useColoredContacts = settings_colored_contacts.isChecked
        }
    }

    private fun setupTipJar() {
        settings_tip_jar_holder.beVisibleIf(isOrWasThankYouInstalled() || isProVersion())
        settings_tip_jar_chevron.applyColorFilter(getProperTextColor())
        settings_tip_jar_holder.setOnClickListener {
            launchPurchase()
        }
    }

    private fun setupAbout() {
        settings_about_chevron.applyColorFilter(getProperTextColor())
        settings_about_version.text = "Version: " + BuildConfig.VERSION_NAME
        settings_about_holder.setOnClickListener {
            launchAbout()
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons_g),
            //FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons),
            FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        startAboutActivity(R.string.app_name_g, licenses, BuildConfig.VERSION_NAME, faqItems, true,
            BuildConfig.GOOGLE_PLAY_LICENSING_KEY, BuildConfig.PRODUCT_ID_X1, BuildConfig.PRODUCT_ID_X2, BuildConfig.PRODUCT_ID_X3)
    }

    private fun launchPurchase() {
        startPurchaseActivity(R.string.app_name_g, BuildConfig.GOOGLE_PLAY_LICENSING_KEY, BuildConfig.PRODUCT_ID_X1, BuildConfig.PRODUCT_ID_X2, BuildConfig.PRODUCT_ID_X3, showLifebuoy = true)
    }
}
