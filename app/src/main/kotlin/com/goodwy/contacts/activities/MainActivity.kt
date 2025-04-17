package com.goodwy.contacts.activities

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.core.view.ScrollingView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.behaviorule.arturdumchev.library.pixels
import com.goodwy.commons.databases.ContactsDatabase
import com.goodwy.commons.databinding.BottomTablayoutItemBinding
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.Release
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MySearchMenu
import com.goodwy.contacts.BuildConfig
import com.goodwy.contacts.R
import com.goodwy.contacts.adapters.ViewPagerAdapter
import com.goodwy.contacts.databinding.ActivityMainBinding
import com.goodwy.contacts.dialogs.ChangeSortingDialog
import com.goodwy.contacts.dialogs.FilterContactSourcesDialog
import com.goodwy.contacts.extensions.config
import com.goodwy.contacts.extensions.handleGenericContactClick
import com.goodwy.contacts.extensions.launchAbout
import com.goodwy.contacts.extensions.tryImportContactsFromFile
import com.goodwy.contacts.fragments.ContactsFragment
import com.goodwy.contacts.fragments.FavoritesFragment
import com.goodwy.contacts.fragments.GroupsFragment
import com.goodwy.contacts.fragments.MyViewPagerFragment
import com.goodwy.contacts.helpers.ALL_TABS_MASK
import com.goodwy.contacts.helpers.tabsList
import com.goodwy.contacts.interfaces.RefreshContactsListener
import me.grantland.widget.AutofitHelper
import java.util.*


class MainActivity : SimpleActivity(), RefreshContactsListener {
    private var isSearchOpen = false
    private var mSearchMenuItem: MenuItem? = null
    private var searchQuery = ""
    private var werePermissionsHandled = false
    private var isFirstResume = true
    private var isGettingContacts = false

    private var storedShowContactThumbnails = false
    private var storedShowPhoneNumbers = false
    private var storedStartNameWithSurname = false
    private var storedFontSize = 0
    private var storedShowTabs = 0
    private var storedBackgroundColor = 0
    private var currentOldScrollY = 0
    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        val useBottomNavigationBar = config.bottomNavigationBar
        updateMaterialActivityViews(binding.mainCoordinator, binding.mainHolder, useTransparentNavigation = false, useTopSearchMenu = useBottomNavigationBar)
        storeStateVariables()
        setupTabs()
        checkContactPermissions()
        checkWhatsNewDialog()

        // TODO TRANSPARENT Navigation Bar
        if (!useBottomNavigationBar) {
            setWindowTransparency(true) { _, bottomNavigationBarSize, leftNavigationBarSize, rightNavigationBarSize ->
                binding.mainCoordinator.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
                binding.mainAddButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    setMargins(0, 0, 0, bottomNavigationBarSize + pixels(com.goodwy.commons.R.dimen.activity_margin).toInt())
                }
            }
        }

        binding.mainMenu.updateTitle(getString(R.string.app_launcher_name))
        binding.mainMenu.searchBeVisibleIf(useBottomNavigationBar)
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

        if (storedShowTabs != config.showTabs ||storedShowPhoneNumbers != config.showPhoneNumbers) {
            System.exit(0)
            return
        }

        @SuppressLint("UnsafeIntentLaunch")
        if (config.tabsChanged || storedBackgroundColor != getProperBackgroundColor()) {
            config.lastUsedViewPagerPage = 0
            finish()
            startActivity(intent)
            return
        }

        val configShowContactThumbnails = config.showContactThumbnails
        if (storedShowContactThumbnails != configShowContactThumbnails) {
            getAllFragments().forEach {
                it?.showContactThumbnailsChanged(configShowContactThumbnails)
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        val properBackgroundColor = getProperBackgroundColor()
        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), properPrimaryColor)
            it?.setBackgroundColor(properBackgroundColor)
        }

        setupTabColors()
        updateTextColors(binding.mainCoordinator)
        binding.mainMenu.updateColors(getStartRequiredStatusBarColor(), scrollingView?.computeVerticalScrollOffset() ?: 0)

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.startNameWithSurnameChanged(configStartNameWithSurname)
            findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.startNameWithSurnameChanged(configStartNameWithSurname)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        if (werePermissionsHandled && !isFirstResume) {
            if (binding.viewPager.adapter == null) {
                initFragments()
            } else {
                if (!binding.mainMenu.isSearchOpen) refreshContacts(ALL_TABS_MASK)
            }
        }

        val dialpadIcon = resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.apply {
            setImageDrawable(dialpadIcon)
            beVisibleIf(config.showDialpadButton)
        }
        val addIcon = resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.ic_plus_vector, properPrimaryColor.getContrastColor())
        binding.mainAddButton.setImageDrawable(addIcon)

        isFirstResume = false
        checkShortcuts()
        invalidateOptionsMenu()

        //Screen slide animation
        val animation = when (config.screenSlideAnimation) {
            1 -> ZoomOutPageTransformer()
            2 -> DepthPageTransformer()
            else -> null
        }
        binding.viewPager.setPageTransformer(true, animation)
        binding.viewPager.setPagingEnabled(!config.useSwipeToAction)
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            ContactsDatabase.destroyInstance()
        }
        config.tabsChanged = false
    }

    override fun onBackPressed() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else if (isSearchOpen && mSearchMenuItem != null) {
            mSearchMenuItem!!.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        binding.mainMenu.getToolbar().menu.apply {
            findItem(R.id.search).isVisible = !config.bottomNavigationBar
            findItem(R.id.sort).isVisible = currentFragment != findViewById(R.id.groups_fragment)
            findItem(R.id.filter).isVisible = currentFragment != findViewById(R.id.groups_fragment)
            //findItem(R.id.dialpad).isVisible = !config.showDialpadButton
            findItem(R.id.change_view_type).isVisible = currentFragment == findViewById(R.id.favorites_fragment)
            findItem(R.id.column_count).isVisible = currentFragment == findViewById(R.id.favorites_fragment) && config.viewType == VIEW_TYPE_GRID
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.getToolbar().inflateMenu(R.menu.menu)
        binding.mainMenu.toggleHideOnScroll(false)

        if (config.bottomNavigationBar) {
            binding.mainMenu.setupMenu()
            binding.mainMenu.onSearchClosedListener = {
                getAllFragments().forEach {
                    it?.onSearchQueryChanged("")
//                it?.onSearchClosed()
                }
            }

            binding.mainMenu.onSearchTextChangedListener = { text ->
                getCurrentFragment()?.onSearchQueryChanged(text)
                binding.mainMenu.clearSearch()
            }
        } else setupSearch(binding.mainMenu.getToolbar().menu)

        binding.mainMenu.getToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                R.id.filter -> showFilterDialog()
                R.id.dialpad -> launchDialpad()
                R.id.change_view_type -> changeViewType()
                R.id.column_count -> changeColumnCount()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun changeViewType() {
//        ChangeViewTypeDialog(this) {
//            refreshMenuItems()
//            findViewById<FavoritesFragment>(R.id.favorites_fragment)?.updateFavouritesAdapter()
//        }
        config.viewType = if (config.viewType == VIEW_TYPE_LIST) VIEW_TYPE_GRID else VIEW_TYPE_LIST
        refreshMenuItems()
        findViewById<FavoritesFragment>(R.id.favorites_fragment)?.updateFavouritesAdapter()
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(com.goodwy.commons.R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                findViewById<FavoritesFragment>(R.id.favorites_fragment)?.columnCountChanged()
            }
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
        storedBackgroundColor = getProperBackgroundColor()
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        (mSearchMenuItem!!.actionView as SearchView).apply {
            val textColor = getProperTextColor()
            findViewById<TextView>(androidx.appcompat.R.id.search_src_text).apply {
                setTextColor(textColor)
                setHintTextColor(textColor)
            }
            findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn).apply {
                setImageResource(com.goodwy.commons.R.drawable.ic_clear_round)
                setColorFilter(textColor)
            }
            findViewById<View>(androidx.appcompat.R.id.search_plate)?.apply { // search underline
                background.setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)
            }
            setIconifiedByDefault(false)
            findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon).apply {
                setColorFilter(textColor)
            }

            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(com.goodwy.commons.R.string.search) //getString(getSearchString())
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

        @Suppress("DEPRECATION")
        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                //getCurrentFragment()?.onSearchOpened()
                isSearchOpen = true
                binding.mainDialpadButton.beGone()
                binding.mainAddButton.beGone()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchClosed()
                }

                isSearchOpen = false
                binding.mainDialpadButton.beVisibleIf(config.showDialpadButton)
                binding.mainAddButton.beVisible()
                return true
            }
        })
    }

//    private fun getSearchString(): Int {
//        return when (getCurrentFragment()) {
//            findViewById<FavoritesFragment>(R.id.favorites_fragment) -> R.string.search_favorites
//            findViewById<ContactsFragment>(R.id.contacts_fragment) -> R.string.search_contacts
//            else -> R.string.search_groups
//        }
//    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val iconColor = getProperPrimaryColor()
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != iconColor) {
            val createNewContact = getCreateNewContactShortcut(iconColor)

            try {
                shortcutManager.dynamicShortcuts = Arrays.asList(createNewContact)
                config.lastHandledShortcutColor = iconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(iconColor: Int): ShortcutInfo {
        val newEvent = getString(com.goodwy.commons.R.string.create_new_contact)
        val drawable = AppCompatResources.getDrawable(this, R.drawable.shortcut_plus)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background).applyColorFilter(iconColor)
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

    private fun getCurrentFragment(): MyViewPagerFragment<*>? {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>>()

        if (showTabs and TAB_FAVORITES != 0) {
            fragments.add(findViewById(R.id.favorites_fragment))
        }

        if (showTabs and TAB_CONTACTS != 0) {
            fragments.add(findViewById(R.id.contacts_fragment))
        }

        if (showTabs and TAB_GROUPS != 0) {
            fragments.add(findViewById(R.id.groups_fragment))
        }

        return fragments.getOrNull(binding.viewPager.currentItem)
    }

    private fun setupTabColors() {
        // bottom tab bar
        if (config.bottomNavigationBar) {
            val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
            updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[binding.viewPager.currentItem])

            getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
                val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
                updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
            }

            val bottomBarColor = getBottomNavigationBackgroundColor()
            binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
            if (binding.mainTabsHolder.tabCount != 1) updateNavigationBarColor(bottomBarColor)
            else {
                // TODO TRANSPARENT Navigation Bar
                setWindowTransparency(true) { _, bottomNavigationBarSize, leftNavigationBarSize, rightNavigationBarSize ->
                    binding.mainCoordinator.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
                    binding.mainAddButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        setMargins(0, 0, 0, bottomNavigationBarSize + pixels(com.goodwy.commons.R.dimen.activity_margin).toInt())
                    }
                }
            }
        } else {
            // top tab bar
            if (binding.viewPager.adapter != null) {

                if (config.tabsChanged) {
                    if (config.useIconTabs) {
                        binding.mainTopTabsHolder.getTabAt(0)?.text = null
                        binding.mainTopTabsHolder.getTabAt(1)?.text = null
                        binding.mainTopTabsHolder.getTabAt(2)?.text = null
                    } else {
                        binding.mainTopTabsHolder.getTabAt(0)?.icon = null
                        binding.mainTopTabsHolder.getTabAt(1)?.icon = null
                        binding.mainTopTabsHolder.getTabAt(2)?.icon = null
                    }
                }

                getInactiveTabIndexes(binding.viewPager.currentItem).forEach {
                    binding.mainTopTabsHolder.getTabAt(it)?.icon?.applyColorFilter(getProperTextColor())
                    binding.mainTopTabsHolder.getTabAt(it)?.icon?.alpha = 220 // max 255
                    binding.mainTopTabsHolder.setTabTextColors(getProperTextColor(), getProperPrimaryColor())
                }

                binding.mainTopTabsHolder.getTabAt(binding.viewPager.currentItem)?.icon?.applyColorFilter(getProperPrimaryColor())
                binding.mainTopTabsHolder.getTabAt(binding.viewPager.currentItem)?.icon?.alpha = 220 // max 255
                getAllFragments().forEach {
                    it?.setupColors(getProperTextColor(), getProperPrimaryColor())
                    binding.mainTopTabsHolder.setTabTextColors(getProperTextColor(), getProperPrimaryColor())
                }
            }

            val lastUsedPage = getDefaultTab()
            binding.mainTopTabsHolder.apply {
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

            binding.mainTopTabsHolder.onTabSelectionChanged(
                tabUnselectedAction = {
                    it.icon?.applyColorFilter(getProperTextColor())
                    it.icon?.alpha = 220 // max 255
                },
                tabSelectedAction = {
                    if (config.closeSearch) {
                        closeSearch()
                    } else {
                        //On tab switch, the search string is not deleted
                        //It should not start on the first startup
                        if (isSearchOpen) getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                    }

                    binding.viewPager.currentItem = it.position
                    it.icon?.applyColorFilter(getProperPrimaryColor())
                    it.icon?.alpha = 220 // max 255

                    if (config.openSearch) {
                        if (getCurrentFragment() is ContactsFragment) {
                            mSearchMenuItem!!.expandActionView()
                        }
                    }
                }
            )
        }
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_vector_scaled)
        }

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_rounded_scaled)
        }

        if (showTabs and TAB_GROUPS != 0) {
            icons.add(R.drawable.ic_people_rounded)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(com.goodwy.commons.R.drawable.ic_star_vector)
        }

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(com.goodwy.commons.R.drawable.ic_person_rounded)
        }

        if (showTabs and TAB_GROUPS != 0) {
            icons.add(R.drawable.ic_people_rounded_scaled)
        }

        return icons
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 2 //tabsList.size - 1
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                if (config.bottomNavigationBar) {
                    binding.mainTabsHolder.getTabAt(position)?.select()
                    if (config.changeColourTopBar) scrollChange()
                } else binding.mainTopTabsHolder.getTabAt(position)?.select()

                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
            }
        })

        binding.viewPager.onGlobalLayout {
            refreshContacts(ALL_TABS_MASK)
            refreshMenuItems()
            if (config.bottomNavigationBar && config.changeColourTopBar) scrollChange()
        }

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            tryImportContactsFromFile(intent.data!!) {
                if (it) {
                    runOnUiThread {
                        refreshContacts(ALL_TABS_MASK)
                    }
                }
            }
            intent.data = null
        }

        binding.mainDialpadButton.setOnClickListener {
            launchDialpad()
        }

        binding.mainAddButton.setOnClickListener {
            when (getCurrentFragment()) {
                findViewById<FavoritesFragment>(R.id.favorites_fragment) -> findViewById<FavoritesFragment>(R.id.favorites_fragment).fabClicked()
                findViewById<ContactsFragment>(R.id.contacts_fragment) -> findViewById<ContactsFragment>(R.id.contacts_fragment).fabClicked()
                findViewById<GroupsFragment>(R.id.groups_fragment) -> findViewById<GroupsFragment>(R.id.groups_fragment).fabClicked()
            }
        }

        binding.mainTopTabsHolder.removeAllTabs()
        var skippedTabs = 0
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value == 0) {
                skippedTabs++
            } else {
                val tab = if (config.useIconTabs) binding.mainTopTabsHolder.newTab().setIcon(getTabIcon(index)) else binding.mainTopTabsHolder.newTab().setText(getTabLabel(index))
                tab.contentDescription = getTabLabel(index)
                binding.mainTopTabsHolder.addTab(tab, index - skippedTabs, getDefaultTab() == index - skippedTabs)
                binding.mainTopTabsHolder.setTabTextColors(getProperTextColor(),
                    getProperPrimaryColor())
            }
        }

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        binding.mainTopTabsHolder.onGlobalLayout {
            Handler().postDelayed({
                binding.mainTopTabsHolder.getTabAt(getDefaultTab())?.select()
                invalidateOptionsMenu()
                refreshMenuItems()
            }, 100L)
        }

        binding.mainTopTabsContainer.beGoneIf(binding.mainTopTabsHolder.tabCount == 1 || config.bottomNavigationBar)
    }

    private fun scrollChange() {
        val myRecyclerView = getCurrentFragment()?.myRecyclerView()
        scrollingView = myRecyclerView
        val scrollingViewOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        currentOldScrollY = scrollingViewOffset
        binding.mainMenu.updateColors(getStartRequiredStatusBarColor(), scrollingViewOffset)
        setupSearchMenuScrollListenerNew(myRecyclerView, binding.mainMenu)
    }

    private fun setupSearchMenuScrollListenerNew(scrollingView: ScrollingView?, searchMenu: MySearchMenu) {
        this.scrollingView = scrollingView
        this.mySearchMenu = searchMenu
        if (scrollingView is RecyclerView) {
            scrollingView.setOnScrollChangeListener { _, _, _, _, _ ->
                val newScrollY = scrollingView.computeVerticalScrollOffset()
                if (newScrollY == 0 || currentOldScrollY == 0) scrollingChanged(newScrollY)
                currentScrollY = newScrollY
                currentOldScrollY = currentScrollY
            }
        }
    }

    private fun scrollingChanged(newScrollY: Int) {
        if (newScrollY > 0 && currentOldScrollY == 0) {
            val colorFrom = window.statusBarColor
            val colorTo = getColoredMaterialStatusBarColor()
            animateMySearchMenuColors(colorFrom, colorTo)
        } else if (newScrollY == 0 && currentOldScrollY > 0) {
            val colorFrom = window.statusBarColor
            val colorTo = getRequiredStatusBarColor()
            animateMySearchMenuColors(colorFrom, colorTo)
        }
    }

    private fun getStartRequiredStatusBarColor(): Int {
        val scrollingViewOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        return if (scrollingViewOffset == 0) {
            getProperBackgroundColor()
        } else {
            getColoredMaterialStatusBarColor()
        }
    }

    private fun setupTabs() {
        // bottom tab bar
        binding.mainTabsHolder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().setCustomView(com.goodwy.commons.R.layout.bottom_tablayout_item).apply tab@{
                    customView?.let {
                        BottomTablayoutItemBinding.bind(it)
                    }?.apply {
                        tabItemIcon.setImageDrawable(getTabIcon(index))
                        tabItemLabel.text = getTabLabel(index)
                        tabItemLabel.beGoneIf(config.useIconTabs)
                        AutofitHelper.create(tabItemLabel)
                        binding.mainTabsHolder.addTab(this@tab)
                    }
                }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position])
            },
            tabSelectedAction = {
                if (config.closeSearch) {
                    binding.mainMenu.closeSearch()
                } else {
                    //On tab switch, the search string is not deleted
                    //It should not start on the first startup
                    if (binding.mainMenu.isSearchOpen) getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }

                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])

                if (config.openSearch) {
                    if (getCurrentFragment() is ContactsFragment) {
                        binding.mainMenu.requestFocusAndShowKeyboard()
                    }
                }
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1 || !config.bottomNavigationBar)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.forceListRedraw = true
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    private fun launchDialpad() {
        hideKeyboard()
        val simpleDialer = "com.goodwy.dialer"
        val simpleDialerDebug = "com.goodwy.dialer.debug"
        if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleDialer) && !isPackageInstalled(simpleDialerDebug))) {
            runOnUiThread {
                NewAppDialog(this, simpleDialer, getString(com.goodwy.strings.R.string.recommendation_dialog_dialer_g), getString(com.goodwy.commons.R.string.right_dialer),
                    AppCompatResources.getDrawable(this, R.drawable.ic_launcher_dialer)) {}
            }
        } else {
            Intent(Intent.ACTION_DIAL).apply {
                try {
                    startActivity(this)
                } catch (e: ActivityNotFoundException) {
                    toast(com.goodwy.commons.R.string.no_app_found)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    private fun launchSettings() {
        binding.mainMenu.closeSearch()
        closeSearch()
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        if (isDestroyed || isFinishing || isGettingContacts) {
            return
        }

        isGettingContacts = true

        if (binding.viewPager.adapter == null) {
            binding.viewPager.adapter = ViewPagerAdapter(this, tabsList, config.showTabs)
            binding.viewPager.currentItem = getDefaultTab()
        }

        ContactsHelper(this).getContacts { contacts ->
            isGettingContacts = false
            if (isDestroyed || isFinishing) {
                return@getContacts
            }

            if (refreshTabsMask and TAB_FAVORITES != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts)
                }
            }

            if (refreshTabsMask and TAB_CONTACTS != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts)
                }
            }

            if (refreshTabsMask and TAB_GROUPS != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.groups_fragment)?.apply {
                    if (refreshTabsMask == TAB_GROUPS) {
                        skipHashComparing = true
                    }
                    refreshContacts(contacts)
                }
            }

            if (isSearchOpen) {
                getCurrentFragment()?.onSearchQueryChanged(searchQuery)
            }
        }
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }

    private fun getAllFragments() = arrayListOf<MyViewPagerFragment<*>?>(
        findViewById(R.id.favorites_fragment),
        findViewById(R.id.contacts_fragment),
        findViewById(R.id.groups_fragment)
    )

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
            add(Release(414, R.string.release_414))
            add(Release(500, R.string.release_500))
            add(Release(510, R.string.release_510))
            add(Release(520, R.string.release_520))
            add(Release(521, R.string.release_521))
            add(Release(522, R.string.release_522))
            add(Release(523, R.string.release_523))
            add(Release(524, R.string.release_524))
            add(Release(610, R.string.release_610))
            add(Release(611, R.string.release_611))
            add(Release(612, R.string.release_612))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
