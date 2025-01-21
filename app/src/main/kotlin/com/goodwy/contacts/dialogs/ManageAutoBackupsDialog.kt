package com.goodwy.contacts.dialogs

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.dialogs.FilePickerDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.ContactSource
import com.goodwy.contacts.R
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.adapters.FilterContactSourcesAdapter
import com.goodwy.contacts.databinding.DialogManageAutomaticBackupsBinding
import com.goodwy.contacts.extensions.config
import com.goodwy.contacts.extensions.getFormattedTime
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.io.File

class ManageAutoBackupsDialog(private val activity: SimpleActivity, onSuccess: () -> Unit) {
    private val binding = DialogManageAutomaticBackupsBinding.inflate(activity.layoutInflater)
    private val config = activity.config
    private var backupFolder = config.autoBackupFolder
    private var contactSources = mutableListOf<ContactSource>()
    private var selectedContactSources = config.autoBackupContactSources
    private var contacts = ArrayList<Contact>()
    private var isContactSourcesReady = false
    private var isContactsReady = false
    private var time = config.autoBackupTime
    private var interval = config.autoBackupInterval

    init {
        binding.apply {
            backupContactsFolder.setText(activity.humanizePath(backupFolder))
            val filename = config.autoBackupFilename.ifEmpty {
                "${activity.getString(R.string.contacts)}_%Y%M%D_%h%m%s"
            }

            backupContactsFilename.setText(filename)
            backupContactsFilenameHint.setEndIconOnClickListener {
                DateTimePatternInfoDialog(activity)
            }

            backupContactsFilenameHint.setEndIconOnLongClickListener {
                DateTimePatternInfoDialog(activity)
                true
            }

            backupContactsFolder.setOnClickListener {
                selectBackupFolder()
            }

            ContactsHelper(activity).getContactSources { sources ->
                contactSources = sources
                isContactSourcesReady = true
                processDataIfReady(this)
            }

            ContactsHelper(activity).getContacts(getAll = true) { receivedContacts ->
                contacts = receivedContacts
                isContactsReady = true
                processDataIfReady(this)
            }

            updateTime(config.autoBackupTime)
            backupContactsTimeHolder.setOnClickListener {
                if (activity.isDynamicTheme()) {
                    val timeFormat = if (DateFormat.is24HourFormat(activity)) {
                        TimeFormat.CLOCK_24H
                    } else {
                        TimeFormat.CLOCK_12H
                    }

                    val timePicker = MaterialTimePicker.Builder()
                        .setTimeFormat(timeFormat)
                        .setHour(config.autoBackupTime / 60)
                        .setMinute(config.autoBackupTime % 60)
                        .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                        .build()

                    timePicker.addOnPositiveButtonClickListener {
                        timePicked(timePicker.hour, timePicker.minute)
                    }

                    timePicker.show(activity.supportFragmentManager, "")
                } else {
                    TimePickerDialog(
                        root.context,
                        root.context.getTimePickerDialogTheme(),
                        timeSetListener,
                        config.autoBackupTime / 60,
                        config.autoBackupTime % 60,
                        DateFormat.is24HourFormat(activity)
                    ).show()
                }
            }

            updateInterval(config.autoBackupInterval)
            backupContactsIntervalHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(1, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 1, 1))),
                    RadioItem(2, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 2, 2))),
                    RadioItem(3, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 3, 3))),
                    RadioItem(4, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 4, 4))),
                    RadioItem(5, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 5, 5))),
                    RadioItem(6, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 6, 6))),
                    RadioItem(7, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 7, 7))),
                    RadioItem(10, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 10, 10))),
                    RadioItem(14, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 14, 14))),
                    RadioItem(20, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 20, 20))),
                    RadioItem(30, String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, 30, 30)))
                )

                RadioGroupDialog(activity, items, interval) {
                    interval = it as Int
                    updateInterval(it)
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok, null)
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, com.goodwy.commons.R.string.manage_automatic_backups) { dialog ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (binding.backupContactSourcesList.adapter == null) {
                            return@setOnClickListener
                        }
                        val filename = binding.backupContactsFilename.value
                        when {
                            filename.isEmpty() -> activity.toast(com.goodwy.commons.R.string.empty_name)
                            filename.isAValidFilename() -> {
                                val file = File(backupFolder, "$filename.vcf")
                                if (file.exists() && !file.canWrite()) {
                                    activity.toast(com.goodwy.commons.R.string.name_taken)
                                    return@setOnClickListener
                                }

                                val selectedSources = (binding.backupContactSourcesList.adapter as FilterContactSourcesAdapter).getSelectedContactSources()
                                if (selectedSources.isEmpty()) {
                                    activity.toast(com.goodwy.commons.R.string.no_entries_for_exporting)
                                    return@setOnClickListener
                                }

                                config.autoBackupContactSources = selectedSources.map { it.name }.toSet()
                                config.autoBackupTime = time
                                config.autoBackupInterval = interval

                                ensureBackgroundThread {
                                    config.apply {
                                        autoBackupFolder = backupFolder
                                        autoBackupFilename = filename
                                    }

                                    activity.runOnUiThread {
                                        onSuccess()
                                    }

                                    dialog.dismiss()
                                }
                            }

                            else -> activity.toast(com.goodwy.commons.R.string.invalid_name)
                        }
                    }
                }
            }
    }

    private fun processDataIfReady(binding: DialogManageAutomaticBackupsBinding) {
        if (!isContactSourcesReady || !isContactsReady) {
            return
        }

        if (selectedContactSources.isEmpty()) {
            selectedContactSources = contactSources.map { it.name }.toSet()
        }

        val contactSourcesWithCount = mutableListOf<ContactSource>()
        for (source in contactSources) {
            val count = contacts.count { it.source == source.name }
            contactSourcesWithCount.add(source.copy(count = count))
        }

        contactSources.clear()
        contactSources.addAll(contactSourcesWithCount)

        activity.runOnUiThread {
            binding.backupContactSourcesList.adapter = FilterContactSourcesAdapter(activity, contactSourcesWithCount, selectedContactSources.toList())
        }
    }

    private fun selectBackupFolder() {
        activity.hideKeyboard(binding.backupContactsFilename)
        FilePickerDialog(activity, backupFolder, false, showFAB = true) { path ->
            activity.handleSAFDialog(path) { grantedSAF ->
                if (!grantedSAF) {
                    return@handleSAFDialog
                }

                activity.handleSAFDialogSdk30(path) { grantedSAF30 ->
                    if (!grantedSAF30) {
                        return@handleSAFDialogSdk30
                    }

                    backupFolder = path
                    binding.backupContactsFolder.setText(activity.humanizePath(path))
                }
            }
        }
    }

    private val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
        timePicked(hourOfDay, minute)
    }

    private fun timePicked(hours: Int, minutes: Int) {
        time = hours * 60 + minutes
        updateTime(time)
    }

    private fun updateTime(time: Int) {
        binding.backupContactsTimeValue.text = activity.getFormattedTime(passedSeconds = time * 60, showSeconds = false, makeAmPmSmaller = true)
    }

    private fun updateInterval(interval: Int) {
        binding.backupContactsIntervalValue.text = String.format(activity.resources.getQuantityString(com.goodwy.commons.R.plurals.days, interval, interval))
    }
}

