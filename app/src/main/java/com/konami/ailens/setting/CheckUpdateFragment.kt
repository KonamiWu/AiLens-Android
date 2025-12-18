package com.konami.ailens.setting

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.FirmwareManager
import com.konami.ailens.ble.FirmwareUpdateService
import com.konami.ailens.ble.Glasses
import com.konami.ailens.databinding.FragmentCheckUpdateBinding
import com.konami.ailens.resolveAttrColor
import com.konami.ailens.ui.LoadingDialogFragment
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch

class CheckUpdateFragment: Fragment() {
    private lateinit var binding: FragmentCheckUpdateBinding
    private val viewModel: CheckUpdateViewModel by viewModels {
        CheckUpdateViewModelFactory(BLEService.instance.connectedSession.value)
    }

    private var version: FirmwareManager.FirmwareInfo? = null
    private lateinit var markwon: Markwon

    private var updateService: FirmwareUpdateService? = null
    private var pendingUpdate: Pair<Glasses, String>? = null
    private var serviceConnected = false
    private var isCancelUpdate = false
    private val animationDuration = 600L

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!isAdded || view == null) return

            serviceConnected = true
            val binder = service as FirmwareUpdateService.LocalBinder
            updateService = binder.getService()

            // Start pending update if exists
            pendingUpdate?.let { (glasses, path) ->
                updateService?.startUpdate(glasses, path)
                pendingUpdate = null
            }

            bindUpdateService()

            updateService?.let { srv ->
                val currentState = srv.state.value
                val currentProgress = srv.progress.value

                when (currentState) {
                    is FirmwareUpdateService.UpdateState.Updating -> {
                        LoadingDialogFragment.dismiss(requireActivity())
                        binding.infoLayout.animate().alpha(1f).start()
                        binding.updateButton.isEnabled = false
                        binding.updateCornerView.progress = currentProgress

                        val percentage = currentProgress * 100
                        binding.updateButton.text = getString(R.string.check_update_updating).format(percentage)

                        binding.cancelLayout.visibility = View.VISIBLE
                    }
                    else -> {
                        LoadingDialogFragment.show(requireActivity())
                        viewModel.checkForUpdate()
                    }
                }

                handleOTAState(currentState)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceConnected = false
            updateService = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentCheckUpdateBinding.inflate(inflater, container, false)

        markwon = Markwon.create(requireContext())


        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.updateButton.setOnClickListener {
            when (viewModel.state.value) {
                is FirmwareManager.DownloadState.DownloadAvailable -> {
                    viewModel.downloadFirmware()
                }
                is FirmwareManager.DownloadState.DownloadCompleted -> {
                    startFirmwareUpdate()
                    binding.cancelLayout.visibility = View.VISIBLE
                }
                else -> {}
            }
        }

        binding.cancelButton.setOnClickListener {
            isCancelUpdate = true
            updateService?.cancelUpdate()
            binding.cancelLayout.visibility = View.GONE

            binding.updateCornerView.fillColor = requireContext().resolveAttrColor(R.attr.appPrimary)
            binding.updateButton.text = getString(R.string.check_update_update_now)
            binding.updateCornerView.progress = 0f
            binding.updateButton.isEnabled = true
        }

        binding.completionLayout.alpha = 0f
        binding.infoLayout.alpha = 0f
        binding.cancelLayout.visibility = View.GONE
        bind()

        binding.updateCornerView.fillColor = requireContext().resolveAttrColor(R.attr.appPrimaryDark)
        binding.updateCornerView.progressColor = requireContext().resolveAttrColor(R.attr.appPrimary)
        binding.updateCornerView.progress = 1f

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), FirmwareUpdateService::class.java)
        requireContext().bindService(intent, serviceConnection, 0)

        binding.root.postDelayed({
            if (!serviceConnected) {
                LoadingDialogFragment.show(requireActivity())
                viewModel.checkForUpdate()
            }
        }, 300)
    }

    private fun bind() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.firmwareInfo.collect { version ->
                        version ?: return@collect
                        this@CheckUpdateFragment.version = version
                        binding.versionTextView.text = "V" + version.newestVersion
                        binding.completionVersionTextView.text = "V" + version.newestVersion
                        markwon.setMarkdown(binding.descriptionTextView, version.updateDetails)
                    }
                }

                launch {
                    viewModel.downloadProgress.collect { progress ->
                        if (viewModel.state.value is FirmwareManager.DownloadState.Downloading) {
                            val percentage = progress * 100
                            binding.updateButton.text = getString(R.string.check_update_downloading).format(percentage)
                            binding.updateCornerView.progress = progress
                        }
                    }
                }

                launch {
                    viewModel.state.collect {
                        updateUIForState(it)
                    }
                }
            }
        }
    }

    private fun updateUIForState(state: FirmwareManager.DownloadState) {
        when (state) {
            is FirmwareManager.DownloadState.Idle -> {

            }
            is FirmwareManager.DownloadState.CheckingVersion -> {

            }
            is FirmwareManager.DownloadState.DownloadAvailable -> {
                LoadingDialogFragment.dismiss(requireActivity())
                binding.infoLayout.animate().alpha(1f).start()
                val text = getString(R.string.check_update_download)
                binding.updateButton.text = text
            }
            is FirmwareManager.DownloadState.Downloading -> {
                binding.updateButton.isEnabled = false
                binding.updateCornerView.progress = 0f
            }

            is FirmwareManager.DownloadState.DownloadCompleted -> {
                LoadingDialogFragment.dismiss(requireActivity())
                binding.updateButton.isEnabled = true
                binding.infoLayout.animate().alpha(1f).start()
                binding.updateButton.text = getString(R.string.check_update_update_now)
                binding.updateCornerView.progress = 1f
            }
            is FirmwareManager.DownloadState.CheckFailed -> {
                LoadingDialogFragment.dismiss(requireActivity())
                binding.completionTextView.text = state.error
                binding.completionImageView.setImageResource(R.drawable.ic_check_update_failed)
                binding.completionLayout.animate().alpha(1f).start()
            }
            is FirmwareManager.DownloadState.DownloadFailed -> {
                LoadingDialogFragment.dismiss(requireActivity())
                binding.completionTextView.text = state.error
                binding.completionImageView.setImageResource(R.drawable.ic_check_update_failed)
                binding.completionLayout.animate().alpha(1f).start()
                binding.infoLayout.animate().alpha(0f).start()
                binding.updateCornerView.progress = 1f
                binding.updateButton.text = getString(R.string.check_update_download)
            }

            is FirmwareManager.DownloadState.NoDownload -> {
                LoadingDialogFragment.dismiss(requireActivity())
                binding.completionVersionTextView.text = viewModel.getVersionDisplayText()
                binding.completionImageView.setImageResource(R.drawable.ic_check_update_completion)
                binding.completionLayout.animate().alpha(1f).start()
            }
        }
    }

    private fun startFirmwareUpdate() {
        val glasses = BLEService.instance.connectedSession.value ?: return
        val firmwarePath = FirmwareManager.getLocalFirmwarePath()?.absolutePath ?: return

        pendingUpdate = Pair(glasses, firmwarePath)

        val intent = Intent(requireContext(), FirmwareUpdateService::class.java)
        requireContext().startForegroundService(intent)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun bindUpdateService() {
        if (!isAdded || view == null) return
        val service = updateService ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    service.state.collect { state ->
                        handleOTAState(state)
                    }
                }

                launch {
                    service.progress.collect { progress ->
                        if (isCancelUpdate)
                            return@collect
                        binding.updateCornerView.progress = progress
                        val percentage = progress * 100
                        binding.updateButton.text = getString(R.string.check_update_updating).format(percentage)
                    }
                }
            }
        }
    }

    private fun handleOTAState(state: FirmwareUpdateService.UpdateState) {
        when (state) {
            is FirmwareUpdateService.UpdateState.Idle -> {
                // Do nothing
            }
            is FirmwareUpdateService.UpdateState.Updating -> {
                binding.updateButton.isEnabled = false
            }
            is FirmwareUpdateService.UpdateState.Completed -> {
                binding.updateButton.isEnabled = true
                binding.completionLayout.animate().alpha(1f).start()
                binding.infoLayout.animate().alpha(0f).start()
                binding.completionVersionTextView.visibility = View.VISIBLE
                binding.cancelLayout.visibility = View.GONE
            }
            is FirmwareUpdateService.UpdateState.Failed -> {
                binding.updateButton.isEnabled = true
                binding.completionTextView.text = getString(R.string.check_update_update_failed)
                binding.completionImageView.setImageResource(R.drawable.ic_check_update_failed)
                binding.completionLayout.animate().alpha(1f).start()
                binding.infoLayout.animate().alpha(0f).start()
                binding.cancelLayout.visibility = View.GONE
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (serviceConnected) {
            try {
                requireContext().unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e("CheckUpdateFragment", "Error unbinding service: ${e.message}")
            }
        }
        serviceConnected = false
        updateService = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (updateService?.state?.value !is FirmwareUpdateService.UpdateState.Updating) {
            FirmwareManager.reset()
        }
    }
}

class CheckUpdateViewModelFactory(private val glasses: com.konami.ailens.ble.Glasses?) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CheckUpdateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CheckUpdateViewModel(glasses) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}