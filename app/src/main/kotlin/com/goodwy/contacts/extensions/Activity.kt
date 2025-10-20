package com.goodwy.contacts.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK
import android.provider.ContactsContract.CommonDataKinds.Im.PROTOCOL_QQ
import android.provider.ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE
import androidx.appcompat.content.res.AppCompatResources
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
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
import androidx.core.net.toUri

fun SimpleActivity.startCallIntent(recipient: String) {
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", recipient, null)
            putExtra(IS_RIGHT_APP, BuildConfig.RIGHT_APP_KEY)
            launchActivityIntent(this)
        }
    }
}

fun SimpleActivity.tryStartCallRecommendation(contact: Contact) {
    val simpleDialer = "com.goodwy.dialer"
    val simpleDialerDebug = "com.goodwy.dialer.debug"
    if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleDialer) && !isPackageInstalled(simpleDialerDebug))) {
        NewAppDialog(this, simpleDialer, getString(com.goodwy.strings.R.string.recommendation_dialog_dialer_g), getString(com.goodwy.commons.R.string.right_dialer),
            AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_dialer)) {
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
        VcfExporter().exportContacts(
            context = this,
            outputStream = it,
            contacts = contacts,
            showExportingToast = false
        ) {
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
        ON_CLICK_EDIT_CONTACT -> editContact(contact, config.mergeDuplicateContacts)
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

fun Activity.editContact(contact: Contact, isMergedDuplicate: Boolean) {
    if (!isMergedDuplicate) {
        editContact(contact)
    } else {
        ContactsHelper(this).getContactSources { contactSources ->
            getDuplicateContacts(contact, true) { contacts ->
                if (contacts.size == 1) {
                    runOnUiThread {
                        editContact(contacts.first())
                    }
                } else {
                    val items = ArrayList(contacts.mapIndexed { index, contact ->
                        var source = getPublicContactSourceSync(contact.source, contactSources)
                        if (source == "") {
                            source = getString(R.string.phone_storage)
                        }
                        RadioItem(index, source, contact)
                    }.sortedBy { it.title })

                    runOnUiThread {
                        RadioGroupDialog(
                            activity = this,
                            items = items,
                            titleId = R.string.select_account,
                        ) {
                            editContact(it as Contact)
                        }
                    }
                }
            }
        }
    }
}

fun Activity.getDuplicateContacts(contact: Contact, includeCurrent: Boolean, callback: (duplicateContacts: ArrayList<Contact>) -> Unit) {
    val duplicateContacts = ArrayList<Contact>()
    if (includeCurrent) {
        duplicateContacts.add(contact)
    }
    ContactsHelper(this).getDuplicatesOfContact(contact, false) { contacts ->
        ensureBackgroundThread {
            val displayContactSources = getVisibleContactSources()
            contacts.filter { displayContactSources.contains(it.source) }.forEach {
                val duplicate = ContactsHelper(this).getContactWithId(it.id, it.isPrivate())
                if (duplicate != null) {
                    duplicateContacts.add(duplicate)
                }
            }
            callback(duplicateContacts)
        }
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
            try {
                val tempFile = copyUriToTempFile(uri, "import-${System.currentTimeMillis()}-$DEFAULT_FILE_NAME")
                if (tempFile == null) {
                    toast(com.goodwy.commons.R.string.unknown_error_occurred)
                    return
                }

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

fun SimpleActivity.launchAbout() {
    val licenses = LICENSE_JODA or LICENSE_GLIDE or LICENSE_GSON or LICENSE_INDICATOR_FAST_SCROLL or LICENSE_AUTOFITTEXTVIEW

    val faqItems = arrayListOf(
        FAQItem(R.string.faq_1_title, R.string.faq_1_text),
        FAQItem(com.goodwy.commons.R.string.faq_9_title_commons, com.goodwy.commons.R.string.faq_9_text_commons),
        FAQItem(com.goodwy.strings.R.string.faq_100_title_commons_g, com.goodwy.strings.R.string.faq_100_text_commons_g),
        FAQItem(com.goodwy.strings.R.string.faq_101_title_commons_g, com.goodwy.strings.R.string.faq_101_text_commons_g, R.string.phone_storage_hidden),
        FAQItem(com.goodwy.commons.R.string.faq_2_title_commons, com.goodwy.strings.R.string.faq_2_text_commons_g),
    )

    val productIdX1 = BuildConfig.PRODUCT_ID_X1
    val productIdX2 = BuildConfig.PRODUCT_ID_X2
    val productIdX3 = BuildConfig.PRODUCT_ID_X3
    val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    startAboutActivity(
        appNameId = R.string.app_name_g,
        licenseMask = licenses,
        versionName = BuildConfig.VERSION_NAME,
        faqItems = faqItems,
        showFAQBeforeMail = true,
        productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
        productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
        subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        playStoreInstalled = isPlayStoreInstalled(),
        ruStoreInstalled = isRuStoreInstalled()
    )
}

fun Activity.openMessengerProfile(username: String, messengerType: Int) {
    val cleanUsername = username.removePrefix("@")

    val intent = when (messengerType) {
        PROTOCOL_QQ -> {
            // QQ - by QQ number
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://qm.qq.com/cgi-bin/qm/qr?k=$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_TEAMS, PROTOCOL_SKYPE -> {
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "msteams://teams.microsoft.com/l/chat/0/0?users=$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://teams.microsoft.com/l/chat/0/0?users=$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_WECOM -> {
            // WeCom (WeChat Work) - by userid
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "wxwork://contacts?userId=$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://work.weixin.qq.com/wework_admin/contacts?userId=$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_GOOGLE_CHAT, PROTOCOL_GOOGLE_TALK -> {
            // Google Chat - by email
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "com.google.android.apps.dynamite://chat/$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://mail.google.com/chat/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_MATRIX -> {
            // Matrix - by Matrix ID (@user:homeserver.com)
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "element://open?userId=$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://matrix.to/#/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_DISCORD -> {
            // Discord uses tag (username#discriminator)
            Intent(Intent.ACTION_VIEW).apply {
                data = "https://discord.com/users/$cleanUsername".toUri()
            }
        }

        PROTOCOL_WECHAT -> {
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "weixin://dl/chat?$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://weixin.qq.com/r/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_LINE -> {
            // LINE - by LINE ID
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "line://ti/p/$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://line.me/R/ti/p/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_TELEGRAM -> {
            // Let's try the app, then the web version
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "tg://resolve?domain=$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://t.me/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_TELEGRAM_CHANNEL -> {
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "tg://resolve?domain=$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://t.me/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_WHATSAPP -> {
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://wa.me/$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://web.whatsapp.com/send?phone=$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_INSTAGRAM -> {
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "instagram://user?username=$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://instagram.com/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_FACEBOOK -> {
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "fb://profile/$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://facebook.com/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_VIBER -> {
            Intent(Intent.ACTION_VIEW).apply {
                data = "viber://add?number=$cleanUsername".toUri()
            }
        }

        PROTOCOL_SIGNAL -> {
            // Signal doesn't support direct links by username
            // You can open the application for manual input
            Intent(Intent.ACTION_VIEW).apply {
                setPackage("org.thoughtcrime.securesms")
            }
        }

        PROTOCOL_TWITTER -> {
            // X (formerly Twitter)
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "twitter://user?screen_name=$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://x.com/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_LINKEDIN -> {
            // LinkedIn typically uses the full URL or ID
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "linkedin://profile/$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://linkedin.com/in/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        PROTOCOL_THREEMA -> {
            // Threema - by Threema ID (8 characters)
            val appIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "threema://compose?id=$cleanUsername".toUri()
            }
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://threema.id/$cleanUsername".toUri()
            }
            createFallbackIntent(appIntent, webIntent)
        }

        else -> {
            toast(com.goodwy.strings.R.string.failed_open_link)
            return
        }
    }

    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        toast(com.goodwy.strings.R.string.failed_open_link)
    }
}

private fun Activity.createFallbackIntent(appIntent: Intent, webIntent: Intent): Intent {
    return try {
        // Checking if the application is installed
        packageManager.getPackageInfo(appIntent.`package` ?: "", 0)
        appIntent
    } catch (_: PackageManager.NameNotFoundException) {
        webIntent
    }
}
