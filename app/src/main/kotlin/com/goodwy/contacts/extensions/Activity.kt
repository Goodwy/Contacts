package com.goodwy.contacts.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.CONTACT_ID
import com.goodwy.commons.helpers.IS_PRIVATE
import com.goodwy.commons.helpers.PERMISSION_CALL_PHONE
import com.goodwy.commons.models.RadioItem
import com.goodwy.contacts.BuildConfig
import com.goodwy.contacts.R
import com.goodwy.contacts.activities.EditContactActivity
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.activities.ViewContactActivity
import com.goodwy.contacts.helpers.*
import com.goodwy.contacts.models.Contact

fun SimpleActivity.startCallIntent(recipient: String) {
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", recipient, null)
            launchActivityIntent(this)
        }
    }
}

fun SimpleActivity.tryStartCall(contact: Contact) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, contact.getNameToDisplay()) {
            startCall(contact)
        }
    } else {
        startCall(contact)
    }
}

fun SimpleActivity.startCall(contact: Contact) {
    val numbers = contact.phoneNumbers
    if (numbers.size == 1) {
        startCallIntent(numbers.first().value)
    } else if (numbers.size > 1) {
        val primaryNumber = contact.phoneNumbers.find { it.isPrimary }
        if (primaryNumber != null) {
            startCallIntent(primaryNumber.value)
        } else {
            val items = ArrayList<RadioItem>()
            numbers.forEachIndexed { index, phoneNumber ->
                items.add(RadioItem(index, "${phoneNumber.value} (${getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)})", phoneNumber.value))
            }

            RadioGroupDialog(this, items) {
                startCallIntent(it as String)
            }
        }
    }
}

fun SimpleActivity.showContactSourcePicker(currentSource: String, callback: (newSource: String) -> Unit) {
    ContactsHelper(this).getSaveableContactSources { sources ->
        val items = ArrayList<RadioItem>()
        var sourceNames = sources.map { it.name }
        var currentSourceIndex = sourceNames.indexOfFirst { it == currentSource }
        sourceNames = sources.map { it.publicName }

        sourceNames.forEachIndexed { index, account ->
            items.add(RadioItem(index, account))
            if (currentSource == SMT_PRIVATE && account == getString(R.string.phone_storage_hidden)) {
                currentSourceIndex = index
            }
        }

        runOnUiThread {
            RadioGroupDialog(this, items, currentSourceIndex) {
                callback(sources[it as Int].name)
            }
        }
    }
}

fun BaseSimpleActivity.shareContacts(contacts: ArrayList<Contact>) {
    val filename = if (contacts.size == 1) {
        "${contacts.first().getNameToDisplay()}.vcf"
    } else {
        DEFAULT_FILE_NAME
    }

    val file = getTempFile(filename)
    if (file == null) {
        toast(R.string.unknown_error_occurred)
        return
    }

    getFileOutputStream(file.toFileDirItem(this), true) {
        VcfExporter().exportContacts(this, it, contacts, false) {
            if (it == VcfExporter.ExportResult.EXPORT_OK) {
                sharePathIntent(file.absolutePath, BuildConfig.APPLICATION_ID)
            } else {
                showErrorToast("$it")
            }
        }
    }
}

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> callContact(contact)
        ON_CLICK_VIEW_CONTACT -> viewContact(contact)
        ON_CLICK_EDIT_CONTACT -> editContact(contact)
    }
}

fun SimpleActivity.callContact(contact: Contact) {
    hideKeyboard()
    if (contact.phoneNumbers.isNotEmpty()) {
        tryStartCall(contact)
    } else {
        toast(R.string.no_phone_number_found)
    }
}

fun Activity.viewContact(contact: Contact) {
    hideKeyboard()
    Intent(applicationContext, ViewContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        startActivity(this)
    }
}

fun Activity.editContact(contact: Contact) {
    hideKeyboard()
    Intent(applicationContext, EditContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        startActivity(this)
    }
}
