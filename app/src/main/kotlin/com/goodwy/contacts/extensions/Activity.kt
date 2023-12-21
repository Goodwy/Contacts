package com.goodwy.contacts.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.content.res.AppCompatResources
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.contacts.BuildConfig
import com.goodwy.contacts.R
import com.goodwy.contacts.activities.EditContactActivity
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.activities.ViewContactActivity
import com.goodwy.contacts.dialogs.ImportContactsDialog
import com.goodwy.contacts.helpers.DEFAULT_FILE_NAME
import com.goodwy.contacts.helpers.VcfExporter
import ezvcard.VCardVersion
import java.io.FileOutputStream

fun SimpleActivity.startCallIntent(recipient: String) {
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", recipient, null)
            launchActivityIntent(this)
        }
    }
}

fun SimpleActivity.tryStartCallRecommendation(contact: Contact) {
    val simpleDialer = "com.goodwy.dialer"
    val simpleDialerDebug = "com.goodwy.dialer.debug"
    if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleDialer) && !isPackageInstalled(simpleDialerDebug))) {
        NewAppDialog(this, simpleDialer, getString(com.goodwy.commons.R.string.recommendation_dialog_dialer_g), getString(com.goodwy.commons.R.string.right_dialer),
            AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_dialer)) {
            tryStartCall(contact)
        }
    } else {
        tryStartCall(contact)
    }
}

fun SimpleActivity.tryStartCall(contact: Contact) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, contact.getNameToDisplay()) {
            callContact(contact)
        }
    } else {
        callContact(contact)
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
        toast(com.goodwy.commons.R.string.unknown_error_occurred)
        return
    }

    getFileOutputStream(file.toFileDirItem(this), true) {

        // whatsApp does not support vCard version 4.0 yet
        VcfExporter().exportContacts(this, it, contacts, false, version = VCardVersion.V3_0) {
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
        tryInitiateCall(contact) { startCallIntent(it) }
    } else {
        toast(com.goodwy.commons.R.string.no_phone_number_found)
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

fun SimpleActivity.tryImportContactsFromFile(uri: Uri, callback: (Boolean) -> Unit) {
    when (uri.scheme) {
        "file" -> showImportContactsDialog(uri.path!!, callback)
        "content" -> {
            val tempFile = getTempFile()
            if (tempFile == null) {
                toast(com.goodwy.commons.R.string.unknown_error_occurred)
                return
            }

            try {
                val inputStream = contentResolver.openInputStream(uri)
                val out = FileOutputStream(tempFile)
                inputStream!!.copyTo(out)
                showImportContactsDialog(tempFile.absolutePath, callback)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

        else -> toast(com.goodwy.commons.R.string.invalid_file_format)
    }
}

fun SimpleActivity.showImportContactsDialog(path: String, callback: (Boolean) -> Unit) {
    ImportContactsDialog(this, path, callback)
}
