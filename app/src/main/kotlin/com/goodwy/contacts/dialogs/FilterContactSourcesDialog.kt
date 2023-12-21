package com.goodwy.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.getVisibleContactSources
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.SMT_PRIVATE
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.ContactSource
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.adapters.FilterContactSourcesAdapter
import com.goodwy.contacts.databinding.DialogFilterContactSourcesBinding
import com.goodwy.contacts.extensions.config

class FilterContactSourcesDialog(val activity: SimpleActivity, private val callback: () -> Unit) {
    private var dialog: AlertDialog? = null
    private val binding = DialogFilterContactSourcesBinding.inflate(activity.layoutInflater)
    private var contactSources = ArrayList<ContactSource>()
    private var contacts = ArrayList<Contact>()
    private var isContactSourcesReady = false
    private var isContactsReady = false

    init {
        ContactsHelper(activity).getContactSources { contactSources ->
            contactSources.mapTo(this@FilterContactSourcesDialog.contactSources) { it.copy() }
            isContactSourcesReady = true
            processDataIfReady()
        }

        ContactsHelper(activity).getContacts(getAll = true) { contacts ->
            contacts.mapTo(this@FilterContactSourcesDialog.contacts) { it.copy() }
            isContactsReady = true
            processDataIfReady()
        }
    }

    private fun processDataIfReady() {
        if (!isContactSourcesReady) {
            return
        }

        val contactSourcesWithCount = ArrayList<ContactSource>()
        for (contactSource in contactSources) {
            val count = if (isContactsReady) {
                contacts.filter { it.source == contactSource.name }.count()
            } else {
                -1
            }
            contactSourcesWithCount.add(contactSource.copy(count = count))
        }

        contactSources.clear()
        contactSources.addAll(contactSourcesWithCount)

        activity.runOnUiThread {
            val selectedSources = activity.getVisibleContactSources()
            binding.filterContactSourcesList.adapter = FilterContactSourcesAdapter(activity, contactSourcesWithCount, selectedSources)

            if (dialog == null) {
                activity.getAlertDialogBuilder()
                    .setPositiveButton(com.goodwy.commons.R.string.ok) { dialogInterface, i -> confirmContactSources() }
                    .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
                    .apply {
                        activity.setupDialogStuff(binding.root, this) { alertDialog ->
                        dialog = alertDialog
                        }
                    }
            }
        }
    }

    private fun confirmContactSources() {
        val selectedContactSources = (binding.filterContactSourcesList.adapter as FilterContactSourcesAdapter).getSelectedContactSources()
        val ignoredContactSources = contactSources.filter { !selectedContactSources.contains(it) }.map {
            if (it.type == SMT_PRIVATE) SMT_PRIVATE else it.getFullIdentifier()
        }.toHashSet()

        if (activity.getVisibleContactSources() != ignoredContactSources) {
            activity.config.ignoredContactSources = ignoredContactSources
            callback()
        }
        dialog?.dismiss()
    }
}
