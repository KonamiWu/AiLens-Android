package com.konami.ailens.login

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
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
import androidx.navigation.fragment.findNavController
import com.konami.ailens.MainActivity
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentLoginBinding
import com.konami.ailens.device.AddDeviceActivity
import com.konami.ailens.resolveAttrColor
import com.konami.ailens.signup.SignUpActivity
import com.konami.ailens.ui.Alert
import com.konami.ailens.ui.LoadingDialogFragment
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private val viewModel: LoginViewModel by viewModels()
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private var hasAttemptedLogin = false
    private var isPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        updateLoginButton()
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
        binding.emailEditText.backgroundTintList = colorStateList
        binding.passwordEditText.backgroundTintList = colorStateList

        binding.emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                emailDidChange()
            }
        })

        binding.passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateLoginButton()
            }
        })

        binding.emailEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.passwordEditText.requestFocus()
                true
            } else {
                false
            }
        }

        binding.passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                loginAction()
                true
            } else {
                false
            }
        }

        binding.loginButton.setOnClickListener {
            loginAction()
        }

        binding.passwordEyeButton.setOnClickListener {
            passwordEyeAction()
        }

        binding.signUpTextView.setOnClickListener {
            signUpAction()
        }

        binding.forgetPasswordTextView.setOnClickListener {
            forgetPasswordAction()
        }
    }

    private fun bind() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.showAddDeviceEvent.collect {
                        LoadingDialogFragment.dismiss(requireActivity())
                        val intent = Intent(requireActivity(), AddDeviceActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        val options = android.app.ActivityOptions.makeCustomAnimation(
                            requireContext(),
                            R.anim.flip_in,
                            R.anim.flip_out
                        )

                        startActivity(intent, options.toBundle())
                        requireActivity().finish()
                    }
                }

                launch {
                    viewModel.loginFailedEvent.collect { message ->
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
        if (hasAttemptedLogin) {
            val email = binding.emailEditText.text.toString()

            if (email.isEmpty()) {
                showEmailInvalid()
            } else if (viewModel.isValidEmail(email)) {
                showEmailValid()
            } else {
                showEmailInvalid()
            }
        }

        updateLoginButton()
    }

    private fun loginAction() {
        hasAttemptedLogin = true

        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()

        if (email.isEmpty()) {
            showEmailInvalid()
            return
        }

        if (!viewModel.isValidEmail(email)) {
            showEmailInvalid()
            return
        }

        if (password.isEmpty()) return

        hideKeyboard()
        LoadingDialogFragment.show(requireActivity())
        viewModel.login(email, password)
    }

    private fun signUpAction() {
        val intent = Intent(requireActivity(), SignUpActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val options = android.app.ActivityOptions.makeCustomAnimation(
            requireContext(),
            R.anim.flip_in,
            R.anim.flip_out
        )

        startActivity(intent, options.toBundle())
        requireActivity().finish()
    }

    private fun forgetPasswordAction() {
        val intent = Intent(requireActivity(), ForgetPasswordActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val options = android.app.ActivityOptions.makeCustomAnimation(
            requireContext(),
            R.anim.flip_in,
            R.anim.flip_out
        )

        startActivity(intent, options.toBundle())
        requireActivity().finish()
    }

    private fun showEmailInvalid() {
        val redColor = requireContext().resolveAttrColor(R.attr.appRed)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(redColor)
        )
        binding.emailEditText.backgroundTintList = colorStateList

        binding.emailErrorLabel.animate()
            .alpha(1f)
            .setDuration(250)
            .start()
    }

    private fun showEmailValid() {
        val grayColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf()),
            intArrayOf(grayColor)
        )
        binding.emailEditText.backgroundTintList = colorStateList

        binding.emailErrorLabel.animate()
            .alpha(0f)
            .setDuration(250)
            .start()
    }

    private fun updateLoginButton() {
        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()

        val shouldEnable = email.isNotEmpty() && password.isNotEmpty() && viewModel.isValidEmail(email)
        setLoginButton(shouldEnable)
    }

    private fun setLoginButton(enabled: Boolean) {
        binding.loginButton.isEnabled = enabled

        binding.buttonLayout.fillColor = if (enabled) {
            requireContext().resolveAttrColor(R.attr.appPrimary)
        } else {
            requireContext().resolveAttrColor(R.attr.appButtonDisable)
        }

        binding.loginButton.setTextColor(
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
