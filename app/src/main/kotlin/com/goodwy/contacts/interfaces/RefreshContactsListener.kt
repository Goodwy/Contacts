package com.goodwy.contacts.interfaces

import com.goodwy.contacts.models.Contact

interface RefreshContactsListener {
    fun refreshContacts(refreshTabsMask: Int)

    fun contactClicked(contact: Contact)
}
