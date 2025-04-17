package com.goodwy.contacts.fragments

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.commons.extensions.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.contacts.R
import com.goodwy.contacts.activities.EditContactActivity
import com.goodwy.contacts.activities.InsertOrEditContactActivity
import com.goodwy.contacts.activities.MainActivity
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.adapters.ContactsAdapter
import com.goodwy.contacts.databinding.FragmentContactsBinding
import com.goodwy.contacts.databinding.FragmentLettersLayoutBinding
import com.goodwy.contacts.extensions.config
import com.goodwy.contacts.helpers.LOCATION_CONTACTS_TAB
import com.goodwy.contacts.interfaces.RefreshContactsListener

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LetterLayout>(context, attributeSet) {

    private lateinit var binding: FragmentContactsBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentContactsBinding.bind(this)
        innerBinding = LetterLayout(FragmentLettersLayoutBinding.bind(binding.root))
    }

    override fun fabClicked() {
        activity?.hideKeyboard()
        Intent(context, EditContactActivity::class.java).apply {
            context.startActivity(this)
        }
    }

    override fun placeholderClicked() {
        if (activity is MainActivity) {
            (activity as MainActivity).showFilterDialog()
        } else if (activity is InsertOrEditContactActivity) {
            (activity as InsertOrEditContactActivity).showFilterDialog()
        }
    }

    fun setupContactsAdapter(contacts: List<Contact>) {
        setupViewVisibility(contacts.isNotEmpty())
        val currAdapter = innerBinding.fragmentList.adapter
        val showFastscroller = contacts.size > 10
        innerBinding.letterFastscroller.beVisibleIf(showFastscroller)
        innerBinding.fragmentList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                activity?.hideKeyboard()
            }
        })

        if (showFastscroller) {
            try {
                //Decrease the font size based on the number of letters in the letter scroller
                val allNotEmpty = contacts.filter { it.getNameToDisplay().isNotEmpty() }
                val all = allNotEmpty.map { it.getNameToDisplay().substring(0, 1) }
                val unique: Set<String> = HashSet(all)
                val sizeUnique = unique.size
                if (isHighScreenSize()) {
                    if (sizeUnique > 48) innerBinding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleTooTiny
                    else if (sizeUnique > 37) innerBinding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleTiny
                    else innerBinding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleSmall
                } else {
                    if (sizeUnique > 36) innerBinding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleTooTiny
                    else if (sizeUnique > 30) innerBinding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleTiny
                    else innerBinding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleSmall
                }
            } catch (e: Exception) {
                activity?.copyToClipboard(e.toString()) }
        }

        if (currAdapter == null || forceListRedraw) {
            forceListRedraw = false
            val location = LOCATION_CONTACTS_TAB

            ContactsAdapter(
                activity = activity as SimpleActivity,
                contactItems = contacts.toMutableList(),
                refreshListener = activity as RefreshContactsListener,
                location = location,
                removeListener = null,
                recyclerView = innerBinding.fragmentList,
                enableDrag = false,
            ) {
                (activity as RefreshContactsListener).contactClicked(it as Contact)
            }.apply {
                innerBinding.fragmentList.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                innerBinding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).apply {
                startNameWithSurname = context.config.startNameWithSurname
                showPhoneNumbers = context.config.showPhoneNumbers
                showContactThumbnails = context.config.showContactThumbnails
                updateItems(contacts)
            }
        }
    }

    private fun isHighScreenSize(): Boolean {
        return when (resources.configuration.screenLayout
            and Configuration.SCREENLAYOUT_LONG_MASK) {
            Configuration.SCREENLAYOUT_LONG_NO -> false
            else -> true
        }
    }

    override fun myRecyclerView() = innerBinding.fragmentList
}
