package com.goodwy.contacts.dialogs

import android.view.View
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.ContactSource
import com.goodwy.commons.models.contacts.Group
import com.goodwy.contacts.R
import com.goodwy.contacts.databinding.DialogCreateNewGroupBinding

class CreateNewGroupDialog(val activity: BaseSimpleActivity, val callback: (newGroup: Group) -> Unit) {
    init {
        val binding = DialogCreateNewGroupBinding.inflate(activity.layoutInflater)

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok, null)
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.create_new_group) { alertDialog ->
                    alertDialog.showKeyboard(binding.groupName)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
                        val name = binding.groupName.value
                        if (name.isEmpty()) {
                            activity.toast(com.goodwy.commons.R.string.empty_name)
                            return@OnClickListener
                        }

                        val contactSources = ArrayList<ContactSource>()
                        ContactsHelper(activity).getContactSources {
//                            it.filter { it.type.contains("google", true) }.mapTo(contactSources) { ContactSource(it.name, it.type, it.name) }
//                            contactSources.add(activity.getPrivateContactSource())
                            it.mapTo(contactSources) { ContactSource(it.name, it.type, it.publicName) }

                            val items = ArrayList<RadioItem>()
                            contactSources.forEachIndexed { index, contactSource ->
                                items.add(RadioItem(index, contactSource.publicName))
                            }

                            activity.runOnUiThread {
                                if (items.size == 1) {
                                    createGroupUnder(name, contactSources.first(), alertDialog)
                                } else {
                                    RadioGroupDialog(activity, items, titleId = R.string.create_group_under_account) {
                                        val contactSource = contactSources[it as Int]
                                        createGroupUnder(name, contactSource, alertDialog)
                                    }
                                }
                            }
                        }
                    })
                }
            }
    }

    private fun createGroupUnder(name: String, contactSource: ContactSource, dialog: AlertDialog) {
        ensureBackgroundThread {
            val newGroup = ContactsHelper(activity).createNewGroup(name, contactSource.name, contactSource.type)
            activity.runOnUiThread {
                if (newGroup != null) {
                    callback(newGroup)
                }
                dialog.dismiss()
            }
        }
    }
}
