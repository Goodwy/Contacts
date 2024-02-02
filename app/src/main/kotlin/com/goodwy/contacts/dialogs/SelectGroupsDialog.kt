package com.goodwy.contacts.dialogs

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.models.contacts.Group
import com.goodwy.commons.views.MyAppCompatCheckbox
import com.goodwy.contacts.R
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.databinding.DialogSelectGroupsBinding
import com.goodwy.contacts.databinding.ItemCheckboxBinding
import com.goodwy.contacts.databinding.ItemTextviewBinding

class SelectGroupsDialog(val activity: SimpleActivity, val selectedGroups: ArrayList<Group>, val callback: (newGroups: ArrayList<Group>) -> Unit) {
    private val binding = DialogSelectGroupsBinding.inflate(activity.layoutInflater)
    private val checkboxes = ArrayList<MyAppCompatCheckbox>()
    private var groups = ArrayList<Group>()
    private var dialog: AlertDialog? = null

    init {
        ContactsHelper(activity).getStoredGroups {
            groups = it
            activity.runOnUiThread {
                initDialog()
            }
        }
    }

    private fun initDialog() {
        groups.sortedBy { it.title }.forEach {
            addGroupCheckbox(it)
        }

        if (groups.isEmpty()) addNoGroupText()
        addCreateNewGroupButton()

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, com.goodwy.commons.R.string.add_group) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun addGroupCheckbox(group: Group) {
        ItemCheckboxBinding.inflate(activity.layoutInflater, null, false).apply {
            checkboxes.add(itemCheckbox)
            itemCheckboxHolder.setOnClickListener {
                itemCheckbox.toggle()
            }

            itemCheckbox.apply {
                isChecked = selectedGroups.contains(group)
                text = group.title
                tag = group.id
                setColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), activity.getProperBackgroundColor())
            }
            binding.dialogGroupsHolder.addView(this.root)
        }
    }

    private fun addNoGroupText() {
        val newGroup = Group(0, activity.getString(com.goodwy.commons.R.string.no_items_found))
        ItemTextviewBinding.inflate(activity.layoutInflater, null, false).itemTextview.apply {
            text = newGroup.title
            setTypeface(null, Typeface.ITALIC)
            tag = newGroup.id
            setTextColor(activity.getProperTextColor())
            binding.dialogGroupsHolder.addView(this)
        }
    }

    private fun addCreateNewGroupButton() {
        val newGroup = Group(0, activity.getString(R.string.create_new_group))
        ItemTextviewBinding.inflate(activity.layoutInflater, null, false).itemTextview.apply {
            text = newGroup.title
            tag = newGroup.id
            setTextColor(activity.getProperPrimaryColor())
            binding.dialogGroupsHolder.addView(this)
            setOnClickListener {
                CreateNewGroupDialog(activity) {
                    selectedGroups.add(it)
                    groups.add(it)
                    binding.dialogGroupsHolder.removeViewAt(binding.dialogGroupsHolder.childCount - 1)
                    addGroupCheckbox(it)
                    addCreateNewGroupButton()
                }
            }
        }
    }

    private fun dialogConfirmed() {
        val selectedGroups = ArrayList<Group>()
        checkboxes.filter { it.isChecked }.forEach {
            val groupId = it.tag as Long
            groups.firstOrNull { it.id == groupId }?.apply {
                selectedGroups.add(this)
            }
        }

        callback(selectedGroups)
    }
}
