package com.aistra.hail.ui.home

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.aistra.hail.app.AppInfo
import com.aistra.hail.app.HailData
import com.aistra.hail.app.HailData.tags
import com.coolappstore.everlastingandroidtweak.databinding.FragmentHomeBinding
import com.aistra.hail.extensions.applyDefaultInsetter
import com.aistra.hail.extensions.isLandscape
import com.aistra.hail.extensions.isRtl
import com.aistra.hail.extensions.paddingRelative
import com.aistra.hail.ui.main.MainFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayoutMediator

class HomeFragment : MainFragment() {
    var multiselect: Boolean = false
    val selectedList: MutableList<AppInfo> = mutableListOf()
    private var _binding: FragmentHomeBinding? = null
    val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        if (tags.size == 1) binding.tabs.isVisible = false
        binding.pager.adapter = HomeAdapter(this)
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = tags[position].first
        }.attach()
        binding.tabs.applyDefaultInsetter {
            paddingRelative(requireContext().isRtl, start = !requireActivity().isLandscape, end = true)
        }
        applyTabPillStyle()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Re-apply every time the fragment becomes visible so the user sees the
        // updated style immediately after toggling it in Settings.
        applyTabPillStyle()
    }

    /**
     * Applies the pill / tab-bar background defined by [HailData.freezeTabPillSolid].
     *
     * ROOT CAUSE FIX — "Blur Home screen Pills blurs tab text":
     * The previous implementation called `b.tabs.setRenderEffect()` on the
     * TabLayout itself.  `setRenderEffect` on a ViewGroup blurs the entire
     * render output of that view, **including its child content** (tab labels,
     * icons).  This made the tab text unreadable when glass style was enabled.
     *
     * Fix strategy:
     *  • The TabLayout background is set to a semi-transparent solid colour.
     *  • `setRenderEffect` is called ONLY on `b.tabsBlurBackground` — a plain
     *    View that sits behind the TabLayout in the FrameLayout wrapper added
     *    to `fragment_home.xml`.
     *  • The TabLayout itself never receives a RenderEffect, so its text and
     *    icons are always drawn crisply on top of the blurred background layer.
     *
     * **Solid (true, default)**
     * Fully-opaque [colorSurfaceContainerLow] on the TabLayout; blur View hidden.
     *
     * **Glass (false)**
     * ~70 % opaque version of the same colour on the TabLayout so the grid peeks
     * through subtly.  On Android 12+ (API 31) a [android.graphics.RenderEffect]
     * blur is applied to the dedicated background View only, producing a proper
     * frosted-glass look without blurring the tab labels.
     */
    private fun applyTabPillStyle() {
        val b = _binding ?: return

        val baseColor = try {
            MaterialColors.getColor(
                b.tabs,
                com.google.android.material.R.attr.colorSurfaceContainerLow,
            )
        } catch (_: Exception) {
            // Fallback for older Material versions that don't have colorSurfaceContainerLow.
            MaterialColors.getColor(b.tabs, com.google.android.material.R.attr.colorSurface)
        }

        if (HailData.freezeTabPillSolid) {
            // ── Solid ──────────────────────────────────────────────────────────
            b.tabs.setBackgroundColor(baseColor)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Remove any previous blur from the TabLayout (should already be
                // null, but guard against stale state from a style transition).
                b.tabs.setRenderEffect(null)

                // Hide and clear the dedicated blur background view.
                b.tabsBlurBackground.visibility = View.GONE
                b.tabsBlurBackground.setRenderEffect(null)
            }
        } else {
            // ── Glass / frosted ────────────────────────────────────────────────
            val alpha = 178 // ~70 % opacity (0xB2)

            // Make the TabLayout semi-transparent so the content below shows
            // through, but apply NO blur to the TabLayout itself.
            b.tabs.setBackgroundColor(
                Color.argb(
                    alpha,
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor),
                )
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Ensure the TabLayout has NO render effect (tab text stays crisp).
                b.tabs.setRenderEffect(null)

                // Apply blur ONLY to the background View that sits BELOW the tabs.
                b.tabsBlurBackground.visibility = View.VISIBLE
                b.tabsBlurBackground.setBackgroundColor(
                    Color.argb(
                        alpha,
                        Color.red(baseColor),
                        Color.green(baseColor),
                        Color.blue(baseColor),
                    )
                )
                b.tabsBlurBackground.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                        20f, 20f,
                        android.graphics.Shader.TileMode.CLAMP,
                    )
                )
            }
        }
    }

    override fun onDestroyView() {
        multiselect = false
        selectedList.clear()
        super.onDestroyView()
        _binding = null
    }
}
