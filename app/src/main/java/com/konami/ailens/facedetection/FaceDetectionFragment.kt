package com.konami.ailens.facedetection


import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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

//        viewLifecycleOwner.lifecycleScope.launch {
//            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                viewModel.imageFlow.collect {
//                    binding.imageView.setImageBitmap(it)
//                }
//            }
//        }

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

                        if (it.isNotEmpty()) {
                            val image = it.first().face112
                            binding.imageView.setImageBitmap(image)
                        }
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

            val resSelector = ResolutionSelector.Builder()
                // 先指定偏好的長寬比（16:9 或 4:3；臉部多半 16:9 比較友善）
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        AspectRatio.RATIO_4_3,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO
                    )
                )
                // 再指定偏好的解析度與回退規則（先找 >= 1280x720，找不到就找最接近）
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()


            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .setResolutionSelector(resSelector)
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