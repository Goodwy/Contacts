package com.goodwy.contacts.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.behaviorule.arturdumchev.library.pixels
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.interfaces.ItemMoveCallback
import com.goodwy.commons.interfaces.ItemTouchHelperContract
import com.goodwy.commons.interfaces.StartReorderDragListener
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.contacts.BuildConfig
import com.goodwy.contacts.R
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.activities.ViewContactActivity
import com.goodwy.contacts.dialogs.CreateNewGroupDialog
import com.goodwy.contacts.extensions.*
import com.goodwy.contacts.helpers.*
import com.goodwy.contacts.interfaces.RefreshContactsListener
import com.goodwy.contacts.interfaces.RemoveFromGroupListener
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeDirection
import me.thanel.swipeactionview.SwipeGestureListener
import java.util.Collections
import kotlin.math.abs
import androidx.core.graphics.drawable.toDrawable

class ContactsAdapter(
    activity: SimpleActivity,
    var contactItems: MutableList<Contact>,
    recyclerView: MyRecyclerView,
    highlightText: String = "",
    var viewType: Int = VIEW_TYPE_LIST,
    private val refreshListener: RefreshContactsListener?,
    private val location: Int,
    private val removeListener: RemoveFromGroupListener?,
    private val enableDrag: Boolean = false,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate, ItemTouchHelperContract {

    private val NEW_GROUP_ID = -1

    private var config = activity.config
    private var textToHighlight = highlightText

    var startNameWithSurname = config.startNameWithSurname
    var showContactThumbnails = config.showContactThumbnails
    var showPhoneNumbers = config.showPhoneNumbers
    var fontSize = activity.getTextSize()
    var fontSizeSmall = activity.getTextSizeSmall()
    var onDragEndListener: (() -> Unit)? = null

    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener? = null

    init {
        setupDragListener(true)

        if (enableDrag) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this, true/*viewType == VIEW_TYPE_GRID*/))
            touchHelper!!.attachToRecyclerView(recyclerView)

            startReorderDragListener = object : StartReorderDragListener {
                override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                    touchHelper?.startDrag(viewHolder)
                }
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_edit).isVisible = isOneItemSelected()
            findItem(R.id.cab_remove).isVisible = location == LOCATION_FAVORITES_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_add_to_favorites).isVisible = (location == LOCATION_CONTACTS_TAB || location == LOCATION_GROUP_CONTACTS) && getSelectedItems().all {it.starred != 1}
            findItem(R.id.cab_add_to_group).isVisible = location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB
            findItem(R.id.cab_call).isVisible = isOneItemSelected()
            findItem(R.id.cab_send_sms_to_contacts).isVisible =
                location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_send_email_to_contacts).isVisible =
                location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_delete).isVisible = location == LOCATION_CONTACTS_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_create_shortcut).isVisible =
                isOreoPlus() && isOneItemSelected() && (location == LOCATION_FAVORITES_TAB || location == LOCATION_CONTACTS_TAB)

            if (location == LOCATION_GROUP_CONTACTS) {
                findItem(R.id.cab_remove).title = activity.getString(R.string.remove_from_group)
            }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_edit -> editContact()
            R.id.cab_select_all -> selectAll()
            R.id.cab_add_to_favorites -> addToFavorites()
            R.id.cab_add_to_group -> addToGroup()
            R.id.cab_share -> shareContacts()
            R.id.cab_call -> callContact()
            R.id.cab_send_sms_to_contacts -> sendSMSToContacts()
            R.id.cab_send_email_to_contacts -> sendEmailToContacts()
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_remove -> removeContacts()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = contactItems.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contactItems.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = contactItems.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (activity.config.useSwipeToAction) {
            when (viewType) {
                VIEW_TYPE_GRID -> {
                    if (showPhoneNumbers) R.layout.item_contact_with_number_grid_swipe else R.layout.item_contact_without_number_grid_swipe
                }

                else -> {
                    if (showPhoneNumbers) R.layout.item_contact_with_number_swipe else R.layout.item_contact_without_number_swipe
                }
            }
        } else {
            when (viewType) {
                VIEW_TYPE_GRID -> {
                    if (showPhoneNumbers) com.goodwy.commons.R.layout.item_contact_with_number_grid else com.goodwy.commons.R.layout.item_contact_without_number_grid
                }

                else -> {
                    if (showPhoneNumbers) com.goodwy.commons.R.layout.item_contact_with_number else com.goodwy.commons.R.layout.item_contact_without_number
                }
            }
        }

        return createViewHolder(layout, parent)
    }

    override fun getItemViewType(position: Int): Int {
        return viewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contactItems[position]
        val allowLongClick = location != LOCATION_INSERT_OR_EDIT
        holder.bindView(contact, true, allowLongClick) { itemView, layoutPosition ->
            setupView(itemView, contact, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contactItems.size

    private fun getItemWithKey(key: Int): Contact? = contactItems.firstOrNull { it.id == key }

    fun updateItems(newItems: List<Contact>, highlightText: String = "") {
        if (newItems.hashCode() != contactItems.hashCode()) {
            contactItems = newItems.toMutableList()
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun editContact() {
        val contact = getItemWithKey(selectedKeys.first()) ?: return
        activity.editContact(contact)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = if (itemsCnt == 1) {
            "\"${getSelectedItems().first().getNameToDisplay()}\""
        } else {
            resources.getQuantityString(com.goodwy.commons.R.plurals.delete_contacts, itemsCnt, itemsCnt)
        }

        val baseString = com.goodwy.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            deleteContacts()
        }
    }

    private fun deleteContacts() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        contactItems.removeAll(contactsToRemove)

        ContactsHelper(activity).getContacts(true) { allContacts ->
            ensureBackgroundThread {
                ContactsHelper(activity).deleteContacts(contactsToRemove
                    .flatMap { contactToRemove -> allContacts.filter {
                        (config.mergeDuplicateContacts || it.id == contactToRemove.id) && (it.getHashToCompare() == contactToRemove.getHashToCompare())
                    } }
                    .toMutableList() as ArrayList<Contact>)

                activity.runOnUiThread {
                    if (contactItems.isEmpty()) {
                        refreshListener?.refreshContacts(ALL_TABS_MASK)
                        finishActMode()
                    } else {
                        removeSelectedItems(positions)
                        refreshListener?.refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
                    }
                }
            }
        }
    }

    // used for removing contacts from groups or favorites, not deleting actual contacts
    private fun removeContacts() {
        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        contactItems.removeAll(contactsToRemove)

        if (location == LOCATION_FAVORITES_TAB) {
            ContactsHelper(activity).removeFavorites(contactsToRemove)
            if (contactItems.isEmpty()) {
                refreshListener?.refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
                finishActMode()
            } else {
                removeSelectedItems(positions)
                refreshListener?.refreshContacts(TAB_CONTACTS)
            }
        } else if (location == LOCATION_GROUP_CONTACTS) {
            removeListener?.removeFromGroup(contactsToRemove)
            removeSelectedItems(positions)
        }
    }

    private fun addToFavorites() {
        ContactsHelper(activity).addFavorites(getSelectedItems())
        refreshListener?.refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        finishActMode()
    }

    private fun addToGroup() {
        val items = ArrayList<RadioItem>()
        ContactsHelper(activity).getStoredGroups {
            it.forEach {
                items.add(RadioItem(it.id!!.toInt(), it.title))
            }
            items.add(RadioItem(NEW_GROUP_ID, activity.getString(R.string.create_new_group)))
            showGroupsPicker(items)
        }
    }

    private fun showGroupsPicker(radioItems: ArrayList<RadioItem>) {
        val selectedContacts = getSelectedItems()
        RadioGroupDialog(activity, radioItems, 0) {
            if (it as Int == NEW_GROUP_ID) {
                CreateNewGroupDialog(activity) {
                    ensureBackgroundThread {
                        activity.addContactsToGroup(selectedContacts, it.id!!.toLong())
                        refreshListener?.refreshContacts(TAB_GROUPS)
                    }
                    finishActMode()
                }
            } else {
                ensureBackgroundThread {
                    activity.addContactsToGroup(selectedContacts, it.toLong())
                    refreshListener?.refreshContacts(TAB_GROUPS)
                }
                finishActMode()
            }
        }
    }

    private fun shareContacts() {
        activity.shareContacts(getSelectedItems())
    }

    private fun callContact() {
        val contact = getItemWithKey(selectedKeys.first()) ?: return
        if (contact.phoneNumbers.isEmpty()) {
            activity.toast(com.goodwy.commons.R.string.no_phone_number_found)
            return
        }
        if (activity.config.showCallConfirmation) {
            CallConfirmationDialog(activity as SimpleActivity, contact.getNameToDisplay()) {
                activity.apply {
                    initiateCall(contact) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
                }
            }
        } else {
            activity.apply {
                initiateCall(contact) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
            }
        }
    }

    private fun sendSMSToContacts() {
        val contacts = getSelectedItems()
        if (!contacts.any { it.phoneNumbers.isNotEmpty() }) {
            activity.toast(com.goodwy.commons.R.string.no_phone_number_found)
            return
        }
        activity.sendSMSToContacts(contacts)
    }

    private fun sendEmailToContacts() {
        val contacts = getSelectedItems()
        if (!contacts.any { it.emails.isNotEmpty() }) {
            activity.toast(com.goodwy.commons.R.string.no_items_found)
            return
        }
        activity.sendEmailToContacts(contacts)
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val contact = getSelectedItems().first()
            val drawable = resources.getDrawable(R.drawable.shortcut_contact).mutate()
            getShortcutImage(contact, drawable) {
                val intent = Intent(activity, ViewContactActivity::class.java)
                intent.action = Intent.ACTION_VIEW
                intent.putExtra(CONTACT_ID, contact.id)
                intent.putExtra(IS_PRIVATE, contact.isPrivate())
                intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY

                val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                    .setShortLabel(contact.getNameToDisplay())
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun getShortcutImage(contact: Contact, drawable: Drawable, callback: () -> Unit) {
        val fullName = contact.getNameToDisplay()
        val letterBackgroundColors = activity.getLetterBackgroundColors()
        val color = letterBackgroundColors[abs(fullName.hashCode()) % letterBackgroundColors.size].toInt()
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_contact_background).applyColorFilter(color)
        val placeholderImage =
            if (contact.isABusinessContact()) {
                val drawablePlaceholder = ResourcesCompat.getDrawable(resources, R.drawable.placeholder_company, activity.theme)
                if (baseConfig.useColoredContacts) {
                    (drawablePlaceholder as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                }
                drawablePlaceholder
            } else {
                SimpleContactsHelper(activity).getContactLetterIcon(fullName).toDrawable(resources)
            }
        if (contact.photoUri.isEmpty() && contact.photo == null) {
            drawable.setDrawableByLayerId(R.id.shortcut_contact_image, placeholderImage)
            callback()
        } else {
            ensureBackgroundThread {
                val options = RequestOptions()
                    .signature(ObjectKey(contact.getSignatureKey()))
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .error(placeholderImage)

                val size = activity.resources.getDimension(com.goodwy.commons.R.dimen.shortcut_size).toInt()
                val itemToLoad: Any? = if (contact.photoUri.isNotEmpty()) {
                    contact.photoUri
                } else {
                    contact.photo
                }

                val builder = Glide.with(activity)
                    .asDrawable()
                    .load(itemToLoad)
                    .apply(options)
                    .apply(RequestOptions.circleCropTransform())
                    .into(size, size)

                try {
                    val bitmap = builder.get()
                    drawable.setDrawableByLayerId(R.id.shortcut_contact_image, bitmap)
                } catch (e: Exception) {
                }

                activity.runOnUiThread {
                    callback()
                }
            }
        }
    }

    private fun getSelectedItems() = contactItems.filter { selectedKeys.contains(it.id) } as ArrayList<Contact>

    private fun getLastItem() = contactItems.last()

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.findViewById<ImageView>(com.goodwy.commons.R.id.item_contact_image))
        }
    }

    private fun setupView(view: View, contact: Contact, holder: ViewHolder) {
        view.apply {
            findViewById<ImageView>(R.id.divider)?.setBackgroundColor(textColor)
            if (getLastItem() == contact || !context.config.useDividers) findViewById<ImageView>(R.id.divider)?.visibility = View.INVISIBLE else findViewById<ImageView>(R.id.divider)?.visibility = View.VISIBLE

            setupViewBackground(activity)
            findViewById<FrameLayout>(R.id.item_contact_frame)?.isSelected = selectedKeys.contains(contact.id)
            val fullName = contact.getNameToDisplay()
            findViewById<TextView>(com.goodwy.commons.R.id.item_contact_name).text = if (textToHighlight.isEmpty()) fullName else {
                if (fullName.contains(textToHighlight, true)) {
                    fullName.highlightTextPart(textToHighlight, properPrimaryColor)
                } else {
                    fullName.highlightTextFromNumbers(textToHighlight, properPrimaryColor)
                }
            }

            findViewById<TextView>(com.goodwy.commons.R.id.item_contact_name).apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }

            if (findViewById<TextView>(com.goodwy.commons.R.id.item_contact_number) != null) {
                val phoneNumberToUse = if (textToHighlight.isEmpty()) {
                    contact.phoneNumbers.firstOrNull { it.isPrimary } ?: contact.phoneNumbers.firstOrNull()
                } else {
                    contact.phoneNumbers.firstOrNull { it.value.contains(textToHighlight) } ?: contact.phoneNumbers.firstOrNull()
                }

                val phoneNumberToFormat = phoneNumberToUse?.value ?: ""
                val numberText = if (config.formatPhoneNumbers) {
                    phoneNumberToFormat.formatPhoneNumber()
                } else {
                    phoneNumberToUse?.value ?: ""
                }

                findViewById<TextView>(com.goodwy.commons.R.id.item_contact_number).apply {
                    text = if (textToHighlight.isEmpty()) numberText else numberText.highlightTextPart(textToHighlight, properPrimaryColor, false, true)
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeSmall)
                }
            }

            findViewById<ImageView>(com.goodwy.commons.R.id.item_contact_image).beVisibleIf(showContactThumbnails)

            if (showContactThumbnails) {
                val placeholderImage = SimpleContactsHelper(context).getContactLetterIcon(fullName).toDrawable(resources)
                if (contact.photoUri.isEmpty() && contact.photo == null) {
                    if (contact.isABusinessContact()) {
                        val drawable = ResourcesCompat.getDrawable(resources, R.drawable.placeholder_company, activity.theme)
                        if (baseConfig.useColoredContacts) {
                            val letterBackgroundColors = activity.getLetterBackgroundColors()
                            val color = letterBackgroundColors[abs(fullName.hashCode()) % letterBackgroundColors.size].toInt()
                            (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                        }
                        findViewById<ImageView>(com.goodwy.commons.R.id.item_contact_image).setImageDrawable(drawable)
                    } else {
                        findViewById<ImageView>(com.goodwy.commons.R.id.item_contact_image).setImageDrawable(placeholderImage)
                    }
                } else {
                    val options = RequestOptions()
                        .signature(ObjectKey(contact.getSignatureKey()))
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .error(placeholderImage)
                        .centerCrop()

                    val itemToLoad: Any? = if (contact.photoUri.isNotEmpty()) {
                        contact.photoUri
                    } else {
                        contact.photo
                    }

                    Glide.with(activity)
                        .load(itemToLoad)
                        .apply(options)
                        .apply(RequestOptions.circleCropTransform())
                        .into(findViewById(com.goodwy.commons.R.id.item_contact_image))
                }

                if (viewType != VIEW_TYPE_GRID) {
                    val size = (context.pixels(com.goodwy.commons.R.dimen.normal_icon_size) * contactThumbnailsSize).toInt()
                    findViewById<ImageView>(com.goodwy.commons.R.id.item_contact_image).setHeightAndWidth(size)
                }
            }

            val dragIcon = findViewById<ImageView>(com.goodwy.commons.R.id.drag_handle_icon)
            @SuppressLint("ClickableViewAccessibility")
            if (enableDrag && textToHighlight.isEmpty()) {
                dragIcon.apply {
                    beVisibleIf(selectedKeys.isNotEmpty())
                    applyColorFilter(textColor)
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            startReorderDragListener?.requestDrag(holder)
                        }
                        false
                    }
                }
            } else {
                dragIcon.apply {
                    beGone()
                    setOnTouchListener(null)
                }
            }

            //swipe
            if (activity.config.useSwipeToAction && findViewById<SwipeActionView>(R.id.itemContactSwipe) != null) {
                findViewById<ConstraintLayout>(R.id.itemContactFrameSelect).setupViewBackground(activity)
                findViewById<FrameLayout>(R.id.item_contact_frame).setBackgroundColor(backgroundColor)

                val isRTL = activity.isRTLLayout
                val swipeLeftAction = if (isRTL) activity.config.swipeRightAction else activity.config.swipeLeftAction
                findViewById<ImageView>(R.id.swipeLeftIcon).apply {
                    setImageResource(swipeActionImageResource(swipeLeftAction))
                    setColorFilter(properPrimaryColor.getContrastColor())
                }
                findViewById<RelativeLayout>(R.id.swipeLeftIconHolder).setBackgroundColor(swipeActionColor(swipeLeftAction))

                val swipeRightAction = if (isRTL) activity.config.swipeLeftAction else activity.config.swipeRightAction
                findViewById<ImageView>(R.id.swipeRightIcon).apply {
                    setImageResource(swipeActionImageResource(swipeRightAction))
                    setColorFilter(properPrimaryColor.getContrastColor())
                }
                findViewById<RelativeLayout>(R.id.swipeRightIconHolder).setBackgroundColor(swipeActionColor(swipeRightAction))

                findViewById<SwipeActionView>(R.id.itemContactSwipe).apply {
                    if (activity.config.swipeRipple) {
                        setRippleColor(SwipeDirection.Left, swipeActionColor(swipeLeftAction))
                        setRippleColor(SwipeDirection.Right, swipeActionColor(swipeRightAction))
                    }
                    useHapticFeedback = activity.config.swipeVibration
                    swipeGestureListener = object : SwipeGestureListener {
                        override fun onSwipedLeft(swipeActionView: SwipeActionView): Boolean {
                            finishActMode()
                            val swipeLeftOrRightAction =
                                if (activity.isRTLLayout) activity.config.swipeRightAction else activity.config.swipeLeftAction
                            swipeAction(swipeLeftOrRightAction, contact)
                            slideLeftReturn(findViewById<ImageView>(R.id.swipeLeftIcon), findViewById<RelativeLayout>(R.id.swipeLeftIconHolder))
                            return true
                        }

                        override fun onSwipedRight(swipeActionView: SwipeActionView): Boolean {
                            finishActMode()
                            val swipeRightOrLeftAction =
                                if (activity.isRTLLayout) activity.config.swipeLeftAction else activity.config.swipeRightAction
                            swipeAction(swipeRightOrLeftAction, contact)
                            slideRightReturn(findViewById<ImageView>(R.id.swipeRightIcon), findViewById<RelativeLayout>(R.id.swipeRightIconHolder))
                            return true
                        }

                        override fun onSwipedActivated(swipedRight: Boolean) {
                            if (viewType != VIEW_TYPE_GRID) {
                                if (swipedRight) slideRight(findViewById<ImageView>(R.id.swipeRightIcon), findViewById<RelativeLayout>(R.id.swipeRightIconHolder))
                                else slideLeft(findViewById<ImageView>(R.id.swipeLeftIcon))
                            }
                        }

                        override fun onSwipedDeactivated(swipedRight: Boolean) {
                            if (viewType != VIEW_TYPE_GRID) {
                                if (swipedRight) slideRightReturn(findViewById<ImageView>(R.id.swipeRightIcon), findViewById<RelativeLayout>(R.id.swipeRightIconHolder))
                                else slideLeftReturn(findViewById<ImageView>(R.id.swipeLeftIcon), findViewById<RelativeLayout>(R.id.swipeLeftIconHolder))
                            }
                        }
                    }
                }

                val contactsGridColumnCount = activity.config.contactsGridColumnCount
                if (viewType == VIEW_TYPE_GRID && contactsGridColumnCount > 1) {
                    val width =
                        (Resources.getSystem().displayMetrics.widthPixels / contactsGridColumnCount / 2.5).toInt()
                    findViewById<RelativeLayout>(R.id.swipeLeftIconHolder).setWidth(width)
                    findViewById<RelativeLayout>(R.id.swipeRightIconHolder).setWidth(width)
                }
            }
        }
    }

    private fun slideRight(view: View, parent: View) {
        view.animate()
            .x(parent.right - activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin) - view.width)
    }

    private fun slideLeft(view: View) {
        view.animate()
            .x(activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin))
    }

    private fun slideRightReturn(view: View, parent: View) {
        view.animate()
            .x(parent.left + activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin))
    }

    private fun slideLeftReturn(view: View, parent: View) {
        view.animate()
            .x(parent.width - activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin) - view.width)
    }

    override fun onChange(position: Int) = contactItems.getOrNull(position)?.getBubbleText() ?: ""

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        activity.config.isCustomOrderSelected = true

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(contactItems, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(contactItems, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        onDragEndListener?.invoke()
    }

    private fun swipeActionImageResource(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> com.goodwy.commons.R.drawable.ic_delete_outline
            SWIPE_ACTION_MESSAGE -> R.drawable.ic_messages
            SWIPE_ACTION_EDIT -> com.goodwy.commons.R.drawable.ic_edit_vector
            else -> com.goodwy.commons.R.drawable.ic_phone_vector
        }
    }

    private fun swipeActionColor(swipeAction: Int): Int {
        val oneSim = activity.config.currentSIMCardIndex == 0
        val simColor = if (oneSim) activity.config.simIconsColors[1] else activity.config.simIconsColors[2]
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> resources.getColor(com.goodwy.commons.R.color.red_missed, activity.theme)
            SWIPE_ACTION_MESSAGE -> resources.getColor(com.goodwy.commons.R.color.ic_messages, activity.theme)
            SWIPE_ACTION_EDIT -> resources.getColor(R.color.swipe_purple, activity.theme)
            else -> simColor
        }
    }

    private fun swipeAction(swipeAction: Int, contact: Contact) {
        when (swipeAction) {
            SWIPE_ACTION_DELETE -> swipedDelete(contact)
            SWIPE_ACTION_MESSAGE -> swipedSMS(contact)
            SWIPE_ACTION_EDIT -> swipedEdit(contact)
            else -> swipedCall(contact)
        }
    }

    private fun swipedDelete(contact: Contact) {
        selectedKeys.add(contact.rawId)
        if (activity.config.skipDeleteConfirmation) deleteContacts() else askConfirmDelete()
    }

    private fun swipedSMS(contact: Contact) {
        if (contact.phoneNumbers.isEmpty()) {
            activity.toast(com.goodwy.commons.R.string.no_phone_number_found)
            return
        }
        val contactList = ArrayList<Contact>()
        contactList.add(contact)
        activity.sendSMSToContacts(contactList)
    }

    private fun swipedEdit(contact: Contact) {
        activity.editContact(contact)
    }

    private fun swipedCall(contact: Contact) {
        if (contact.phoneNumbers.isEmpty()) {
            activity.toast(com.goodwy.commons.R.string.no_phone_number_found)
            return
        }
        if (activity.config.showCallConfirmation) {
            CallConfirmationDialog(activity as SimpleActivity, contact.getNameToDisplay()) {
                activity.apply {
                    initiateCall(contact) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
                }
            }
        } else {
            activity.apply {
                initiateCall(contact) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
            }
        }
    }
}
