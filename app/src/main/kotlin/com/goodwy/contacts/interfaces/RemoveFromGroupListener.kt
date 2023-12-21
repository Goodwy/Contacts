package com.goodwy.contacts.interfaces

import com.goodwy.commons.models.contacts.Contact

interface RemoveFromGroupListener {
    fun removeFromGroup(contacts: ArrayList<Contact>)
}
