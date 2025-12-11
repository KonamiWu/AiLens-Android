package com.konami.ailens.setting

import android.os.Bundle
import android.util.Log
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
import com.konami.ailens.R
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.FirmwareManager
import com.konami.ailens.databinding.FragmentCheckUpdateBinding
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentCheckUpdateBinding.inflate(inflater)

        markwon = Markwon.create(requireContext())

        bind()
        setupClickListeners()

        viewModel.checkForUpdate()

        binding.completionLayout.alpha = 0f
        binding.infoLayout.alpha = 0f

        LoadingDialogFragment.show(requireActivity())
        return binding.root
    }

    private fun bind() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.firmwareInfo.collect { version ->
                        this@CheckUpdateFragment.version = version
                        binding.versionTextView.text = "V" + version.newestVersion
                        binding.completionVersionTextView.text = "V" + version.newestVersion
                        markwon.setMarkdown(binding.descriptionTextView, version.updateDetails)
                    }
                }

                launch {
                    viewModel.downloadProgress.collect { progress ->
                        if (viewModel.state.value is FirmwareManager.UpdateState.Downloading) {
                            val percentage = (progress * 100).toInt()
                            val text = requireContext().getString(R.string.check_update_downloading)
                            binding.updateButton.text = "$text $percentage%"
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

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.updateButton.setOnClickListener {
            when (viewModel.state.value) {
                is FirmwareManager.UpdateState.UpdateAvailable -> {
                    viewModel.downloadFirmware()
                }
                is FirmwareManager.UpdateState.DownloadCompleted -> {

                }
                else -> {}
            }
        }
    }

    private fun updateDescription(details: List<String>) {
        if (details.isEmpty()) {
            binding.descriptionTextView.text = viewModel.getUpdateTitleText()
        } else {
            val title = viewModel.getUpdateTitleText()
            val detailsText = details.joinToString("\n") { "• $it" }
            binding.descriptionTextView.text = "$title\n$detailsText"
        }
    }

    private fun updateUIForState(state: FirmwareManager.UpdateState) {
        when (state) {
            is FirmwareManager.UpdateState.Idle -> {

            }
            is FirmwareManager.UpdateState.CheckingVersion -> {

            }
            is FirmwareManager.UpdateState.UpdateAvailable -> {
                LoadingDialogFragment.dismiss(requireActivity())
                binding.infoLayout.animate().alpha(1f).start()
            }
            is FirmwareManager.UpdateState.Downloading -> {
                binding.updateButton.isEnabled = false

            }
            is FirmwareManager.UpdateState.DownloadCompleted -> {
                binding.updateButton.isEnabled = true
                val text = requireContext().getString(R.string.check_update_update_now)
                binding.updateButton.text = text
            }
            is FirmwareManager.UpdateState.CheckFailed -> {
                LoadingDialogFragment.dismiss(requireActivity())
                binding.completionTextView.text = state.error
                binding.completionImageView.setImageResource(R.drawable.ic_check_update_failed)
                binding.completionLayout.animate().alpha(1f).start()
            }
            is FirmwareManager.UpdateState.DownloadFailed -> {
                LoadingDialogFragment.dismiss(requireActivity())
                binding.completionTextView.text = state.error
                binding.completionImageView.setImageResource(R.drawable.ic_check_update_failed)
                binding.completionLayout.animate().alpha(1f).start()
            }

            is FirmwareManager.UpdateState.NoUpdate -> {
                LoadingDialogFragment.dismiss(requireActivity())
                binding.completionImageView.setImageResource(R.drawable.ic_check_update_completion)
                binding.completionLayout.animate().alpha(1f).start()
            }
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