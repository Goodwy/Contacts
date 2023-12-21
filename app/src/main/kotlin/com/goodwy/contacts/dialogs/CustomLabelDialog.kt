package com.goodwy.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.*
import com.goodwy.contacts.databinding.DialogCustomLabelBinding

class CustomLabelDialog(val activity: BaseSimpleActivity, val callback: (label: String) -> Unit) {
    init {
        val binding = DialogCustomLabelBinding.inflate(activity.layoutInflater)

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok, null)
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, com.goodwy.commons.R.string.label) { alertDialog ->
                    alertDialog.showKeyboard(binding.customLabelEdittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val label = binding.customLabelEdittext.value
                        if (label.isEmpty()) {
                            activity.toast(com.goodwy.commons.R.string.empty_name)
                            return@setOnClickListener
                        }

                        callback(label)
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
