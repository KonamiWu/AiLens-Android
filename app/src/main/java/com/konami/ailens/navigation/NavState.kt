package com.konami.ailens.navigation

import android.content.Context
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentAddressPickerBinding
import com.konami.ailens.main.CutCornerView

class NavState(val fragment: AddressPickerFragment): LayoutState {
    private val context: Context = fragment.requireContext()
    private val binding: FragmentAddressPickerBinding = fragment.binding
    private val animationDuration = 600L

    init {
        animateToTransportMode()
        binding.cutCornerView.animateAddCorners(
            binding.cutCornerView.cutCorners or CutCornerView.CUT_BOTTOM_LEFT or CutCornerView.CUT_BOTTOM_RIGHT,
            animationDuration
        )

        // Show the back button when entering NavState
        binding.backButton.visibility = View.VISIBLE
        binding.backButton.alpha = 0f  // Start from invisible
        binding.backButton.animate().alpha(1f).setDuration(animationDuration).start()
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        return true
    }

    fun animateToTransportMode() {
        val insets = ViewCompat.getRootWindowInsets(binding.root)
        val navBarInset = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val horizontalInset = context.resources.getDimension(R.dimen.address_picker_nav_mode_padding_horizontal)

        val set = ConstraintSet()
        set.clone(binding.main)
        set.clear(R.id.container, ConstraintSet.TOP)

        set.constrainHeight(R.id.container, ConstraintSet.WRAP_CONTENT)
        set.connect(R.id.container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

        set.setMargin(R.id.container, ConstraintSet.START, horizontalInset.toInt())
        set.setMargin(R.id.container, ConstraintSet.TOP, 0)
        set.setMargin(R.id.container, ConstraintSet.END, horizontalInset.toInt())
        set.setMargin(R.id.container, ConstraintSet.BOTTOM, navBarInset)

        TransitionManager.beginDelayedTransition(binding.main, AutoTransition().apply {
            duration = animationDuration
            interpolator = AddressPickerFragment.SmoothInterpolator()
        })

        set.applyTo(binding.main)

        binding.favoriteRecyclerView.animate().alpha(0f).setDuration(animationDuration).start()
        binding.transportLayout.animate().alpha(1f).setDuration(animationDuration).start()
        binding.grabImageView.animate().alpha(0f).setDuration(animationDuration).start()
    }
}