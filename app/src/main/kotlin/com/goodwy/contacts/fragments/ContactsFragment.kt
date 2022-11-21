package com.goodwy.contacts.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.goodwy.commons.extensions.hideKeyboard
import com.goodwy.contacts.activities.EditContactActivity
import com.goodwy.contacts.activities.InsertOrEditContactActivity
import com.goodwy.contacts.activities.MainActivity

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun fabClicked() {
        activity?.hideKeyboard()
        Intent(context, EditContactActivity::class.java).apply {
            context.startActivity(this)
        }
    }

    override fun placeholderClicked() {
        if (activity is MainActivity) {
            (activity as MainActivity).showFilterDialog()
        } else if (activity is InsertOrEditContactActivity) {
            (activity as InsertOrEditContactActivity).showFilterDialog()
        }
    }
}
