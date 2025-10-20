package com.goodwy.contacts.helpers

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.core.net.toUri
import com.goodwy.commons.R
import com.goodwy.commons.extensions.getDateTimeFromDateString
import com.goodwy.commons.extensions.showErrorToast
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.helpers.CUSTOM_EVENT_TYPE_DEATH
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.ContactRelation
import com.goodwy.contacts.helpers.VcfExporter.ExportResult.EXPORT_FAIL
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.parameter.RelatedType
import ezvcard.property.*
import ezvcard.util.PartialDate
import java.io.OutputStream
import java.time.LocalDate

class VcfExporter {
    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    private var contactsExported = 0
    private var contactsFailed = 0

    fun exportContacts(
        context: Context,
        outputStream: OutputStream?,
        contacts: ArrayList<Contact>,
        showExportingToast: Boolean,
        version: VCardVersion = VCardVersion.V4_0,
        callback: (result: ExportResult) -> Unit,
    ) {
        try {
            if (outputStream == null) {
                callback(EXPORT_FAIL)
                return
            }

            if (showExportingToast) {
                context.toast(com.goodwy.commons.R.string.exporting)
            }

            val cards = ArrayList<VCard>()
            for (contact in contacts) {
                val card = VCard()

                card.addProperty(RawProperty("X-PRODID", getAppInfo(context)))

                val formattedName = arrayOf(
                    contact.prefix,
                    contact.firstName,
                    contact.middleName,
                    contact.surname,
                    contact.suffix
                )
                    .filter { it.isNotEmpty() }
                    .joinToString(separator = " ")
                card.formattedName = FormattedName(formattedName)

                StructuredName().apply {
                    prefixes.add(contact.prefix)
                    given = contact.firstName
                    additionalNames.add(contact.middleName)
                    family = contact.surname
                    suffixes.add(contact.suffix)
                    card.structuredName = this
                }

                if (contact.nickname.isNotEmpty()) {
                    card.setNickname(contact.nickname)
                }

                contact.phoneNumbers.forEach {
                    val phoneNumber = Telephone(it.value)
                    phoneNumber.parameters.addType(getPhoneNumberTypeLabel(it.type, it.label))
                    if (it.isPrimary) {
                        phoneNumber.parameters.addType(getPreferredType(1))
                    }
                    card.addTelephoneNumber(phoneNumber)
                }

                contact.emails.forEach {
                    val email = Email(it.value)
                    email.parameters.addType(getEmailTypeLabel(it.type, it.label))
                    card.addEmail(email)
                }

                contact.events.forEach { event ->
                    val dateTime = event.value.getDateTimeFromDateString(false)
                    when (event.type) {
                        Event.TYPE_BIRTHDAY -> {
                            if (event.value.startsWith("--")) {
                                val partial = PartialDate.builder()
                                    .month(dateTime.monthOfYear)
                                    .date(dateTime.dayOfMonth)
                                    .build()
                                card.birthdays.add(Birthday(partial))
                            } else {
                                val date = LocalDate.of(dateTime.year, dateTime.monthOfYear, dateTime.dayOfMonth)
                                card.birthdays.add(Birthday(date))
                            }
                        }

                        Event.TYPE_ANNIVERSARY -> {
                            if (event.value.startsWith("--")) {
                                val partial = PartialDate.builder()
                                    .month(dateTime.monthOfYear)
                                    .date(dateTime.dayOfMonth)
                                    .build()
                                card.anniversaries.add(Anniversary(partial))
                            } else {
                                val date = LocalDate.of(dateTime.year, dateTime.monthOfYear, dateTime.dayOfMonth)
                                card.anniversaries.add(Anniversary(date))
                            }
                        }

                        else -> {
                            if (event.label == context.getString(com.goodwy.strings.R.string.death)
                                || event.type == CUSTOM_EVENT_TYPE_DEATH
                            ) {
                                if (event.value.startsWith("--")) {
                                    val partial = PartialDate.builder()
                                        .month(dateTime.monthOfYear)
                                        .date(dateTime.dayOfMonth)
                                        .build()
                                    card.deathdates.add(Deathdate(partial))
                                } else {
                                    val date = LocalDate.of(dateTime.year, dateTime.monthOfYear, dateTime.dayOfMonth)
                                    card.deathdates.add(Deathdate(date))
                                }
                            } else {
                                val eventLabel = event.label.ifBlank { context.getString(R.string.other) }

                                val dateString = if (event.value.startsWith("--")) {
                                    "--${dateTime.monthOfYear.toString().padStart(2, '0')}-${dateTime.dayOfMonth.toString().padStart(2, '0')}"
                                } else {
                                    LocalDate.of(dateTime.year, dateTime.monthOfYear, dateTime.dayOfMonth).toString()
                                }

                                card.addProperty(RawProperty("X-EVENT-DATE", dateString))
                                card.addProperty(RawProperty("X-EVENT-LABEL", eventLabel))
                            }
                        }
                    }
                }

                contact.addresses.forEach {
                    val address = Address()
                    if (
                        listOf(
                            it.country,
                            it.region,
                            it.city,
                            it.postcode,
                            it.pobox,
                            it.street,
                            it.neighborhood
                        )
                            .map { it.isEmpty() }
                            .fold(false) { a, b -> a || b }
                    ) {
                        address.country = it.country
                        address.region = it.region
                        address.locality = it.city
                        address.postalCode = it.postcode
                        address.poBox = it.pobox
                        address.streetAddress = it.street
                        address.extendedAddress = it.neighborhood
                    } else {
                        address.streetAddress = it.value
                    }
                    address.parameters.addType(getAddressTypeLabel(it.type, it.label))
                    card.addAddress(address)
                }

                contact.IMs.forEach {
                    val impp = when (it.type) {
                        Im.PROTOCOL_AIM -> Impp.aim(it.value)
                        Im.PROTOCOL_YAHOO -> Impp.yahoo(it.value)
                        Im.PROTOCOL_MSN -> Impp.msn(it.value)
                        Im.PROTOCOL_ICQ -> Impp.icq(it.value)
                        Im.PROTOCOL_SKYPE -> Impp.skype(it.value)
                        Im.PROTOCOL_GOOGLE_TALK -> Impp(HANGOUTS, it.value)
                        Im.PROTOCOL_QQ -> Impp(QQ, it.value)
                        Im.PROTOCOL_JABBER -> Impp(JABBER, it.value)
                        else -> Impp(it.label, it.value)
                    }

                    card.addImpp(impp)
                }

                if (contact.notes.isNotEmpty()) {
                    card.addNote(contact.notes)
                }

                if (contact.organization.isNotEmpty()) {
                    val organization = Organization()
                    organization.values.add(contact.organization.company)
                    card.organization = organization
                    card.titles.add(Title(contact.organization.jobPosition))
                }

                contact.websites.forEach {
                    card.addUrl(it)
                }

                contact.relations.forEach {
                    val name = it.name.trim()
                    if (name.isNotEmpty()) {
                        val related = Related()
                        related.text = name
                        related.types.add (
                            when (it.type) {
                                // vCard 4.0 relation types are directly mapped to their related type
                                ContactRelation.TYPE_CONTACT -> RelatedType.CONTACT
                                ContactRelation.TYPE_ACQUAINTANCE ->  RelatedType.ACQUAINTANCE
                                ContactRelation.TYPE_FRIEND ->  RelatedType.FRIEND
                                ContactRelation.TYPE_MET ->  RelatedType.MET
                                ContactRelation.TYPE_CO_WORKER ->  RelatedType.CO_WORKER
                                ContactRelation.TYPE_COLLEAGUE ->  RelatedType.COLLEAGUE
                                ContactRelation.TYPE_CO_RESIDENT ->  RelatedType.CO_RESIDENT
                                ContactRelation.TYPE_NEIGHBOR ->  RelatedType.NEIGHBOR
                                ContactRelation.TYPE_CHILD ->  RelatedType.CHILD
                                ContactRelation.TYPE_PARENT ->  RelatedType.PARENT
                                ContactRelation.TYPE_SIBLING ->  RelatedType.SIBLING
                                ContactRelation.TYPE_SPOUSE ->  RelatedType.SPOUSE
                                ContactRelation.TYPE_KIN ->  RelatedType.KIN
                                ContactRelation.TYPE_MUSE ->  RelatedType.MUSE
                                ContactRelation.TYPE_CRUSH ->  RelatedType.CRUSH
                                ContactRelation.TYPE_DATE ->  RelatedType.DATE
                                ContactRelation.TYPE_SWEETHEART ->  RelatedType.SWEETHEART
                                ContactRelation.TYPE_ME ->  RelatedType.ME
                                ContactRelation.TYPE_AGENT ->  RelatedType.AGENT
                                ContactRelation.TYPE_EMERGENCY ->  RelatedType.EMERGENCY

                                // Android relation types are mapped to a suitable substitute (with loss of precision!)
                                ContactRelation.TYPE_ASSISTANT -> RelatedType.COLLEAGUE
                                ContactRelation.TYPE_BROTHER -> RelatedType.SIBLING
                                ContactRelation.TYPE_DOMESTIC_PARTNER -> RelatedType.FRIEND
                                ContactRelation.TYPE_FATHER ->  RelatedType.PARENT
                                ContactRelation.TYPE_MANAGER -> RelatedType.COLLEAGUE
                                ContactRelation.TYPE_MOTHER ->  RelatedType.PARENT
                                ContactRelation.TYPE_PARTNER -> RelatedType.FRIEND
                                ContactRelation.TYPE_REFERRED_BY -> RelatedType.CONTACT
                                ContactRelation.TYPE_RELATIVE -> RelatedType.KIN
                                ContactRelation.TYPE_SISTER -> RelatedType.SIBLING

                                // Custom relation types are mapped to a suitable substitute (with loss of precision!)
                                ContactRelation.TYPE_SUPERIOR -> RelatedType.COLLEAGUE
                                ContactRelation.TYPE_SUBORDINATE -> RelatedType.COLLEAGUE

                                ContactRelation.TYPE_HUSBAND -> RelatedType.SPOUSE
                                ContactRelation.TYPE_WIFE -> RelatedType.SPOUSE
                                ContactRelation.TYPE_SON -> RelatedType.CHILD
                                ContactRelation.TYPE_DAUGHTER -> RelatedType.CHILD
                                ContactRelation.TYPE_GRANDPARENT -> RelatedType.KIN
                                ContactRelation.TYPE_GRANDFATHER -> RelatedType.KIN
                                ContactRelation.TYPE_GRANDMOTHER -> RelatedType.KIN
                                ContactRelation.TYPE_GRANDCHILD -> RelatedType.KIN
                                ContactRelation.TYPE_GRANDSON -> RelatedType.KIN
                                ContactRelation.TYPE_GRANDDAUGHTER -> RelatedType.KIN
                                ContactRelation.TYPE_UNCLE -> RelatedType.KIN
                                ContactRelation.TYPE_AUNT -> RelatedType.KIN
                                ContactRelation.TYPE_NEPHEW -> RelatedType.KIN
                                ContactRelation.TYPE_NIECE -> RelatedType.KIN
                                ContactRelation.TYPE_FATHER_IN_LAW -> RelatedType.KIN
                                ContactRelation.TYPE_MOTHER_IN_LAW -> RelatedType.KIN
                                ContactRelation.TYPE_SON_IN_LAW -> RelatedType.KIN
                                ContactRelation.TYPE_DAUGHTER_IN_LAW -> RelatedType.KIN
                                ContactRelation.TYPE_BROTHER_IN_LAW -> RelatedType.KIN
                                ContactRelation.TYPE_SISTER_IN_LAW -> RelatedType.KIN
                                else -> RelatedType.CONTACT
                            }
                        )
                        card.addRelated(related)
                    }
                }

                try {
                    val inputStream =
                        context.contentResolver.openInputStream(contact.photoUri.toUri())

                    if (inputStream != null) {
                        val photoByteArray = inputStream.readBytes()
                        val photo = Photo(photoByteArray, ImageType.JPEG)
                        card.addPhoto(photo)
                        inputStream.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (contact.groups.isNotEmpty()) {
                    val groupList = Categories()
                    contact.groups.forEach {
                        groupList.values.add(it.title)
                    }

                    card.categories = groupList
                }

                cards.add(card)
                contactsExported++
            }

            Ezvcard.write(cards).version(version).go(outputStream)
        } catch (e: Exception) {
            context.showErrorToast(e)
        }

        callback(
            when {
                contactsExported == 0 -> EXPORT_FAIL
                contactsFailed > 0 -> ExportResult.EXPORT_PARTIAL
                else -> ExportResult.EXPORT_OK
            }
        )
    }

    private fun getPhoneNumberTypeLabel(type: Int, label: String) = when (type) {
        Phone.TYPE_MOBILE -> CELL
        Phone.TYPE_HOME -> HOME
        Phone.TYPE_WORK -> WORK
        Phone.TYPE_MAIN -> MAIN
        Phone.TYPE_FAX_WORK -> WORK_FAX
        Phone.TYPE_FAX_HOME -> HOME_FAX
        Phone.TYPE_PAGER -> PAGER
        Phone.TYPE_OTHER -> OTHER
        else -> label
    }

    private fun getEmailTypeLabel(type: Int, label: String) = when (type) {
        Email.TYPE_HOME -> HOME
        Email.TYPE_WORK -> WORK
        Email.TYPE_MOBILE -> MOBILE
        Email.TYPE_OTHER -> OTHER
        else -> label
    }

    private fun getAddressTypeLabel(type: Int, label: String) = when (type) {
        StructuredPostal.TYPE_HOME -> HOME
        StructuredPostal.TYPE_WORK -> WORK
        StructuredPostal.TYPE_OTHER -> OTHER
        else -> label
    }

    private fun getPreferredType(value: Int) = "$PREF=$value"

    private fun getAppInfo(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val packageName = packageInfo.packageName ?: "unknown"
            val versionName = packageInfo.versionName ?: "unknown"
            "//$packageName//$versionName"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
