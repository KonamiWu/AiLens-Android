package com.konami.ailens.login

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentResetPasswordBinding
import com.konami.ailens.resolveAttrColor
import com.konami.ailens.ui.Alert
import com.konami.ailens.ui.LoadingDialogFragment
import com.konami.ailens.ui.Toast
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {
    private val viewModel: ResetPasswordViewModel by viewModels()
    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private var hasAttemptedReset = false
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        updateConfirmButton()
        bind()
    }

    private fun setupUI() {
        binding.root.setOnClickListener {
            hideKeyboard()
        }

        val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(grayColor)
        )
        binding.passwordEditText.backgroundTintList = colorStateList
        binding.confirmPasswordEditText.backgroundTintList = colorStateList

        binding.passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                textFieldDidChange()
            }
        })

        binding.confirmPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                textFieldDidChange()
            }
        })

        binding.passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.confirmPasswordEditText.requestFocus()
                true
            } else {
                false
            }
        }

        binding.confirmPasswordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmAction()
                true
            } else {
                false
            }
        }

        binding.confirmButton.setOnClickListener {
            confirmAction()
        }

        binding.passwordEyeButton.setOnClickListener {
            passwordEyeAction()
        }

        binding.confirmPasswordEyeButton.setOnClickListener {
            confirmPasswordEyeAction()
        }
    }

    private fun bind() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.updatePasswordSuccessEvent.collect {
                        LoadingDialogFragment.dismiss(requireActivity())
                        Toast.show(requireActivity(), requireContext().getString(R.string.reset_password_successfully))

                        val intent = Intent(requireActivity(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        val options = android.app.ActivityOptions.makeCustomAnimation(
                            requireContext(),
                            R.anim.flip_in,
                            R.anim.flip_out
                        )

                        startActivity(intent, options.toBundle())
                    }
                }

                launch {
                    viewModel.updatePasswordFailureEvent.collect { message ->
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

    private fun textFieldDidChange() {
        if (hasAttemptedReset) {
            val validation = validateAllFields()
            updateUIForValidation(validation)
        }
        updateConfirmButton()
    }

    private fun confirmAction() {
        hasAttemptedReset = true

        val validation = validateAllFields()
        if (validation.isValid) {
            hideKeyboard()
            LoadingDialogFragment.show(requireActivity())
            viewModel.updatePassword(binding.passwordEditText.text.toString())
        } else {
            updateUIForValidation(validation)
            updateConfirmButton()
        }
    }

    private data class ValidationResult(
        val isPasswordValid: Boolean,
        val isConfirmPasswordValid: Boolean
    ) {
        val isValid: Boolean
            get() = isPasswordValid && isConfirmPasswordValid
    }

    private fun validateAllFields(): ValidationResult {
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        val isPasswordValid = password.length >= 8
        val isConfirmPasswordValid = password == confirmPassword && password.isNotEmpty()

        return ValidationResult(
            isPasswordValid = isPasswordValid,
            isConfirmPasswordValid = isConfirmPasswordValid
        )
    }

    private fun areAllFieldsFilled(): Boolean {
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        return password.isNotEmpty() && confirmPassword.isNotEmpty()
    }

    private fun updateConfirmButton() {
        val shouldEnable = if (hasAttemptedReset) {
            validateAllFields().isValid
        } else {
            areAllFieldsFilled()
        }

        setConfirmButton(shouldEnable)
    }

    private fun updateUIForValidation(validation: ValidationResult) {
        if (validation.isPasswordValid) {
            showPasswordValid()
        } else {
            showPasswordInvalid()
        }

        if (validation.isConfirmPasswordValid) {
            showConfirmPasswordValid()
        } else {
            showConfirmPasswordInvalid()
        }
    }

    private fun showPasswordInvalid() {
        val redColor = requireContext().resolveAttrColor(R.attr.appRed)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(redColor)
        )
        binding.passwordEditText.backgroundTintList = colorStateList

        binding.passwordErrorLabel.animate()
            .alpha(1f)
            .setDuration(250)
            .start()
    }

    private fun showPasswordValid() {
        val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(grayColor)
        )
        binding.passwordEditText.backgroundTintList = colorStateList

        binding.passwordErrorLabel.animate()
            .alpha(0f)
            .setDuration(250)
            .start()
    }

    private fun showConfirmPasswordInvalid() {
        val redColor = requireContext().resolveAttrColor(R.attr.appRed)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(redColor)
        )
        binding.confirmPasswordEditText.backgroundTintList = colorStateList

        binding.confirmPasswordErrorLabel.animate()
            .alpha(1f)
            .setDuration(250)
            .start()
    }

    private fun showConfirmPasswordValid() {
        val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(grayColor)
        )
        binding.confirmPasswordEditText.backgroundTintList = colorStateList

        binding.confirmPasswordErrorLabel.animate()
            .alpha(0f)
            .setDuration(250)
            .start()
    }

    private fun setConfirmButton(enabled: Boolean) {
        binding.confirmButton.isEnabled = enabled

        binding.buttonLayout.fillColor = if (enabled) {
            requireContext().resolveAttrColor(R.attr.appPrimary)
        } else {
            requireContext().resolveAttrColor(R.attr.appButtonDisable)
        }

        binding.confirmButton.setTextColor(
            if (enabled) {
                requireContext().resolveAttrColor(R.attr.appTextButton)
            } else {
                requireContext().resolveAttrColor(R.attr.appTextDisable)
            }
        )
    }

    private fun passwordEyeAction() {
        isPasswordVisible = !isPasswordVisible
        if (isPasswordVisible) {
            binding.passwordEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            binding.passwordEyeButton.setImageResource(R.drawable.ic_eye_open)
        } else {
            binding.passwordEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            binding.passwordEyeButton.setImageResource(R.drawable.ic_eye_close)
        }
        binding.passwordEditText.setSelection(binding.passwordEditText.text?.length ?: 0)
    }

    private fun confirmPasswordEyeAction() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible
        if (isConfirmPasswordVisible) {
            binding.confirmPasswordEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            binding.confirmPasswordEyeButton.setImageResource(R.drawable.ic_eye_open)
        } else {
            binding.confirmPasswordEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            binding.confirmPasswordEyeButton.setImageResource(R.drawable.ic_eye_close)
        }
        binding.confirmPasswordEditText.setSelection(binding.confirmPasswordEditText.text?.length ?: 0)
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
