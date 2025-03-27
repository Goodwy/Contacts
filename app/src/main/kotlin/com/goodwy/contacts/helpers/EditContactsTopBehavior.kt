package com.goodwy.contacts.helpers

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.behaviorule.arturdumchev.library.*
import com.goodwy.contacts.R
import com.goodwy.contacts.databinding.ActivityEditContactBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout

class EditContactsTopBehavior(
    context: Context?,
    attrs: AttributeSet?
) : BehaviorByRules(context, attrs) {
    private lateinit var binding: ActivityEditContactBinding

    override fun calcAppbarHeight(child: View): Int = with(child) {
        return height
    }

    override fun View.provideAppbar(): AppBarLayout {
        binding = ActivityEditContactBinding.bind(this)
        return  binding.contactAppbar
    }
    override fun View.provideCollapsingToolbar(): CollapsingToolbarLayout = binding.collapsingToolbar
    override fun canUpdateHeight(progress: Float): Boolean = progress >= GONE_VIEW_THRESHOLD

    override fun View.setUpViews(): List<RuledView> {
        val heightView = calcAppbarHeight(this)
        val height = if (heightView < 5) pixels(R.dimen.toolbar_height) else heightView.toFloat()
        return listOf(
            RuledView(
                binding.topDetails.root,
                BRuleYOffset(
                    min = -(height/4),
                    max = pixels(R.dimen.zero),
                    interpolator = LinearInterpolator()
                )
            ),
            RuledView(
                binding.topDetails.contactPhoto,
                BRuleXOffset(
                    min = 0f, max = pixels(R.dimen.edit_image_right_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleYOffset(
                    min = pixels(R.dimen.zero), max = pixels(R.dimen.image_top_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleScale(min = 0.5f, max = 1f)
            )
        )
    }

    private fun actionBarSize(context: Context?): Float {
        val styledAttributes = context!!.theme?.obtainStyledAttributes(IntArray(1) { android.R.attr.actionBarSize })
        val actionBarSize = styledAttributes?.getDimension(0, 0F)
        styledAttributes?.recycle()
        return actionBarSize ?: context.pixels(R.dimen.toolbar_height)
    }

    private fun getScreenWidth(): Int {
        return Resources.getSystem().displayMetrics.widthPixels
    }


    companion object {
        const val GONE_VIEW_THRESHOLD = 0.8f
    }
}
