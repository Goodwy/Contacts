package com.goodwy.contacts.helpers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.behaviorule.arturdumchev.library.*
import com.goodwy.contacts.R
import com.goodwy.contacts.databinding.ActivityViewContactBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout


class ViewContactsTopBehavior(
    context: Context?,
    attrs: AttributeSet?
) : BehaviorByRules(context, attrs) {
    private lateinit var binding: ActivityViewContactBinding

    override fun calcAppbarHeight(child: View): Int = with(child) {
        return height
    }

    override fun View.provideAppbar(): AppBarLayout {
        binding = ActivityViewContactBinding.bind(this)
        return  binding.contactAppbar
    }
    override fun View.provideCollapsingToolbar(): CollapsingToolbarLayout = binding.collapsingToolbar
    override fun canUpdateHeight(progress: Float): Boolean = progress >= GONE_VIEW_THRESHOLD

    override fun View.setUpViews(): List<RuledView> {
        val heightView = calcAppbarHeight(this)
        val height = if (heightView < 5) pixels(R.dimen.toolbar_height) else heightView.toFloat()

        val density = context.resources.displayMetrics.density
        val screenWidthDp = context.resources.configuration.screenWidthDp * density
        val factor = if (screenWidthDp < 1200) 0.092 else 0.088
        val screenWidth =
            ((screenWidthDp - context.resources.getDimension(com.goodwy.commons.R.dimen.activity_padding_left_right)).toDouble() * factor).toFloat()

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
                    min = 0f, max = pixels(R.dimen.image_right_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleYOffset(
                    min = pixels(R.dimen.zero), max = pixels(R.dimen.image_top_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleScale(min = 0.5f, max = 1f)
            ),
            RuledView(
                binding.topDetails.contactName,
                BRuleXOffset(
                    min = 0f, max = screenWidth,
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleYOffset(
                    min = -pixels(R.dimen.name_top_margin), max = pixels(R.dimen.zero),
                    interpolator = LinearInterpolator()
                ),
                BRuleScale(min = 0.8f, max = 1f)
            ),
            RuledView(
                binding.topDetails.contactCompanyHolder,
                BRuleXOffset(
                    min = 0f, max = screenWidth,
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleYOffset(
                    min = -pixels(R.dimen.company_top_margin), max = pixels(R.dimen.zero),
                    interpolator = LinearInterpolator()
                ),
                BRuleScale(min = 0.8f, max = 1f),
                BRuleAppear(visibleUntil = GONE_VIEW_THRESHOLD),
            ),
            RuledView(
                binding.topDetails.contactOrganizationCompany,
                BRuleAlpha(min = 0f, max = 0.6f),
            ),
            RuledView(
                binding.topDetails.contactOrganizationJobPosition,
                BRuleYOffset(
                    min = pixels(com.goodwy.commons.R.dimen.medium_margin), max = pixels(com.goodwy.commons.R.dimen.big_margin),
                    interpolator = LinearInterpolator()
                ),
                BRuleAlpha(min = 0f, max = 0.6f),
            )
        )
    }

    companion object {
        const val GONE_VIEW_THRESHOLD = 0.4f
    }
}
