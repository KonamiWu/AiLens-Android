package com.konami.ailens.signup

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
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
import com.konami.ailens.databinding.FragmentSignUpBinding
import com.konami.ailens.resolveAttrColor
import com.konami.ailens.ui.Alert
import com.konami.ailens.ui.LoadingDialogFragment
import com.konami.ailens.ui.Toast
import kotlinx.coroutines.launch

class SignUpFragment : Fragment() {

    private val viewModel: SignUpViewModel by viewModels()
    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    private var hasAttemptedSignUp = false
    private lateinit var email: String

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    private var displayNameTintAnimator: ValueAnimator? = null
    private var passwordTintAnimator: ValueAnimator? = null
    private var confirmPasswordTintAnimator: ValueAnimator? = null

    private var isDisplayNameValid = true
    private var isPasswordValid = true
    private var isConfirmPasswordValid = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        email = arguments?.getString("email") ?: ""

        setupUI()
        updateSignUpButton()
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
        binding.displayNameEditText.backgroundTintList = colorStateList
        binding.passwordEditText.backgroundTintList = colorStateList
        binding.confirmPasswordEditText.backgroundTintList = colorStateList

        binding.displayNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                textFieldDidChange()
            }
        })

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

        binding.displayNameEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.passwordEditText.requestFocus()
                true
            } else {
                false
            }
        }

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
                signUpAction()
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.signUpButton.setOnClickListener {
            signUpAction()
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
                    viewModel.signUpSuccessEvent.collect {
                        LoadingDialogFragment.dismiss(requireActivity())
                        Toast.show(requireActivity(), requireContext().getString(R.string.signed_up_successfully))
                    }
                }

                launch {
                    viewModel.signUpFailedEvent.collect { message ->
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

    private fun signUpAction() {
        hasAttemptedSignUp = true

        val validation = validateAllFields()
        if (validation.isValid) {
            hideKeyboard()
            LoadingDialogFragment.show(requireActivity())
            viewModel.signUp(
                email = email,
                password = binding.passwordEditText.text.toString(),
                displayName = binding.displayNameEditText.text.toString()
            )
        } else {
            updateUIForValidation(validation)
        }

        updateSignUpButton()
    }

    private fun textFieldDidChange() {
        if (hasAttemptedSignUp) {
            val validation = validateAllFields()
            updateUIForValidation(validation)
        }
        updateSignUpButton()
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
        // Move cursor to end
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
        // Move cursor to end
        binding.confirmPasswordEditText.setSelection(binding.confirmPasswordEditText.text?.length ?: 0)
    }

    private data class ValidationResult(
        val isDisplayNameValid: Boolean,
        val isPasswordValid: Boolean,
        val isConfirmPasswordValid: Boolean
    ) {
        val isValid: Boolean
            get() = isDisplayNameValid && isPasswordValid && isConfirmPasswordValid
    }

    private fun validateAllFields(): ValidationResult {
        val displayName = binding.displayNameEditText.text.toString()
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        val isDisplayNameValid = displayName.isNotEmpty()
        val isPasswordValid = password.length >= 8
        val isConfirmPasswordValid = password == confirmPassword && password.isNotEmpty()

        return ValidationResult(
            isDisplayNameValid = isDisplayNameValid,
            isPasswordValid = isPasswordValid,
            isConfirmPasswordValid = isConfirmPasswordValid
        )
    }

    private fun areAllFieldsFilled(): Boolean {
        val displayName = binding.displayNameEditText.text.toString()
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        return displayName.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()
    }

    private fun updateSignUpButton() {
        val shouldEnable = if (hasAttemptedSignUp) {
            validateAllFields().isValid
        } else {
            areAllFieldsFilled()
        }
        binding.signUpButton.isEnabled = shouldEnable
    }

    private fun updateUIForValidation(validation: ValidationResult) {
        if (validation.isDisplayNameValid) {
            showDisplayNameValid()
        } else {
            showDisplayNameInvalid()
        }

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

    private fun showDisplayNameInvalid() {
        if (isDisplayNameValid) {
            isDisplayNameValid = false
            val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
            val redColor = requireContext().resolveAttrColor(R.attr.appRed)
            animateTintColor(displayNameTintAnimator, grayColor, redColor) { animator ->
                displayNameTintAnimator = animator
            }

            binding.displayNameErrorLabel.animate()
                .alpha(1f)
                .setDuration(250)
                .start()
        }
    }

    private fun showDisplayNameValid() {
        if (!isDisplayNameValid) {
            isDisplayNameValid = true
            val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
            val redColor = requireContext().resolveAttrColor(R.attr.appRed)
            animateTintColor(displayNameTintAnimator, redColor, grayColor) { animator ->
                displayNameTintAnimator = animator
            }

            binding.displayNameErrorLabel.animate()
                .alpha(0f)
                .setDuration(250)
                .start()
        }
    }

    private fun showPasswordInvalid() {
        if (isPasswordValid) {
            isPasswordValid = false
            val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
            val redColor = requireContext().resolveAttrColor(R.attr.appRed)
            animateTintColor(passwordTintAnimator, grayColor, redColor, binding.passwordEditText) { animator ->
                passwordTintAnimator = animator
            }

            binding.passwordErrorLabel.animate()
                .alpha(1f)
                .setDuration(250)
                .start()
        }
    }

    private fun showPasswordValid() {
        if (!isPasswordValid) {
            isPasswordValid = true
            val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
            val redColor = requireContext().resolveAttrColor(R.attr.appRed)
            animateTintColor(passwordTintAnimator, redColor, grayColor, binding.passwordEditText) { animator ->
                passwordTintAnimator = animator
            }

            binding.passwordErrorLabel.animate()
                .alpha(0f)
                .setDuration(250)
                .start()
        }
    }

    private fun showConfirmPasswordInvalid() {
        if (isConfirmPasswordValid) {
            isConfirmPasswordValid = false
            val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
            val redColor = requireContext().resolveAttrColor(R.attr.appRed)
            animateTintColor(confirmPasswordTintAnimator, grayColor, redColor, binding.confirmPasswordEditText) { animator ->
                confirmPasswordTintAnimator = animator
            }

            binding.confirmPasswordErrorLabel.animate()
                .alpha(1f)
                .setDuration(250)
                .start()
        }
    }

    private fun showConfirmPasswordValid() {
        if (!isConfirmPasswordValid) {
            isConfirmPasswordValid = true
            val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
            val redColor = requireContext().resolveAttrColor(R.attr.appRed)
            animateTintColor(confirmPasswordTintAnimator, redColor, grayColor, binding.confirmPasswordEditText) { animator ->
                confirmPasswordTintAnimator = animator
            }

            binding.confirmPasswordErrorLabel.animate()
                .alpha(0f)
                .setDuration(250)
                .start()
        }
    }

    private fun animateTintColor(
        currentAnimator: ValueAnimator?,
        fromColor: Int,
        toColor: Int,
        editText: android.widget.EditText = binding.displayNameEditText,
        onAnimatorCreated: (ValueAnimator) -> Unit
    ) {
        currentAnimator?.cancel()

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250L
            val colorEvaluator = ArgbEvaluator()

            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val color = colorEvaluator.evaluate(fraction, fromColor, toColor) as Int
                val colorStateList = ColorStateList(
                    arrayOf(intArrayOf()),
                    intArrayOf(color)
                )
                editText.backgroundTintList = colorStateList
            }

            start()
        }

        onAnimatorCreated(animator)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        displayNameTintAnimator?.cancel()
        passwordTintAnimator?.cancel()
        confirmPasswordTintAnimator?.cancel()
        LoadingDialogFragment.dismiss(requireActivity())
        _binding = null
    }
}
