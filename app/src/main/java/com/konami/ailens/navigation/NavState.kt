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

        // Disable editing in NavState - hide EditText and show marquee TextView
        binding.destinationEditText.visibility = View.INVISIBLE // Keep space but hide content

        // Show marquee TextView overlay with the same text
        val destinationText = binding.destinationEditText.text?.toString() ?: ""
        if (destinationText.isNotEmpty()) {
            binding.destinationMarqueeTextView.text = destinationText
            binding.destinationMarqueeTextView.visibility = View.VISIBLE

            // Only enable marquee if text is longer than the view width
            binding.destinationMarqueeTextView.post {
                val paint = binding.destinationMarqueeTextView.paint
                val textWidth = paint.measureText(destinationText)
                val viewWidth = binding.destinationMarqueeTextView.width

                // Only enable marquee if text overflows
                if (textWidth > viewWidth) {
                    binding.destinationMarqueeTextView.isSelected = true
                } else {
                    binding.destinationMarqueeTextView.isSelected = false
                }
            }
        }
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

        // Apply separate ConstraintSet for topLayout (destinationLayout is inside topLayout)
        val editTextMarginSpacing = context.resources.getDimension(R.dimen.address_picker_edit_text_spacing)
        val set2 = ConstraintSet()
        set2.clone(binding.topLayout)
        set2.setMargin(R.id.destinationLayout, ConstraintSet.TOP, editTextMarginSpacing.toInt())
        set2.applyTo(binding.topLayout)

        binding.transportLayout.visibility = View.VISIBLE
        binding.favoriteRecyclerView.animate().alpha(0f).setDuration(animationDuration / 2).withEndAction {
            binding.transportLayout.animate().alpha(1f).setDuration(animationDuration / 2).start()
            binding.myLocationLayout.animate().alpha(1f).setDuration(animationDuration / 2).start()
        }.start()

        binding.grabImageView.animate().alpha(0f).setDuration(animationDuration).start()

    }
}