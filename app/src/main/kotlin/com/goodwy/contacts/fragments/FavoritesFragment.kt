package com.goodwy.contacts.fragments

import android.content.Context
import android.util.AttributeSet
import com.goodwy.commons.helpers.TAB_FAVORITES
import com.goodwy.contacts.activities.MainActivity
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.dialogs.SelectContactsDialog
import com.goodwy.contacts.helpers.ContactsHelper

class FavoritesFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun fabClicked() {
        finishActMode()
        showAddFavoritesDialog()
    }

    override fun placeholderClicked() {
        showAddFavoritesDialog()
    }

    private fun showAddFavoritesDialog() {
        SelectContactsDialog(activity!!, allContacts, true, false) { addedContacts, removedContacts ->
            ContactsHelper(activity as SimpleActivity).apply {
                addFavorites(addedContacts)
                removeFavorites(removedContacts)
            }

            (activity as? MainActivity)?.refreshContacts(TAB_FAVORITES)
        }
    }
}
