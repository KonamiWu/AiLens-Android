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
import com.google.android.libraries.navigation.NavigationView
import com.konami.ailens.R
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.command.LeaveNavigationCommand
import com.konami.ailens.databinding.FragmentNavigationGuidanceBinding
import com.konami.ailens.databinding.LayoutNavBottomBinding
import com.konami.ailens.databinding.LayoutNavTopBinding
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.NavigationDisplayCapability
import com.konami.ailens.orchestrator.role.Role
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sin

class NavigationGuidanceFragment : Fragment(), NavigationDisplayCapability, Role {
    private lateinit var binding: FragmentNavigationGuidanceBinding
    private lateinit var navTop: LayoutNavTopBinding
    private lateinit var navBottom: LayoutNavBottomBinding
    private var navigationView: NavigationView? = null

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Navigate back to HomeFragment when back button is pressed
            findNavController().popBackStack(R.id.MainFragment, false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNavigationGuidanceBinding.inflate(inflater, container, false)
        navBottom = LayoutNavBottomBinding.inflate(inflater, container, false)
        // Exit button listener is set in setupCustomFooter()

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

        Orchestrator.instance.register(this)
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
            Orchestrator.instance.stopNavigation()
        }

        NavigationService.instance.setCustomFooter(navBottom.root)
    }

    override fun registerCapabilities(sink: CapabilitySink) {
        sink.addNavigationDisplay(this)
    }

    override fun displayStartNavigation() {
    }

    override fun displayEndNavigation() {
        findNavController().popBackStack(R.id.MainFragment, false)
    }

    override fun displayCurrentStep(step: NavStep, remainingTimeInSecond: Int) {
        updateCurrentStep(step)
        navTop.currentImageView.setImageDrawable(step.maneuverIcon)

        // Update distance to next maneuver
        val distanceText = when {
            step.distanceMeters >= 1000 -> "%.1f km".format(step.distanceMeters / 1000.0)
            else -> "${step.distanceMeters} m"
        }
        navTop.distanceTextview.text = distanceText
        navTop.targetTextView.text = step.instruction
    }

    override fun displayRemainingSteps(steps: List<NavStep>) {
        if (steps.isEmpty())
            updateNextStep(null)
        else
            updateNextStep(steps.first())
    }

    override fun displayMap(mapView: View) {
        // Save reference to NavigationView
        if (mapView is NavigationView) {
            navigationView = mapView
        }

        // Remove mapView from its current parent first
        (mapView.parent as? ViewGroup)?.removeView(mapView)

        // Clear any existing views in the container
        binding.mapContainer.removeAllViews()

        // Add mapView to the container
        binding.mapContainer.addView(mapView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun displayRemainingDistance(meters: Int) {
        updateDistanceDisplay(meters.toDouble())
    }

    override fun displayRemainingTime(seconds: Int) {
        updateTimeDisplay(seconds.toLong())
    }

    override fun displayETA(eta: Instant) {
        updateETADisplay(eta)
    }

    private fun updateCurrentStep(step: NavStep) {
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

    override fun onStart() {
        super.onStart()
        navigationView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        navigationView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        navigationView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        navigationView?.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Don't clear custom footer here - let the new view replace it
        Orchestrator.instance.removeNavigationDisplay(this)
        navigationView = null
    }
}
