package com.konami.ailens.navigation

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.command.LeaveNavigationCommand
import com.konami.ailens.databinding.FragmentNavigationGuidanceBinding
import com.konami.ailens.databinding.LayoutNavBottomBinding
import com.konami.ailens.databinding.LayoutNavTopBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class NavigationGuidanceFragment : Fragment() {

    private var _binding: FragmentNavigationGuidanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var navTop: LayoutNavTopBinding
    private lateinit var navBottom: LayoutNavBottomBinding
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Navigate back to HomeFragment when back button is pressed
            findNavController().popBackStack(R.id.MainFragment, false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNavigationGuidanceBinding.inflate(inflater, container, false)
        navBottom = LayoutNavBottomBinding.inflate(inflater, container, false)
        NavigationService.attachTo(binding.guidanceMapContainer, viewLifecycleOwner, requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize navTop binding from included layout
        navTop = binding.navTop

        // Register the back pressed callback
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        // Set custom footer after view is created and NavigationView is ready
        setupCustomFooter()

        // Observe navigation state and update UI
        observeNavigationState()
    }

    private fun setupCustomFooter() {
        val cutCornerView = navBottom.cutCornerView
        val originalBottomPadding = cutCornerView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(navBottom.controlLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            cutCornerView.setPadding(
                cutCornerView.paddingLeft + systemBars.left,
                cutCornerView.paddingTop,
                cutCornerView.paddingRight + systemBars.right,
                originalBottomPadding + systemBars.bottom
            )
            insets
        }

        // Setup exit button
        navBottom.exitButton.setOnClickListener {
            NavigationService.stop()
            findNavController().popBackStack(R.id.MainFragment, false)
            BLEService.instance.connectedSession.value?.add(LeaveNavigationCommand())
        }

        NavigationService.setCustomFooter(navBottom.root)
    }

    private fun observeNavigationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe current navigation step
                launch {
                    NavigationService.state.currentStep.collect { step ->
                        updateCurrentStep(step)
                    }
                }

                // Observe remaining steps for "Then" indicator
                launch {
                    NavigationService.state.remainingSteps.collect { steps ->
                        updateNextStep(steps.firstOrNull())
                    }
                }

                // Observe remaining time
                launch {
                    NavigationService.state.remainingTimeSec.collect { seconds ->
                        updateTimeDisplay(seconds)
                    }
                }

                // Observe remaining distance
                launch {
                    NavigationService.state.remainingDistanceMeters.collect { meters ->
                        updateDistanceDisplay(meters)
                    }
                }

                // Observe ETA
                launch {
                    NavigationService.state.eta.collect { eta ->
                        updateETADisplay(eta)
                    }
                }
            }
        }
    }

    private fun updateTimeDisplay(seconds: Long?) {
        if (seconds == null || seconds <= 0) {
            navBottom.timeNumberTextView.text = "--"
            return
        }

        val minutes = (seconds / 60).toInt()
        val hours = minutes / 60
        val remainingMinutes = minutes % 60

        navBottom.timeNumberTextView.text = when {
            hours > 0 -> "${hours}h ${remainingMinutes}min"
            else -> "${minutes}min"
        }
    }

    private fun updateDistanceDisplay(meters: Double?) {
        if (meters == null || meters <= 0) {
            updateDistanceETAText(null, null)
            return
        }

        val distanceText = when {
            meters >= 1000 -> "%.1f km".format(meters / 1000)
            else -> "${meters.roundToInt()} m"
        }

        updateDistanceETAText(distanceText, null)
    }

    private fun updateETADisplay(eta: java.time.Instant?) {
        if (eta == null) {
            updateDistanceETAText(null, null)
            return
        }

        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val etaText = formatter.format(Date.from(eta))

        updateDistanceETAText(null, etaText)
    }

    private var cachedDistance: String? = null
    private var cachedETA: String? = null

    private fun updateDistanceETAText(distance: String?, eta: String?) {
        // Cache values
        if (distance != null) cachedDistance = distance
        if (eta != null) cachedETA = eta

        // Build combined text
        val parts = mutableListOf<String>()
        cachedDistance?.let { parts.add(it) }
        cachedETA?.let { parts.add(it) }

        navBottom.distanceETATextView.text = if (parts.isEmpty()) {
            "--"
        } else {
            parts.joinToString(" · ")
        }
    }

    private fun updateCurrentStep(step: NavStep?) {
        if (step == null) {
            // No current step - hide or show placeholder
            navTop.currentImageView.setImageDrawable(null)
            navTop.distanceTextview.text = "--"
            navTop.targetTextView.text = "--"
            return
        }

        navTop.currentImageView.setImageDrawable(step.maneuverIcon)

        // Update distance to next maneuver
        val distanceText = when {
            step.distanceMeters >= 1000 -> "%.1f km".format(step.distanceMeters / 1000.0)
            else -> "${step.distanceMeters} m"
        }
        navTop.distanceTextview.text = distanceText
        navTop.targetTextView.text = step.instruction
    }

    private fun updateNextStep(nextStep: NavStep?) {
        if (nextStep == null) {
            // No next step - hide the "Then" indicator
            navTop.nextStepContainer.visibility = View.GONE
        } else {
            // Show the "Then" indicator with next maneuver icon
            navTop.nextStepContainer.visibility = View.VISIBLE
            navTop.nextImageView.setImageDrawable(nextStep.maneuverIcon)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Don't clear custom footer here - let the new view replace it
        _binding = null
    }
}
