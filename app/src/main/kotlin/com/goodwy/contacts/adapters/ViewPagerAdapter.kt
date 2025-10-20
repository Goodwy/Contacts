package com.goodwy.contacts.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.goodwy.commons.extensions.getProperAccentColor
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.getProperTextColor
import com.goodwy.commons.helpers.TAB_CONTACTS
import com.goodwy.commons.helpers.TAB_FAVORITES
import com.goodwy.commons.helpers.TAB_GROUPS
import com.goodwy.contacts.R
import com.goodwy.contacts.activities.SimpleActivity
import com.goodwy.contacts.fragments.MyViewPagerFragment

class ViewPagerAdapter(val activity: SimpleActivity, val currTabsList: ArrayList<Int>, val showTabs: Int) : PagerAdapter() {

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = getFragment(position)
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        (view as MyViewPagerFragment<*>).apply {
            setupFragment(activity)
            setupColors(
                textColor = activity.getProperTextColor(),
                primaryColor = activity.getProperPrimaryColor(),
                accentColor = activity.getProperAccentColor()
            )
        }

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount() = currTabsList.filter { it and showTabs != 0 }.size

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragment(position: Int): Int {
        val fragments = arrayListOf<Int>()
        if (showTabs and TAB_FAVORITES != 0) {
            fragments.add(R.layout.fragment_favorites)
        }

        if (showTabs and TAB_CONTACTS != 0) {
            fragments.add(R.layout.fragment_contacts)
        }

        if (showTabs and TAB_GROUPS != 0) {
            fragments.add(R.layout.fragment_groups)
        }

        return fragments[position]
    }
}
