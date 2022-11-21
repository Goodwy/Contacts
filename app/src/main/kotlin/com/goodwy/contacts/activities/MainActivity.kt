package com.goodwy.contacts.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.viewpager.widget.ViewPager
import com.goodwy.commons.dialogs.FilePickerDialog
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.Release
import com.goodwy.contacts.BuildConfig
import com.goodwy.contacts.R
import com.goodwy.contacts.adapters.ViewPagerAdapter
import com.goodwy.contacts.databases.ContactsDatabase
import com.goodwy.contacts.dialogs.ChangeSortingDialog
import com.goodwy.contacts.dialogs.ExportContactsDialog
import com.goodwy.contacts.dialogs.FilterContactSourcesDialog
import com.goodwy.contacts.dialogs.ImportContactsDialog
import com.goodwy.contacts.extensions.config
import com.goodwy.contacts.extensions.getTempFile
import com.goodwy.contacts.extensions.handleGenericContactClick
import com.goodwy.contacts.fragments.FavoritesFragment
import com.goodwy.contacts.fragments.MyViewPagerFragment
import com.goodwy.contacts.helpers.ALL_TABS_MASK
import com.goodwy.contacts.helpers.ContactsHelper
import com.goodwy.contacts.helpers.VcfExporter
import com.goodwy.contacts.helpers.tabsList
import com.goodwy.contacts.interfaces.RefreshContactsListener
import com.goodwy.contacts.models.Contact
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_contacts.*
import kotlinx.android.synthetic.main.fragment_favorites.*
import kotlinx.android.synthetic.main.fragment_groups.*
import me.grantland.widget.AutofitHelper
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*

class MainActivity : SimpleActivity(), RefreshContactsListener {
    private val PICK_IMPORT_SOURCE_INTENT = 1
    private val PICK_EXPORT_FILE_INTENT = 2

    private var isSearchOpen = false
    private var mSearchMenuItem: MenuItem? = null
    private var searchQuery = ""
    private var werePermissionsHandled = false
    private var isFirstResume = true
    private var isGettingContacts = false
    private var ignoredExportContactSources = HashSet<String>()

    private var storedShowContactThumbnails = false
    private var storedShowPhoneNumbers = false
    private var storedStartNameWithSurname = false
    private var storedFontSize = 0
    private var storedShowTabs = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        storeStateVariables()
        setupTabs()
        checkContactPermissions()
        //checkWhatsNewDialog()
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            werePermissionsHandled = true
            if (it) {
                handlePermission(PERMISSION_WRITE_CONTACTS) {
                    handlePermission(PERMISSION_GET_ACCOUNTS) {
                        initFragments()
                    }
                }
            } else {
                initFragments()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMenuItems()

        main_toolbar.menu.findItem(R.id.settings).setIcon(getSettingsIcon(config.settingsIcon))
        if (storedShowPhoneNumbers != config.showPhoneNumbers) {
            System.exit(0)
            return
        }

        if (storedShowTabs != config.showTabs || config.tabsChanged) {
            config.lastUsedViewPagerPage = 0
            System.exit(0)
            return
        }

        val configShowContactThumbnails = config.showContactThumbnails
        if (storedShowContactThumbnails != configShowContactThumbnails) {
            getAllFragments().forEach {
                it?.showContactThumbnailsChanged(configShowContactThumbnails)
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), properPrimaryColor)
        }

        setupTabColors()
        setupToolbar(main_toolbar, searchMenuItem = mSearchMenuItem)
        updateTextColors(main_coordinator)

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            contacts_fragment?.startNameWithSurnameChanged(configStartNameWithSurname)
            favorites_fragment?.startNameWithSurnameChanged(configStartNameWithSurname)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        if (werePermissionsHandled && !isFirstResume) {
            if (view_pager.adapter == null) {
                initFragments()
            } else {
                refreshContacts(ALL_TABS_MASK)
            }
        }

        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        main_dialpad_button.apply {
            setImageDrawable(dialpadIcon)
            beVisibleIf(config.showDialpadButton)
        }
        val addIcon = resources.getColoredDrawableWithColor(R.drawable.ic_plus_vector, properPrimaryColor.getContrastColor())
        main_add_button.setImageDrawable(addIcon)

        isFirstResume = false
        checkShortcuts()
        invalidateOptionsMenu()

        //Screen slide animation
        val animation = when (config.screenSlideAnimation) {
            1 -> ZoomOutPageTransformer()
            2 -> DepthPageTransformer()
            else -> null
        }
        view_pager.setPageTransformer(true, animation)

        favorites_fragment?.setBackgroundColor(getProperBackgroundColor())
        contacts_fragment?.setBackgroundColor(getProperBackgroundColor())
        groups_fragment?.setBackgroundColor(getProperBackgroundColor())
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = view_pager.currentItem
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            ContactsDatabase.destroyInstance()
        }
        config.tabsChanged = false
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        main_toolbar.menu.apply {
            findItem(R.id.sort).isVisible = currentFragment != groups_fragment
            findItem(R.id.filter).isVisible = currentFragment != groups_fragment
            //findItem(R.id.dialpad).isVisible = !config.showDialpadButton
            //findItem(R.id.settings).setIcon(getSettingsIcon(config.settingsIcon))
        }
    }

    private fun setupOptionsMenu() {
        setupSearch(main_toolbar.menu)
        main_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                R.id.filter -> showFilterDialog()
                R.id.dialpad -> launchDialpad()
                R.id.import_contacts -> tryImportContacts()
                R.id.export_contacts -> tryExportContacts()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
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

    override fun onBackPressed() {
        if (isSearchOpen && mSearchMenuItem != null) {
            mSearchMenuItem!!.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowContactThumbnails = showContactThumbnails
            storedShowPhoneNumbers = showPhoneNumbers
            storedStartNameWithSurname = startNameWithSurname
            storedShowTabs = showTabs
            storedFontSize = fontSize
            tabsChanged = false
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        (mSearchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(getSearchString())
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        searchQuery = newText
                        getCurrentFragment()?.onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                getCurrentFragment()?.onSearchOpened()
                isSearchOpen = true
                main_dialpad_button.beGone()
                main_add_button.beGone()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                getCurrentFragment()?.onSearchClosed()
                isSearchOpen = false
                main_dialpad_button.beVisibleIf(config.showDialpadButton)
                main_add_button.beVisible()
                return true
            }
        })
    }

    private fun getSearchString(): Int {
        return when (getCurrentFragment()) {
            favorites_fragment -> R.string.search_favorites
            contacts_fragment -> R.string.search_contacts
            else -> R.string.search_groups
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val createNewContact = getCreateNewContactShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = Arrays.asList(createNewContact)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.create_new_contact)
        val drawable = resources.getDrawable(R.drawable.shortcut_plus)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, EditContactActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "create_new_contact")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun getCurrentFragment(): MyViewPagerFragment? {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment>()

        if (showTabs and TAB_FAVORITES != 0) {
            fragments.add(favorites_fragment)
        }

        if (showTabs and TAB_CONTACTS != 0) {
            fragments.add(contacts_fragment)
        }

        if (showTabs and TAB_GROUPS != 0) {
            fragments.add(groups_fragment)
        }

        return fragments.getOrNull(view_pager.currentItem)
    }

    private fun setupTabColors() {
        // bottom tab bar
        val activeView = main_tabs_holder.getTabAt(view_pager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true)

        getInactiveTabIndexes(view_pager.currentItem).forEach { index ->
            val inactiveView = main_tabs_holder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false)
        }

        val bottomBarColor = getBottomNavigationBackgroundColor()
        main_tabs_holder.setBackgroundColor(bottomBarColor)
        if (config.bottomNavigationBar) updateNavigationBarColor(bottomBarColor)

        // top tab bar
        if (view_pager.adapter != null) {

            if (config.tabsChanged) {
                if (config.useIconTabs) {
                    main_top_tabs_holder.getTabAt(0)?.text = null
                    main_top_tabs_holder.getTabAt(1)?.text = null
                    main_top_tabs_holder.getTabAt(2)?.text = null
                } else {
                    main_top_tabs_holder.getTabAt(0)?.icon = null
                    main_top_tabs_holder.getTabAt(1)?.icon = null
                    main_top_tabs_holder.getTabAt(2)?.icon = null
                }
            }

            getInactiveTabIndexes(view_pager.currentItem).forEach {
                main_top_tabs_holder.getTabAt(it)?.icon?.applyColorFilter(getProperTextColor())
                main_top_tabs_holder.getTabAt(it)?.icon?.alpha = 220 // max 255
                main_top_tabs_holder.setTabTextColors(getProperTextColor(), getProperPrimaryColor())
            }

            main_top_tabs_holder.getTabAt(view_pager.currentItem)?.icon?.applyColorFilter(getProperPrimaryColor())
            main_top_tabs_holder.getTabAt(view_pager.currentItem)?.icon?.alpha = 220 // max 255
            getAllFragments().forEach {
                it?.setupColors(getProperTextColor(), getProperPrimaryColor())
                main_top_tabs_holder.setTabTextColors(getProperTextColor(), getProperPrimaryColor())
            }
        }

        val lastUsedPage = getDefaultTab()
        main_top_tabs_holder.apply {
            //background = ColorDrawable(getProperBackgroundColor())
            setSelectedTabIndicatorColor(getProperBackgroundColor())
            getTabAt(lastUsedPage)?.select()
            getTabAt(lastUsedPage)?.icon?.applyColorFilter(getProperPrimaryColor())
            getTabAt(lastUsedPage)?.icon?.alpha = 220 // max 255

            getInactiveTabIndexes(lastUsedPage).forEach {
                getTabAt(it)?.icon?.applyColorFilter(getProperTextColor())
                getTabAt(it)?.icon?.alpha = 220 // max 255
            }
        }

        main_top_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                it.icon?.applyColorFilter(getProperTextColor())
                it.icon?.alpha = 220 // max 255
            },
            tabSelectedAction = {
                view_pager.currentItem = it.position
                it.icon?.applyColorFilter(getProperPrimaryColor())
                it.icon?.alpha = 220 // max 255

            }
        )
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until tabsList.size).filter { it != activeIndex }

    private fun initFragments() {
        view_pager.offscreenPageLimit = 2 //tabsList.size - 1
        view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                main_top_tabs_holder.getTabAt(position)?.select()
                main_tabs_holder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
            }
        })

        view_pager.onGlobalLayout {
            refreshContacts(ALL_TABS_MASK)
            refreshMenuItems()
        }

        /*main_top_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                it.icon?.applyColorFilter(getProperTextColor())
            },
            tabSelectedAction = {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged("")
                    mSearchMenuItem?.collapseActionView()
                }
                view_pager.currentItem = it.position
                it.icon?.applyColorFilter(getProperPrimaryColor())
            }
        )*/

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            tryImportContactsFromFile(intent.data!!)
            intent.data = null
        }

        main_dialpad_button.setOnClickListener {
            launchDialpad()
        }

        main_add_button.setOnClickListener {
            when (getCurrentFragment()) {
                favorites_fragment -> favorites_fragment.fabClicked()
                contacts_fragment -> contacts_fragment.fabClicked()
                groups_fragment -> groups_fragment.fabClicked()
            }
        }

        main_top_tabs_holder.removeAllTabs()
        var skippedTabs = 0
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value == 0) {
                skippedTabs++
            } else {
                val tab = if (config.useIconTabs) main_top_tabs_holder.newTab().setIcon(getTabIcon(index)) else main_top_tabs_holder.newTab().setText(getTabLabel(index))
                tab.contentDescription = getTabLabel(index)
                main_top_tabs_holder.addTab(tab, index - skippedTabs, getDefaultTab() == index - skippedTabs)
                main_top_tabs_holder.setTabTextColors(getProperTextColor(),
                    getProperPrimaryColor())
            }
        }

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        main_top_tabs_holder.onGlobalLayout {
            Handler().postDelayed({
                main_top_tabs_holder.getTabAt(getDefaultTab())?.select()
                invalidateOptionsMenu()
                refreshMenuItems()
            }, 100L)
        }

        //main_top_tabs_holder.beVisibleIf(skippedTabs < tabsList.size - 1)
        main_top_tabs_container.beGoneIf(main_top_tabs_holder.tabCount == 1 || config.bottomNavigationBar)
    }

    private fun setupTabs() {
        // bottom tab bar
        main_tabs_holder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                main_tabs_holder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.apply {
                        setImageDrawable(getTabIcon(index))
                        alpha = 0.86f
                    }
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.apply {
                        text = getTabLabel(index)
                        alpha = 0.86f
                        beGoneIf(config.useIconTabs)
                    }
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    main_tabs_holder.addTab(this)
                }
            }
        }

        main_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false)
            },
            tabSelectedAction = {
                closeSearch()
                view_pager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true)
            }
        )

        main_tabs_holder.beGoneIf(main_tabs_holder.tabCount == 1 || !config.bottomNavigationBar)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            contacts_fragment?.forceListRedraw = true
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    private fun launchDialpad() {
        hideKeyboard()
        val simpleDialer = "com.goodwy.dialer"
        val simpleDialerDebug = "com.goodwy.dialer.debug"
        if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleDialer) && !isPackageInstalled(simpleDialerDebug))) {
            NewAppDialog(this, simpleDialer, getString(R.string.recommendation_dialog_contacts_g), getString(R.string.right_dialer),
                getDrawable(R.drawable.ic_launcher_dialer)) {}
        } else {
            Intent(Intent.ACTION_DIAL).apply {
                try {
                    startActivity(this)
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.no_app_found)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
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
                    refreshContacts(ALL_TABS_MASK)
                }
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

    private fun launchSettings() {
        closeSearch()
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        closeSearch()
        val licenses = LICENSE_JODA or LICENSE_GLIDE or LICENSE_GSON or LICENSE_INDICATOR_FAST_SCROLL or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
            faqItems.add(FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true,
            BuildConfig.GOOGLE_PLAY_LICENSING_KEY, BuildConfig.PRODUCT_ID_X1, BuildConfig.PRODUCT_ID_X2, BuildConfig.PRODUCT_ID_X3)
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        if (isDestroyed || isFinishing || isGettingContacts) {
            return
        }

        isGettingContacts = true

        if (view_pager.adapter == null) {
            view_pager.adapter = ViewPagerAdapter(this, tabsList, config.showTabs)
            view_pager.currentItem = getDefaultTab()
        }

        ContactsHelper(this).getContacts { contacts ->
            isGettingContacts = false
            if (isDestroyed || isFinishing) {
                return@getContacts
            }

            if (refreshTabsMask and TAB_FAVORITES != 0) {
                favorites_fragment?.skipHashComparing = true
                favorites_fragment?.refreshContacts(contacts)
            }

            if (refreshTabsMask and TAB_CONTACTS != 0) {
                contacts_fragment?.skipHashComparing = true
                contacts_fragment?.refreshContacts(contacts)
            }

            if (refreshTabsMask and TAB_GROUPS != 0) {
                if (refreshTabsMask == TAB_GROUPS) {
                    groups_fragment.skipHashComparing = true
                }
                groups_fragment?.refreshContacts(contacts)
            }

            if (isSearchOpen) {
                getCurrentFragment()?.onSearchQueryChanged(searchQuery)
            }
        }
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }

    private fun getAllFragments() = arrayListOf(favorites_fragment, contacts_fragment, groups_fragment)

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        return when (config.defaultTab) {
            TAB_LAST_USED -> config.lastUsedViewPagerPage
            TAB_FAVORITES -> 0
            TAB_CONTACTS -> if (showTabsMask and TAB_FAVORITES > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_GROUPS > 0) {
                    if (showTabsMask and TAB_FAVORITES > 0) {
                        if (showTabsMask and TAB_CONTACTS > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_CONTACTS > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun closeSearch() {
        if (isSearchOpen) {
            getAllFragments().forEach {
                it?.onSearchQueryChanged("")
            }
            mSearchMenuItem?.collapseActionView()
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(10, R.string.release_10))
            add(Release(11, R.string.release_11))
            add(Release(16, R.string.release_16))
            add(Release(27, R.string.release_27))
            add(Release(29, R.string.release_29))
            add(Release(31, R.string.release_31))
            add(Release(32, R.string.release_32))
            add(Release(34, R.string.release_34))
            add(Release(39, R.string.release_39))
            add(Release(40, R.string.release_40))
            add(Release(47, R.string.release_47))
            add(Release(56, R.string.release_56))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
