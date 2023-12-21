package com.goodwy.contacts.interfaces

import com.goodwy.commons.models.contacts.Contact

interface RefreshContactsListener {
    fun refreshContacts(refreshTabsMask: Int)

    fun contactClicked(contact: Contact)
}
