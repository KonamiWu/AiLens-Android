package com.konami.ailens.facedetection


import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.graphics.scale
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.konami.ailens.SharedViewModel
import com.konami.ailens.databinding.FragmentFaceDetectionBinding
import kotlinx.coroutines.Dispatchers

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FaceDetectionFragment : Fragment() {
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val viewModel: FaceDetectionViewModel by viewModels()
    private var _binding: FragmentFaceDetectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FaceOverlayView

    private lateinit var faceAnalyzer: FaceAnalyzer
    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
//    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFaceDetectionBinding.inflate(inflater, container, false)

        binding.resetCenterButton.setOnClickListener {
            viewModel.resetCenter()
        }

        binding.recalibrateButton.setOnClickListener {
            viewModel.recalibrate()
        }

        previewView = binding.previewView
        overlayView = binding.overlayView
        faceAnalyzer = FaceAnalyzer(binding.previewView, binding.overlayView, requireContext(), viewModel.repo, useFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.readyFlow.collect {
                    if (it)
                        startCamera()
                }
            }
        }

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    faceAnalyzer.faceResultsFlow.collect {

                        overlayView.updateResult(it)
                        viewModel.transformMatrix = overlayView.transformMatrix
                        viewModel.updateFaceResult(it)
                    }
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }


            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(
                        ContextCompat.getMainExecutor(requireContext()),
                        faceAnalyzer
                    )
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, analysis)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}