package com.konami.ailens.signup

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentVerifyOtpBinding
import com.konami.ailens.resolveAttrColor
import com.konami.ailens.ui.Alert
import com.konami.ailens.ui.LoadingDialogFragment
import kotlinx.coroutines.launch

class VerifyOTPFragment : Fragment() {
    enum class OTPContext {
        SIGN,
        RESET
    }

    private val viewModel: VerifyOTPViewModel by viewModels()
    private var _binding: FragmentVerifyOtpBinding? = null
    private val binding get() = _binding!!

    private var countDownTimer: CountDownTimer? = null
    private val resentTimeInterval = 40000L // 40 seconds in milliseconds

    private lateinit var email: String
    var context: OTPContext = OTPContext.SIGN

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerifyOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get email from arguments
        email = arguments?.getString("email") ?: ""

        // Get context from arguments
        val contextString = arguments?.getString("context") ?: "SIGN"
        context = when (contextString) {
            "RESET" -> OTPContext.RESET
            else -> OTPContext.SIGN
        }

        setupUI()
        updateNextButton()
        bind()
        startCountDown()
    }

    private fun setupUI() {
        binding.root.setOnClickListener {
            hideKeyboard()
        }

        binding.emailTextView.text = email

        binding.getOTPTextView.visibility = View.GONE
        binding.getOTPTextView.isEnabled = false

        val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val colorStateList = android.content.res.ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(grayColor)
        )
        binding.otpEditText.backgroundTintList = colorStateList

        binding.otpEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                otpDidChange()
            }
        })

        binding.otpEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                nextAction()
                true
            } else {
                false
            }
        }

        binding.nextButton.setOnClickListener {
            nextAction()
        }

        binding.getOTPTextView.setOnClickListener {
            getOTPAction()
        }
    }

    private fun bind() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.verifyOTPSuccessEvent.collect {
                        LoadingDialogFragment.dismiss(requireActivity())
                        navigateNext()
                    }
                }

                launch {
                    viewModel.verifyOTPFailureEvent.collect { message ->
                        LoadingDialogFragment.dismiss(requireActivity())
                        Alert.newAlert(requireActivity(), requireContext().getString(R.string.error), message, requireContext().getString(R.string.confirm)).show()
                    }
                }

                launch {
                    viewModel.verificationEmailSuccessEvent.collect {
                        LoadingDialogFragment.dismiss(requireActivity())
                        startCountDown()
                    }
                }

                launch {
                    viewModel.verificationEmailFailedEvent.collect { message ->
                        LoadingDialogFragment.dismiss(requireActivity())
                        Alert.newAlert(requireActivity(), requireContext().getString(R.string.error), message, requireContext().getString(R.string.confirm)).show()
                    }
                }
            }
        }
    }

    private fun otpDidChange() {
        updateNextButton()
    }

    private fun nextAction() {
        val otp = binding.otpEditText.text.toString()

        if (otp.isEmpty()) return

        hideKeyboard()
        LoadingDialogFragment.show(requireActivity())
        viewModel.verifyOTP(email, otp)
    }

    private fun getOTPAction() {
        LoadingDialogFragment.show(requireActivity())
        viewModel.sendVerificationEmail(email)
    }

    private fun isOTPFilled(): Boolean {
        val otp = binding.otpEditText.text.toString()
        return otp.isNotEmpty()
    }

    private fun updateNextButton() {
        setNextButton(isOTPFilled())
    }

    private fun setNextButton(enabled: Boolean) {
        binding.nextButton.isEnabled = enabled

        binding.buttonLayout.fillColor = if (enabled) {
            requireContext().resolveAttrColor(R.attr.appPrimary)
        } else {
            requireContext().resolveAttrColor(R.attr.appButtonDisable)
        }

        binding.nextButton.setTextColor(
            if (enabled) {
                requireContext().resolveAttrColor(R.attr.appTextButton)
            } else {
                requireContext().resolveAttrColor(R.attr.appTextDisable)
            }
        )
    }

    private fun startCountDown() {
        countDownTimer?.cancel()

        binding.countDownTextView.visibility = View.VISIBLE
        binding.getOTPTextView.visibility = View.GONE
        binding.getOTPTextView.isEnabled = false

        var remainingTime = resentTimeInterval

        countDownTimer = object : CountDownTimer(resentTimeInterval, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTime = millisUntilFinished
                val seconds = (millisUntilFinished / 1000).toInt()
                updateCountDownLabel(seconds)
            }

            override fun onFinish() {
                showGetOTPButton()
            }
        }.start()
    }

    private fun updateCountDownLabel(seconds: Int) {
        binding.countDownTextView.text = String.format("%02ds", seconds)
    }

    private fun showGetOTPButton() {
        binding.countDownTextView.visibility = View.GONE
        binding.getOTPTextView.visibility = View.VISIBLE
        binding.getOTPTextView.isEnabled = true
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }


    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.confirm)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun navigateNext() {
        when (context) {
            OTPContext.SIGN -> {
                val bundle = Bundle()
                bundle.putString("email", email)
                findNavController().navigate(R.id.action_VerifyOTPFragment_to_SignUpFragment, bundle)
            }
            OTPContext.RESET -> {
                Log.e("VerifyOTPFragment", "navigateNext: RESET - email = $email")
                findNavController().navigate(R.id.action_VerifyOTPFragment_to_ResetPasswordFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        LoadingDialogFragment.dismiss(requireActivity())
        _binding = null
    }
}
