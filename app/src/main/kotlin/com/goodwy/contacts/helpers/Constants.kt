package com.goodwy.contacts.helpers

import com.goodwy.commons.helpers.TAB_CONTACTS
import com.goodwy.commons.helpers.TAB_FAVORITES
import com.goodwy.commons.helpers.TAB_GROUPS

const val GROUP = "group"
const val IS_FROM_SIMPLE_CONTACTS = "is_from_simple_contacts"
const val ADD_NEW_CONTACT_NUMBER = "add_new_contact_number"
const val FIRST_CONTACT_ID = 1000000
const val FIRST_GROUP_ID = 10000L
const val DEFAULT_FILE_NAME = "contacts.vcf"
const val AVOID_CHANGING_TEXT_TAG = "avoid_changing_text_tag"
const val AVOID_CHANGING_VISIBILITY_TAG = "avoid_changing_visibility_tag"

// extras used at third party intents
const val KEY_NAME = "name"
const val KEY_EMAIL = "email"
const val KEY_MAILTO = "mailto"

const val LOCATION_FAVORITES_TAB = 0
const val LOCATION_CONTACTS_TAB = 1
const val LOCATION_GROUP_CONTACTS = 2
const val LOCATION_INSERT_OR_EDIT = 3

val tabsList = arrayListOf(
    TAB_FAVORITES,
    TAB_CONTACTS,
    TAB_GROUPS
)
const val ALL_TABS_MASK = TAB_CONTACTS or TAB_FAVORITES or TAB_GROUPS

// phone number/email types
const val CELL = "CELL"
const val WORK = "WORK"
const val HOME = "HOME"
const val OTHER = "OTHER"
const val PREF = "PREF"
const val MAIN = "MAIN"
const val FAX = "FAX"
const val WORK_FAX = "WORK;FAX"
const val HOME_FAX = "HOME;FAX"
const val PAGER = "PAGER"
const val MOBILE = "MOBILE"

// IMs not supported by Ez-vcard
const val HANGOUTS = "Hangouts"
const val QQ = "QQ"
const val JABBER = "Jabber"

const val WHATSAPP = "whatsapp"
const val SIGNAL = "signal"
const val VIBER = "viber"
const val TELEGRAM = "telegram"
const val THREEMA = "threema"

// relation types
const val ASSISTANT = "ASSISTANT"
const val BROTHER = "BROTHER"
const val CHILD = "CHILD"
const val DOMESTIC = "DOMESTIC"
const val FATHER = "FATHER"
const val FRIEND = "FRIEND"
const val MANAGER = "MANAGER"
const val MOTHER = "MOTHER"
const val PARENT = "PARENT"
const val PARTNER = "PARTNER"
const val REFERRED = "REFERRED"
const val RELATIVE = "RELATIVE"
const val SISTER = "SISTER"
const val SPOUSE = "SPOUSE"
