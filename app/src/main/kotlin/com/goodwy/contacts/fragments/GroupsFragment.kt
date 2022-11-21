package com.goodwy.contacts.fragments

import android.content.Context
import android.util.AttributeSet
import com.goodwy.commons.helpers.TAB_GROUPS
import com.goodwy.contacts.activities.MainActivity
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.dialogs.CreateNewGroupDialog

class GroupsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun fabClicked() {
        finishActMode()
        showNewGroupsDialog()
    }

    override fun placeholderClicked() {
        showNewGroupsDialog()
    }

    private fun showNewGroupsDialog() {
        CreateNewGroupDialog(activity as SimpleActivity) {
            (activity as? MainActivity)?.refreshContacts(TAB_GROUPS)
        }
    }
}
