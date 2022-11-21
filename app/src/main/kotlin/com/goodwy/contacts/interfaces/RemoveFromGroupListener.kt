package com.goodwy.contacts.interfaces

import com.goodwy.contacts.models.Contact

interface RemoveFromGroupListener {
    fun removeFromGroup(contacts: ArrayList<Contact>)
}
