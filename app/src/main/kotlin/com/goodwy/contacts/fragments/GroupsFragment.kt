package com.goodwy.contacts.fragments

import android.content.Context
import android.util.AttributeSet
import com.goodwy.commons.helpers.TAB_GROUPS
import com.goodwy.contacts.activities.MainActivity
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.databinding.FragmentGroupsBinding
import com.goodwy.contacts.databinding.FragmentLayoutBinding
import com.goodwy.contacts.dialogs.CreateNewGroupDialog

class GroupsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.FragmentLayout>(context, attributeSet) {

    private lateinit var binding: FragmentGroupsBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentGroupsBinding.bind(this)
        innerBinding = FragmentLayout(FragmentLayoutBinding.bind(binding.root))
    }

    override fun fabClicked() {
        finishActMode()
        showNewGroupsDialog()
    }

    override fun placeholderClicked() {
        showNewGroupsDialog()
    }

    private fun showNewGroupsDialog() {
        CreateNewGroupDialog(activity as SimpleActivity) {
            (activity as? MainActivity)?.refreshContacts(TAB_GROUPS)
        }
    }

    override fun myRecyclerView() = innerBinding.fragmentList
}
