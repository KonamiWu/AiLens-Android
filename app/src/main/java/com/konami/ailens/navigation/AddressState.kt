package com.konami.ailens.navigation

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentAddressPickerBinding
import com.konami.ailens.main.CutCornerView
import com.konami.ailens.navigation.AddressPickerFragment.SmoothInterpolator
import kotlin.math.abs

class AddressState(val fragment: AddressPickerFragment, isReverse: Boolean): LayoutState {
    private enum class State {
        COLLAPSE,
        EXPAND
    }
    private var previousY = 0f
    private var touchActive = false
    private var state = State.EXPAND
    private val context: Context = fragment.requireContext()
    private val velocityThreshold = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat() * 30f

    private val resources: Resources = context.resources
    private val binding: FragmentAddressPickerBinding = fragment.binding
    private val topInset: Int = fragment.topInset
    private val topMargin: Float = fragment.topMargin

    init {
        if (isReverse) {
            val animationDuration = 600L

            val set = ConstraintSet()
            set.clone(binding.main)
            set.setMargin(R.id.container, ConstraintSet.START, 0)
            set.setMargin(R.id.container, ConstraintSet.END, 0)
            set.setMargin(R.id.container, ConstraintSet.TOP, resources.getDimension(R.dimen.address_picker_margin_top).toInt())
            set.setMargin(R.id.container, ConstraintSet.BOTTOM, 0)
            set.connect(R.id.container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            set.constrainHeight(R.id.container, ConstraintSet.MATCH_CONSTRAINT)

            TransitionManager.beginDelayedTransition(binding.main, AutoTransition().apply {
                duration = animationDuration
                interpolator = AddressPickerFragment.SmoothInterpolator()
            })
            set.applyTo(binding.main)

            binding.transportLayout.animate().alpha(0f).setDuration(animationDuration).start()
            binding.favoriteRecyclerView.animate().alpha(1f).setDuration(animationDuration).start()
            binding.cutCornerView.animateRemoveCorners(CutCornerView.CUT_TOP_LEFT or CutCornerView.CUT_TOP_RIGHT, animationDuration)
            binding.grabImageView.animate().alpha(1f).setDuration(animationDuration).start()
            
            // Hide the back button when entering AddressState
            binding.backButton.animate().alpha(0f).setDuration(animationDuration).withEndAction {
                binding.backButton.visibility = View.GONE
            }.start()
        } else {
            // Also hide back button for non-reverse initialization
            binding.backButton.visibility = View.GONE
            binding.backButton.alpha = 0f
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                binding.container.animate().cancel()
                touchActive = false
                previousY = event.rawY
                // If touch starts on the container itself, consume to keep receiving moves.
                // For child views (RecyclerView/EditText), let them handle until drag recognized.
                return if (view === binding.container) true else false
            }
            MotionEvent.ACTION_MOVE -> {
                val currentY = event.rawY
                val diff = currentY - previousY
                if (!touchActive && abs(diff) > touchSlop) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    //close keyboard here
                    imm.hideSoftInputFromWindow(binding.destinationEditText.windowToken, 0)
                    binding.destinationEditText.clearFocus()
                    touchActive = true
                }
                if (!touchActive) {
                    return false
                }

                // Engage drag on container
                view.parent.requestDisallowInterceptTouchEvent(true)
                previousY = currentY

                val params = binding.container.layoutParams as ConstraintLayout.LayoutParams
                val set = ConstraintSet()
                set.clone(binding.main)
                set.setMargin(R.id.container, ConstraintSet.TOP, params.topMargin + diff.toInt())
                set.applyTo(binding.main)
                updateLayout()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.parent.requestDisallowInterceptTouchEvent(false)

                if (touchActive) {
                    val velocity = binding.root.velocity
                    if (velocity > velocityThreshold) {
                        collapse()
                    } else if (velocity < -velocityThreshold) {
                        expand()
                    } else {
                        if (binding.container.top > binding.container.height / 3 * 2)
                            collapse()
                        else if (binding.container.top < binding.container.height / 3)
                            expand()
                        else
                            reverse()
                    }
                    updateLayout()
                }
                // Only consume if we were dragging; otherwise allow clicks/edits
                return touchActive
            }
        }
        return false
    }

     fun updateLayout() {
         val slidingDistance = fragment.slidingDistance
         binding.transportLayout.visibility = View.VISIBLE

        val progress = (binding.container.top - topMargin - topInset) / (slidingDistance - topMargin - topInset)
        val editTextMarginSpacing = context.resources.getDimension(R.dimen.address_picker_edit_text_spacing)
        val editTextHeight = context.resources.getDimension(R.dimen.address_picker_edit_text_height)
        val start = editTextMarginSpacing
        val end = -(editTextHeight)
        val value = start + (end - start) * progress
        val set = ConstraintSet()
        set.clone(binding.topLayout)
        set.connect(R.id.destinationLayout, ConstraintSet.TOP, R.id.myLocationLayout, ConstraintSet.BOTTOM, value.toInt())
        set.applyTo(binding.topLayout)

        binding.myLocationLayout.alpha = (1 - (progress / 0.5f)).coerceIn(0f, 1f)
    }

    private fun expand() {
        state = State.EXPAND
        val params = binding.container.layoutParams as ConstraintLayout.LayoutParams
        ValueAnimator.ofFloat(params.topMargin.toFloat(), topMargin).apply {
            duration = 400
            interpolator = SmoothInterpolator()
            addUpdateListener {
                val set = ConstraintSet()
                set.clone(binding.main)
                set.setMargin(R.id.container, ConstraintSet.TOP, (it.animatedValue as Float).toInt())
                set.applyTo(binding.main)
                updateLayout()
            }
            start()
        }
    }

    // Expose a safe entry for external callers (e.g., EditText focus)
    fun requestExpand() {
        expand()
    }

    private fun collapse() {
        state = State.COLLAPSE
        val slidingDistance = fragment.slidingDistance
        val params = binding.container.layoutParams as ConstraintLayout.LayoutParams
        val originalMarginTop = params.topMargin.toFloat()
        val target = slidingDistance
        ValueAnimator.ofFloat(originalMarginTop, target).apply {
            duration = 400
            interpolator = SmoothInterpolator()
            addUpdateListener {
                val set = ConstraintSet()
                set.clone(binding.main)
                set.setMargin(R.id.container, ConstraintSet.TOP, (it.animatedValue as Float).toInt())
                set.applyTo(binding.main)
                updateLayout()
            }
            start()
        }
    }

    private fun reverse() {
        when (state) {
            State.EXPAND ->
                expand()
            State.COLLAPSE ->
                collapse()
        }
    }
}
