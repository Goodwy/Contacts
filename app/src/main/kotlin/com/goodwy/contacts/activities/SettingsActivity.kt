package com.goodwy.contacts.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.activities.FAQActivity
import com.goodwy.commons.dialogs.*
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.helpers.rustore.RuStoreHelper
import com.goodwy.commons.helpers.rustore.model.StartPurchasesEvent
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.SimpleListItem
import com.goodwy.contacts.BuildConfig
import com.goodwy.contacts.R
import com.goodwy.contacts.databinding.ActivitySettingsBinding
import com.goodwy.contacts.dialogs.ExportContactsDialog
import com.goodwy.contacts.dialogs.ManageAutoBackupsDialog
import com.goodwy.contacts.dialogs.ManageVisibleFieldsDialog
import com.goodwy.contacts.dialogs.ManageVisibleTabsDialog
import com.goodwy.contacts.extensions.*
import com.goodwy.contacts.helpers.VcfExporter
import kotlinx.coroutines.launch
import ru.rustore.sdk.core.feature.model.FeatureAvailabilityResult
import java.io.OutputStream
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    companion object {
        private const val PICK_IMPORT_SOURCE_INTENT = 1
        private const val PICK_EXPORT_FILE_INTENT = 2
    }

    private val binding by viewBinding(ActivitySettingsBinding::inflate)
    private val purchaseHelper = PurchaseHelper(this)
    private val ruStoreHelper = RuStoreHelper(this)
    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    private val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    private val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    private val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3
    private var ruStoreIsConnected = false

    private var ignoredExportContactSources = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.settingsCoordinator, binding.settingsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsToolbar)

        if (isPlayStoreInstalled()) {
            //PlayStore
            purchaseHelper.initBillingClient()
            val iapList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
            val subList: ArrayList<String> = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3, subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3)
            purchaseHelper.retrieveDonation(iapList, subList)

            purchaseHelper.isIapPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isPro = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isPro = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }

            purchaseHelper.isSupPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isProSubs = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isProSubs = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }
        }
        if (isRuStoreInstalled()) {
            //RuStore
            ruStoreHelper.checkPurchasesAvailability()

            lifecycleScope.launch {
                ruStoreHelper.eventStart
                    .flowWithLifecycle(lifecycle)
                    .collect { event ->
                        handleEventStart(event)
                    }
            }

            lifecycleScope.launch {
                ruStoreHelper.statePurchased
                    .flowWithLifecycle(lifecycle)
                    .collect { state ->
                        //update of purchased
                        if (!state.isLoading && ruStoreIsConnected) {
                            baseConfig.isProRuStore = state.purchases.firstOrNull() != null
                            updatePro()
                        }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()

        setupCustomizeColors()
        setupShowDialpadButton()
        setupMaterialDesign3()
        setupOverflowIcon()
        setupUseColoredContacts()
        setupContactsColorList()

        setupDefaultTab()
        setupManageShownTabs()
        setupNavigationBarStyle()
        setupUseIconTabs()
        setupScreenSlideAnimation()
        setupOpenSearch()

        setupManageShownContactFields()
        setupMergeDuplicateContacts()
        setupShowCallConfirmation()
        setupShowPrivateContacts()
        setupOnContactClick()
        setupShowContactsWithNumbers()
        setupFontSize()
        setupUseEnglish()
        setupLanguage()

        setupShowDividers()
        setupShowContactThumbnails()
        setupShowPhoneNumbers()
        setupStartNameWithSurname()

        setupExportContacts()
        setupImportContacts()
        setupEnableAutomaticBackups()
        setupInfoAutomaticBackups()
        setupManageAutomaticBackups()

        setupTipJar()
        setupAbout()

        updateTextColors(binding.settingsHolder)

        arrayOf(
            binding.settingsAppearanceLabel,
            binding.settingsTabsLabel,
            binding.settingsGeneralLabel,
            binding.settingsListViewLabel,
            binding.settingsBackupsLabel,
            binding.settingsOtherLabel).forEach {
            it.setTextColor(getProperPrimaryColor())
        }

        arrayOf(
            binding.settingsColorCustomizationHolder,
            binding.settingsTabsHolder,
            binding.settingsGeneralHolder,
            binding.settingsListViewHolder,
            binding.settingsBackupsHolder,
            binding.settingsOtherHolder
        ).forEach {
            it.background.applyColorFilter(getBottomNavigationBackgroundColor())
        }

        arrayOf(
            binding.settingsCustomizeColorsChevron,
            binding.settingsManageShownTabsChevron,
            binding.settingsManageContactFieldsChevron,
            binding.settingsManageContactFieldsChevron,
            binding.contactsImportChevron,
            binding.contactsExportChevron,
            binding.settingsManageAutomaticBackupsChevron,
            binding.settingsTipJarChevron,
            binding.settingsAboutChevron
        ).forEach {
            it.applyColorFilter(getProperTextColor())
        }
    }

    private fun setupPurchaseThankYou() = binding.apply {
        settingsPurchaseThankYouHolder.beGoneIf(isPro())
        settingsPurchaseThankYouHolder.setOnClickListener {
            launchPurchase()
        }
        moreButton.setOnClickListener {
            launchPurchase()
        }
        val appDrawable = resources.getColoredDrawableWithColor(this@SettingsActivity, com.goodwy.commons.R.drawable.ic_plus_support, getProperPrimaryColor())
        purchaseLogo.setImageDrawable(appDrawable)
        val drawable = resources.getColoredDrawableWithColor(this@SettingsActivity, com.goodwy.commons.R.drawable.button_gray_bg, getProperPrimaryColor())
        moreButton.background = drawable
        moreButton.setTextColor(getProperBackgroundColor())
        moreButton.setPadding(2, 2, 2, 2)
    }

    private fun setupCustomizeColors() = binding.apply {
//        settingsCustomizeColorsLabel.text = if (isPro()) {
//            getString(com.goodwy.commons.R.string.customize_colors)
//        } else {
//            getString(com.goodwy.commons.R.string.customize_colors_locked)
//        }
        settingsCustomizeColorsHolder.setOnClickListener {
            startCustomizationActivity(
                showAccentColor = false,
                licensingKey = BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
                productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
                productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
                subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                playStoreInstalled = isPlayStoreInstalled(),
                ruStoreInstalled = isRuStoreInstalled()
            )
        }
    }

    private fun setupManageShownContactFields() {
        binding.settingsManageContactFieldsHolder.setOnClickListener {
            ManageVisibleFieldsDialog(this) {}
        }
    }

    private fun setupManageShownTabs() {
        binding.settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupScreenSlideAnimation() {
        binding.settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
        binding.settingsScreenSlideAnimationHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, getString(com.goodwy.commons.R.string.no)),
                RadioItem(1, getString(com.goodwy.commons.R.string.screen_slide_animation_zoomout)),
                RadioItem(2, getString(com.goodwy.commons.R.string.screen_slide_animation_depth)))

            RadioGroupDialog(this@SettingsActivity, items, config.screenSlideAnimation) {
                config.screenSlideAnimation = it as Int
                config.tabsChanged = true
                binding.settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
            }
        }
    }

    private fun setupOpenSearch() {
        binding.apply {
            settingsOpenSearch.isChecked = config.openSearch
            settingsOpenSearchHolder.setOnClickListener {
                settingsOpenSearch.toggle()
                config.openSearch = settingsOpenSearch.isChecked
            }
        }
    }

    private fun setupDefaultTab() {
        binding.settingsDefaultTab.text = getDefaultTabText()
        binding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_LAST_USED, getString(com.goodwy.commons.R.string.last_used_tab)),
                RadioItem(TAB_FAVORITES, getString(com.goodwy.commons.R.string.favorites_tab)),
                RadioItem(TAB_CONTACTS, getString(com.goodwy.commons.R.string.contacts_tab)),
                RadioItem(TAB_GROUPS, getString(com.goodwy.commons.R.string.groups_tab)))

            RadioGroupDialog(this@SettingsActivity, items, config.defaultTab) {
                config.defaultTab = it as Int
                binding.settingsDefaultTab.text = getDefaultTabText()
            }
        }
    }

    private fun getDefaultTabText() = getString(
        when (baseConfig.defaultTab) {
            TAB_FAVORITES -> com.goodwy.commons.R.string.favorites_tab
            TAB_CONTACTS -> com.goodwy.commons.R.string.contacts_tab
            TAB_GROUPS -> com.goodwy.commons.R.string.groups_tab
            else -> com.goodwy.commons.R.string.last_used_tab
        }
    )

    private fun setupNavigationBarStyle() {
        binding.settingsNavigationBarStyle.text = getNavigationBarStyleText()
        binding.settingsNavigationBarStyleHolder.setOnClickListener {
            launchNavigationBarStyleDialog()
        }
    }

    private fun launchNavigationBarStyleDialog() {
        BottomSheetChooserDialog.createChooser(
            fragmentManager = supportFragmentManager,
            title = com.goodwy.commons.R.string.tab_navigation,
            items = arrayOf(
                SimpleListItem(0, com.goodwy.commons.R.string.top, imageRes = com.goodwy.commons.R.drawable.ic_tab_top, selected = !config.bottomNavigationBar),
                SimpleListItem(1, com.goodwy.commons.R.string.bottom, imageRes = com.goodwy.commons.R.drawable.ic_tab_bottom, selected = config.bottomNavigationBar)
            )
        ) {
            config.bottomNavigationBar = it.id == 1
            config.tabsChanged = true
            binding.settingsNavigationBarStyle.text = getNavigationBarStyleText()
        }
    }

    private fun setupFontSize() {
        binding.settingsFontSize.text = getFontSizeText()
        binding.settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(com.goodwy.commons.R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(com.goodwy.commons.R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(com.goodwy.commons.R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(com.goodwy.commons.R.string.extra_large)))

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                binding.settingsFontSize.text = getFontSizeText()
                config.tabsChanged = true
            }
        }
    }

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsUseEnglish.isChecked = config.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupShowContactThumbnails() {
        binding.apply {
            settingsShowContactThumbnails.isChecked = config.showContactThumbnails
            settingsShowContactThumbnailsHolder.setOnClickListener {
                settingsShowContactThumbnails.toggle()
                config.showContactThumbnails = settingsShowContactThumbnails.isChecked
            }
        }
    }

    private fun setupShowPhoneNumbers() {
        binding.apply {
            settingsShowPhoneNumbers.isChecked = config.showPhoneNumbers
            settingsShowPhoneNumbersHolder.setOnClickListener {
                settingsShowPhoneNumbers.toggle()
                config.showPhoneNumbers = settingsShowPhoneNumbers.isChecked
            }
        }
    }

    private fun setupShowContactsWithNumbers() {
        binding.apply {
            settingsShowOnlyContactsWithNumbers.isChecked = config.showOnlyContactsWithNumbers
            settingsShowOnlyContactsWithNumbersHolder.setOnClickListener {
                settingsShowOnlyContactsWithNumbers.toggle()
                config.showOnlyContactsWithNumbers = settingsShowOnlyContactsWithNumbers.isChecked
            }
        }
    }

    private fun setupStartNameWithSurname() {
        binding.apply {
            settingsStartNameWithSurname.isChecked = config.startNameWithSurname
            settingsStartNameWithSurnameHolder.setOnClickListener {
                settingsStartNameWithSurname.toggle()
                config.startNameWithSurname = settingsStartNameWithSurname.isChecked
            }
        }
    }

    private fun setupShowDialpadButton() {
        binding.apply {
            settingsShowDialpadButton.isChecked = config.showDialpadButton
            settingsShowDialpadButtonHolder.setOnClickListener {
                settingsShowDialpadButton.toggle()
                config.showDialpadButton = settingsShowDialpadButton.isChecked
            }
        }
    }

    private fun setupShowPrivateContacts() {
        binding.apply {
            //val simpleDialer = "com.goodwy.dialer"
            //val simpleDialerDebug = "com.goodwy.dialer.debug"
            //settings_show_private_contacts_holder.beVisibleIf(isPackageInstalled(simpleDialer) && isPackageInstalled(simpleDialerDebug))
            settingsShowPrivateContacts.isChecked = config.showPrivateContacts
            settingsShowPrivateContactsHolder.setOnClickListener {
                settingsShowPrivateContacts.toggle()
                config.showPrivateContacts = settingsShowPrivateContacts.isChecked
            }
            settingsShowPrivateContactsFaq.imageTintList = ColorStateList.valueOf(getProperTextColor())
            val faqItems = arrayListOf(
                FAQItem(com.goodwy.commons.R.string.faq_100_title_commons_g, com.goodwy.commons.R.string.faq_100_text_commons_g),
                FAQItem(com.goodwy.commons.R.string.faq_101_title_commons_g, com.goodwy.commons.R.string.faq_101_text_commons_g, R.string.phone_storage_hidden),
            )
            settingsShowPrivateContactsFaq.setOnClickListener {
                openFAQ(faqItems)
            }
        }
    }

    private fun setupOnContactClick() {
        binding.apply {
            settingsOnContactClick.text = getOnContactClickText()
            settingsOnContactClickHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(ON_CLICK_CALL_CONTACT, getString(R.string.call_contact)),
                    RadioItem(ON_CLICK_VIEW_CONTACT, getString(R.string.view_contact)),
                    RadioItem(ON_CLICK_EDIT_CONTACT, getString(R.string.edit_contact))
                )

                RadioGroupDialog(this@SettingsActivity, items, config.onContactClick) {
                    config.onContactClick = it as Int
                    settingsOnContactClick.text = getOnContactClickText()
                }
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
        binding.apply {
            settingsShowCallConfirmation.isChecked = config.showCallConfirmation
            settingsShowCallConfirmationHolder.setOnClickListener {
                settingsShowCallConfirmation.toggle()
                config.showCallConfirmation = settingsShowCallConfirmation.isChecked
            }
        }
    }

    private fun setupMergeDuplicateContacts() {
        binding.apply {
            settingsMergeDuplicateContacts.isChecked = config.mergeDuplicateContacts
            settingsMergeDuplicateContactsHolder.setOnClickListener {
                settingsMergeDuplicateContacts.toggle()
                config.mergeDuplicateContacts = settingsMergeDuplicateContacts.isChecked
            }
        }
    }

    private fun setupEnableAutomaticBackups() {
        val getBottomNavigationBackgroundColor = getBottomNavigationBackgroundColor()
        val wrapperColor = if (config.autoBackup) getBottomNavigationBackgroundColor.lightenColor(4) else getBottomNavigationBackgroundColor
        binding.settingsAutomaticBackupsWrapper.background.applyColorFilter(wrapperColor)

        binding.settingsBackupsLabel.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackupsHolder.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackups.isChecked = config.autoBackup
        binding.settingsEnableAutomaticBackupsHolder.setOnClickListener {
            val wasBackupDisabled = !config.autoBackup
            if (wasBackupDisabled) {
                ManageAutoBackupsDialog(
                    activity = this,
                    onSuccess = {
                        enableOrDisableAutomaticBackups(true)
                        scheduleNextAutomaticBackup()
                        updateAutomaticBackupsLastAndNext()
                        binding.settingsAutomaticBackupsWrapper.background.applyColorFilter(getBottomNavigationBackgroundColor.lightenColor(4))
                    }
                )
            } else {
                cancelScheduledAutomaticBackup()
                enableOrDisableAutomaticBackups(false)
                binding.settingsAutomaticBackupsWrapper.background.applyColorFilter(getBottomNavigationBackgroundColor)
            }
        }
    }

    private fun setupInfoAutomaticBackups() {
        binding.settingsInfoAutomaticBackupsHolder.beVisibleIf(isRPlus() && config.autoBackup)

        binding.settingsInfoAutomaticBackupsCreate.apply {
            val getProperPrimaryColor = getProperPrimaryColor()
            setTextColor(getProperPrimaryColor.getContrastColor())
            background.setTint(getProperPrimaryColor)
            setOnClickListener {
                backupContacts{success -> updateAutomaticBackupsLastAndNext() }
            }
        }
        updateAutomaticBackupsLastAndNext()
    }

    private fun setupManageAutomaticBackups() {
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(isRPlus() && config.autoBackup)
        binding.settingsManageAutomaticBackupsHolder.setOnClickListener {
            ManageAutoBackupsDialog(
                activity = this,
                onSuccess = {
                    scheduleNextAutomaticBackup()
                    updateAutomaticBackupsLastAndNext()
                }
            )
        }
    }

    private fun updateAutomaticBackupsLastAndNext() {
        val lastAutoBackup = if (config.lastAutoBackupTime == 0L) getString(com.goodwy.commons.R.string.none)
                                    else config.lastAutoBackupTime.formatDate(this)
        val lastAutoBackupText = getString(com.goodwy.commons.R.string.last_g, lastAutoBackup)
        binding.settingsInfoAutomaticBackupsLast.text = lastAutoBackupText
        binding.settingsInfoAutomaticBackupsLast.contentDescription = lastAutoBackupText

        val nextBackup = if (config.nextAutoBackupTime == 0L) getString(com.goodwy.commons.R.string.none)
                                else config.nextAutoBackupTime.formatDate(this)
        val nextAutoBackupText = getString(com.goodwy.commons.R.string.next_g, nextBackup)
        binding.settingsInfoAutomaticBackupsNext.text = nextAutoBackupText
        binding.settingsInfoAutomaticBackupsNext.contentDescription = nextAutoBackupText
    }

    private fun enableOrDisableAutomaticBackups(enable: Boolean) {
        config.autoBackup = enable
        binding.settingsEnableAutomaticBackups.isChecked = enable
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(enable)
        binding.settingsInfoAutomaticBackupsHolder.beVisibleIf(enable)
    }

    private fun setupExportContacts() {
        binding.contactsExportHolder.setOnClickListener {
            tryExportContacts()
        }
    }

    private fun setupImportContacts() {
        binding.contactsImportHolder.setOnClickListener {
            tryImportContacts()
        }
    }

    private fun tryImportContacts() {
        if (isQPlus()) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/x-vcard"

                try {
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(com.goodwy.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
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
            showImportContactsDialog(it) {}
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
                        toast(com.goodwy.commons.R.string.no_app_found, Toast.LENGTH_LONG)
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
                toast(com.goodwy.commons.R.string.no_entries_for_exporting)
            } else {
                VcfExporter().exportContacts(this, outputStream, contacts, true) { result ->
                    toast(
                        when (result) {
                            VcfExporter.ExportResult.EXPORT_OK -> com.goodwy.commons.R.string.exporting_successful
                            VcfExporter.ExportResult.EXPORT_PARTIAL -> com.goodwy.commons.R.string.exporting_some_entries_failed
                            else -> com.goodwy.commons.R.string.exporting_failed
                        }
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == Activity.RESULT_OK && resultData?.data != null) {
            tryImportContactsFromFile(resultData.data!!) {}
        } else if (requestCode == PICK_EXPORT_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData?.data != null) {
            try {
                val outputStream = contentResolver.openOutputStream(resultData.data!!)
                exportContactsTo(ignoredExportContactSources, outputStream)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun setupMaterialDesign3() {
        binding.apply {
            settingsMaterialDesign3.isChecked = config.materialDesign3
            settingsMaterialDesign3Holder.setOnClickListener {
                settingsMaterialDesign3.toggle()
                config.materialDesign3 = settingsMaterialDesign3.isChecked
                config.tabsChanged = true
            }
        }
    }

    private fun setupOverflowIcon() = binding.apply {
        settingsOverflowIcon.applyColorFilter(getProperTextColor())
        settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
        settingsOverflowIconHolder.setOnClickListener {
            OverflowIconDialog(this@SettingsActivity) {
                settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
            }
        }
    }

    private fun setupUseIconTabs() {
        binding.apply {
            settingsUseIconTabs.isChecked = config.useIconTabs
            settingsUseIconTabsHolder.setOnClickListener {
                settingsUseIconTabs.toggle()
                config.useIconTabs = settingsUseIconTabs.isChecked
                config.tabsChanged = true
            }
        }
    }

    private fun setupShowDividers() {
        binding.apply {
            settingsShowDividers.isChecked = config.useDividers
            settingsShowDividersHolder.setOnClickListener {
                settingsShowDividers.toggle()
                config.useDividers = settingsShowDividers.isChecked
            }
        }
    }

    private fun setupUseColoredContacts() = binding.apply {
        updateWrapperUseColoredContacts()
            settingsColoredContacts.isChecked = config.useColoredContacts
            settingsColoredContactsHolder.setOnClickListener {
                settingsColoredContacts.toggle()
                config.useColoredContacts = settingsColoredContacts.isChecked
                settingsContactColorListHolder.beVisibleIf(config.useColoredContacts)
                updateWrapperUseColoredContacts()
            }
    }

    private fun updateWrapperUseColoredContacts() {
        val getBottomNavigationBackgroundColor = getBottomNavigationBackgroundColor()
        val wrapperColor = if (config.useColoredContacts) getBottomNavigationBackgroundColor.lightenColor(4) else getBottomNavigationBackgroundColor
        binding.settingsColoredContactsWrapper.background.applyColorFilter(wrapperColor)
    }

    private fun setupContactsColorList() = binding.apply {
        settingsContactColorListHolder.beVisibleIf(config.useColoredContacts)
        settingsContactColorListIcon.setImageResource(getContactsColorListIcon(config.contactColorList))
        settingsContactColorListHolder.setOnClickListener {
            ColorListDialog(this@SettingsActivity) {
                config.contactColorList = it as Int
                settingsContactColorListIcon.setImageResource(getContactsColorListIcon(it))
            }
        }
    }

    private fun setupTipJar() = binding.apply {
        settingsTipJarHolder.apply {
            beVisibleIf(isPro())
            background.applyColorFilter(getBottomNavigationBackgroundColor().lightenColor(4))
            setOnClickListener {
                launchPurchase()
            }
        }
    }

    private fun setupAbout() = binding.apply {
        settingsAboutVersion.text = "Version: " + BuildConfig.VERSION_NAME
        settingsAboutHolder.setOnClickListener {
            launchAbout()
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(com.goodwy.commons.R.string.faq_9_title_commons, com.goodwy.commons.R.string.faq_9_text_commons),
            FAQItem(com.goodwy.commons.R.string.faq_100_title_commons_g, com.goodwy.commons.R.string.faq_100_text_commons_g),
            FAQItem(com.goodwy.commons.R.string.faq_101_title_commons_g, com.goodwy.commons.R.string.faq_101_text_commons_g, R.string.phone_storage_hidden),
            FAQItem(com.goodwy.commons.R.string.faq_2_title_commons, com.goodwy.commons.R.string.faq_2_text_commons_g),
        )

        startAboutActivity(
            appNameId = R.string.app_name_g,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true,
            licensingKey = BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
            productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
            productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
            subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            playStoreInstalled = isPlayStoreInstalled(),
            ruStoreInstalled = isRuStoreInstalled()
        )
    }

    private fun launchPurchase() {
        startPurchaseActivity(
            R.string.app_name_g,
            BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
            productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
            productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
            subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            playStoreInstalled = isPlayStoreInstalled(),
            ruStoreInstalled = isRuStoreInstalled()
        )
    }

    private fun openFAQ(faqItems: ArrayList<FAQItem>) {
        Intent(applicationContext, FAQActivity::class.java).apply {
            putExtra(APP_ICON_IDS, getAppIconIDs())
            putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
            putExtra(APP_FAQ, faqItems)
            startActivity(this)
        }
    }

    private fun updatePro(isPro: Boolean = isPro()) {
        binding.apply {
            settingsPurchaseThankYouHolder.beGoneIf(isPro)
//            settingsCustomizeColorsLabel.text = if (isPro) {
//                getString(com.goodwy.commons.R.string.customize_colors)
//            } else {
//                getString(com.goodwy.commons.R.string.customize_colors_locked)
//            }
            settingsTipJarHolder.beVisibleIf(isPro)
        }
    }

    private fun updateProducts() {
        val productList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3, subscriptionIdX1, subscriptionIdX2, subscriptionIdX3, subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3)
        ruStoreHelper.getProducts(productList)
    }

    private fun handleEventStart(event: StartPurchasesEvent) {
        when (event) {
            is StartPurchasesEvent.PurchasesAvailability -> {
                when (event.availability) {
                    is FeatureAvailabilityResult.Available -> {
                        //Process purchases available
                        updateProducts()
                        ruStoreIsConnected = true
                    }

                    is FeatureAvailabilityResult.Unavailable -> {
                        //toast(event.availability.cause.message ?: "Process purchases unavailable", Toast.LENGTH_LONG)
                    }

                    else -> {}
                }
            }

            is StartPurchasesEvent.Error -> {
                //toast(event.throwable.message ?: "Process unknown error", Toast.LENGTH_LONG)
            }
        }
    }
}
