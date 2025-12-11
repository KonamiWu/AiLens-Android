package com.konami.ailens.signup

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentSendOtpBinding
import com.konami.ailens.login.LoginActivity
import com.konami.ailens.resolveAttrColor
import com.konami.ailens.ui.Alert
import com.konami.ailens.ui.LoadingDialogFragment
import kotlinx.coroutines.launch

class SendOTPFragment : Fragment() {
    private val viewModel: SendOTPViewModel by viewModels()
    private var _binding: FragmentSendOtpBinding? = null
    private val binding get() = _binding!!

    private var hasAttemptedNext = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSendOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        updateNextButton()
        bind()
    }

    private fun setupUI() {
        binding.root.setOnClickListener {
            hideKeyboard()
        }

        binding.invalidTextView.visibility = View.INVISIBLE

        val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(grayColor)
        )
        binding.emailEditText.backgroundTintList = colorStateList

        binding.emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                emailDidChange()
            }
        })

        binding.emailEditText.setOnEditorActionListener { _, actionId, _ ->
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

        binding.loginTextView.setOnClickListener {
            loginAction()
        }
    }

    private fun bind() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.verificationEmailSuccessEvent.collect {
                        LoadingDialogFragment.dismiss(requireActivity())
                        val email = binding.emailEditText.text.toString()
                        val bundle = Bundle().apply {
                            putString("email", email)
                        }
                        findNavController().navigate(R.id.action_SendOTPFragment_to_VerifyOTPFragment, bundle)
                    }
                }

                launch {
                    viewModel.verificationEmailFailedEvent.collect { message ->
                        LoadingDialogFragment.dismiss(requireActivity())
                        Alert.newAlert(
                            activity = requireActivity(),
                            title = requireContext().getString(R.string.error),
                            message = message,
                            positiveTitle = getString(R.string.confirm)
                        ).show()
                    }
                }
            }
        }
    }

    private fun emailDidChange() {
        if (hasAttemptedNext) {
            val isValid = validateEmail()
            if (isValid) {
                showEmailValid()
            } else {
                showEmailInvalid()
            }
        }
        updateNextButton()
    }

    private fun nextAction() {
        hasAttemptedNext = true

        val isValid = validateEmail()

        if (isValid) {
            LoadingDialogFragment.show(requireActivity())
            viewModel.sendVerificationEmail(binding.emailEditText.text.toString())
        } else {
            showEmailInvalid()
        }

        updateNextButton()
    }

    private fun loginAction() {
        val intent = Intent(requireActivity(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val options = android.app.ActivityOptions.makeCustomAnimation(
            requireContext(),
            R.anim.flip_in,
            R.anim.flip_out
        )

        startActivity(intent, options.toBundle())
    }

    private fun validateEmail(): Boolean {
        val email = binding.emailEditText.text.toString()
        return viewModel.isValidEmail(email)
    }

    private fun isEmailFilled(): Boolean {
        val email = binding.emailEditText.text.toString()
        return email.isNotEmpty()
    }

    private fun updateNextButton() {
        val shouldEnable = if (hasAttemptedNext) {
            validateEmail()
        } else {
            isEmailFilled()
        }

        setNextButton(shouldEnable)
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

    private fun showEmailInvalid() {
        binding.invalidTextView.visibility = View.VISIBLE
        val redColor = requireContext().resolveAttrColor(R.attr.appRed)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(redColor)
        )
        binding.emailEditText.backgroundTintList = colorStateList
    }

    private fun showEmailValid() {
        binding.invalidTextView.visibility = View.INVISIBLE
        val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(grayColor)
        )
        binding.emailEditText.backgroundTintList = colorStateList
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        LoadingDialogFragment.dismiss(requireActivity())
        _binding = null
    }
}
