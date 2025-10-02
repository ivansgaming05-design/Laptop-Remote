package com.ivans.remotecontrol.dialogs

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.network.ApiClient
import com.ivans.remotecontrol.utils.PreferencesManager
import com.ivans.remotecontrol.utils.SecurePreferencesManager
import com.ivans.remotecontrol.utils.BiometricHelper
import kotlinx.coroutines.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Log

class SecurityDialog : DialogFragment() {

    companion object {
        private const val TAG = "SecurityDialog"
        private var isShowing = false

        fun show(fragmentManager: FragmentManager, onUnlocked: (String) -> Unit) {
            if (isShowing) {
                Log.d(TAG, "Dialog already showing, ignoring request")
                return
            }

            val existingDialog = fragmentManager.findFragmentByTag(TAG) as? SecurityDialog
            existingDialog?.dismissAllowingStateLoss()

            isShowing = true
            val dialog = SecurityDialog()
            dialog.onUnlocked = { sessionToken ->
                isShowing = false
                onUnlocked(sessionToken)
            }
            dialog.show(fragmentManager, TAG)
        }
    }

    private var onUnlocked: ((String) -> Unit)? = null

    private lateinit var passwordInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var unlockButton: Button
    private lateinit var cancelButton: Button
    private lateinit var biometricButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var timeInfoText: TextView
    private lateinit var rememberCheckbox: CheckBox
    private lateinit var biometricCheckbox: CheckBox

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var securePreferencesManager: SecurePreferencesManager // NEW
    private lateinit var biometricHelper: BiometricHelper
    private var currentChallenge: String? = null
    private var unlockJob: Job? = null
    private var isUnlocking = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_security, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferencesManager = PreferencesManager(requireContext())
        securePreferencesManager = SecurePreferencesManager(requireContext()) // NEW
        biometricHelper = BiometricHelper(requireContext())

        initViews(view)
        setupViews()
        checkAuthStatus()
    }

    private fun initViews(view: View) {
        passwordInput = view.findViewById(R.id.editTextPassword)
        passwordLayout = view.findViewById(R.id.passwordLayout)
        unlockButton = view.findViewById(R.id.buttonUnlock)
        cancelButton = view.findViewById(R.id.buttonCancel)
        biometricButton = view.findViewById(R.id.buttonBiometric)
        progressBar = view.findViewById(R.id.progressBar)
        timeInfoText = view.findViewById(R.id.textTimeInfo)
        rememberCheckbox = view.findViewById(R.id.checkboxRemember)
        biometricCheckbox = view.findViewById(R.id.checkboxBiometric)
    }

    private fun setupViews() {
        // Load remembered password if available (from SECURE storage)
        val rememberedPassword = securePreferencesManager.getRememberedPassword()
        if (rememberedPassword.isNotEmpty()) {
            passwordInput.setText(rememberedPassword)
            rememberCheckbox.isChecked = true
        }

        biometricCheckbox.isChecked = preferencesManager.isBiometricEnabled()

        updateBiometricButtonVisibility()

        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                passwordLayout.error = null
                unlockButton.isEnabled = !s.isNullOrEmpty() && !isUnlocking
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        unlockButton.setOnClickListener {
            if (!isUnlocking) {
                attemptUnlock(viaPassword = true)
            }
        }

        biometricButton.setOnClickListener {
            attemptBiometricUnlock()
        }

        cancelButton.setOnClickListener {
            activity?.moveTaskToBack(true)
        }

        passwordInput.setOnEditorActionListener { _, _, _ ->
            if (unlockButton.isEnabled && !isUnlocking) {
                attemptUnlock(viaPassword = true)
                true
            } else {
                false
            }
        }

        passwordInput.requestFocus()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        if (shouldAutoShowBiometric()) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (isAdded) {
                    attemptBiometricUnlock()
                }
            }, 500)
        }
    }

    private fun updateBiometricButtonVisibility() {
        val canUseBiometric = biometricHelper.canUseBiometric()
        val hasAuthToday = preferencesManager.hasPasswordAuthToday()
        val biometricEnabled = preferencesManager.isBiometricEnabled()
        val hasBiometricPassword = securePreferencesManager.hasBiometricPassword()

        biometricButton.visibility = if (canUseBiometric && hasAuthToday && biometricEnabled && hasBiometricPassword) {
            View.VISIBLE
        } else {
            View.GONE
        }

        biometricCheckbox.visibility = if (canUseBiometric) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun shouldAutoShowBiometric(): Boolean {
        return biometricHelper.canUseBiometric() &&
                preferencesManager.hasPasswordAuthToday() &&
                preferencesManager.isBiometricEnabled() &&
                securePreferencesManager.hasBiometricPassword()
    }

    private fun attemptBiometricUnlock() {
        if (!preferencesManager.hasPasswordAuthToday()) {
            timeInfoText.text = "Please enter password first today"
            return
        }

        val biometricPassword = securePreferencesManager.getBiometricPassword()
        if (biometricPassword.isEmpty()) {
            timeInfoText.text = "No saved password for biometric. Enable biometric and enter password once."
            return
        }

        biometricHelper.showBiometricPrompt(
            activity = requireActivity(),
            onSuccess = {
                passwordInput.setText(biometricPassword)
                attemptUnlock(viaPassword = false)
            },
            onError = { error ->
                timeInfoText.text = "Biometric error: $error"
            },
            onCancel = {
                timeInfoText.text = "Biometric cancelled. Use password."
            }
        )
    }

    private fun checkAuthStatus() {
        setLoading(true, "Checking server status...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.apiService.getAuthStatus()

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext

                    setLoading(false)

                    if (response.isSuccessful) {
                        val authStatus = response.body()

                        if (authStatus?.locked == true) {
                            if (authStatus.temporarilyLocked == true && authStatus.retryAfter != null) {
                                handleTemporaryLock(authStatus.retryAfter)
                            } else {
                                getChallenge()
                            }
                        } else {
                            Log.d(TAG, "Server not locked, dismissing dialog")
                            successfulUnlock("")
                        }
                    } else {
                        showError("Failed to check server status")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    setLoading(false)
                    showError("Connection error: ${e.message}")
                }
            }
        }
    }

    private fun getChallenge() {
        setLoading(true, "Getting security challenge...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.apiService.getAuthChallenge()

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext

                    setLoading(false)

                    if (response.isSuccessful) {
                        val challengeResponse = response.body()
                        currentChallenge = challengeResponse?.challenge

                        if (currentChallenge != null) {
                            updateTimeInfo("Challenge received. Enter your password to unlock.")
                            unlockButton.isEnabled = passwordInput.text?.isNotEmpty() == true && !isUnlocking
                            updateBiometricButtonVisibility()
                        } else {
                            showError("Failed to get security challenge")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        if (response.code() == 429) {
                            try {
                                val errorData = com.google.gson.Gson().fromJson(errorBody,
                                    com.google.gson.JsonObject::class.java)
                                val retryAfter = errorData.get("retry_after")?.asInt ?: 300
                                handleTemporaryLock(retryAfter)
                            } catch (e: Exception) {
                                showError("Server temporarily locked. Please try again later.")
                            }
                        } else {
                            showError("Failed to get challenge: ${response.code()}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    setLoading(false)
                    showError("Connection error: ${e.message}")
                }
            }
        }
    }

    private fun attemptUnlock(viaPassword: Boolean) {
        if (isUnlocking) return

        val password = passwordInput.text.toString().trim()
        val challenge = currentChallenge

        if (password.isEmpty()) {
            passwordLayout.error = "Password is required"
            return
        }

        if (challenge == null) {
            showError("No challenge available. Please retry.")
            getChallenge()
            return
        }

        isUnlocking = true
        setLoading(true, "Unlocking server...")

        unlockJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = generateHMACResponse(password, challenge)

                val unlockResponse = ApiClient.apiService.unlockServer(
                    mapOf("response" to response)
                )

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext

                    isUnlocking = false
                    setLoading(false)

                    if (unlockResponse.isSuccessful) {
                        val unlockData = unlockResponse.body()
                        val sessionToken = unlockData?.sessionToken

                        if (sessionToken != null) {
                            preferencesManager.setSessionToken(sessionToken)

                            // Save to SECURE storage if remember is checked
                            if (rememberCheckbox.isChecked) {
                                securePreferencesManager.setRememberedPassword(password)
                            } else {
                                securePreferencesManager.clearRememberedPassword()
                            }

                            // Save to SECURE biometric storage if enabled
                            if (biometricCheckbox.isChecked) {
                                securePreferencesManager.setBiometricPassword(password)
                                preferencesManager.setBiometricEnabled(true)
                            } else {
                                securePreferencesManager.clearBiometricPassword()
                                preferencesManager.setBiometricEnabled(false)
                            }

                            if (viaPassword) {
                                preferencesManager.setLastPasswordAuthDate()
                            }

                            Log.d(TAG, "Authentication successful, dismissing dialog")
                            successfulUnlock(sessionToken)
                        } else {
                            showError("Invalid response from server")
                        }
                    } else {
                        val errorBody = unlockResponse.errorBody()?.string()

                        if (unlockResponse.code() == 401) {
                            passwordLayout.error = "Incorrect password"
                            passwordInput.selectAll()
                            getChallenge()
                        } else if (unlockResponse.code() == 429) {
                            try {
                                val errorData = com.google.gson.Gson().fromJson(errorBody,
                                    com.google.gson.JsonObject::class.java)
                                val retryAfter = errorData.get("retry_after")?.asInt ?: 300
                                handleTemporaryLock(retryAfter)
                            } catch (e: Exception) {
                                showError("Too many failed attempts. Please try again later.")
                            }
                        } else {
                            showError("Unlock failed: ${unlockResponse.code()}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    isUnlocking = false
                    setLoading(false)
                    showError("Connection error: ${e.message}")
                }
            }
        }
    }

    private fun successfulUnlock(sessionToken: String) {
        try {
            Log.d(TAG, "Calling onUnlocked callback")
            onUnlocked?.invoke(sessionToken)

            Log.d(TAG, "Dismissing dialog")
            dismissAllowingStateLoss()
        } catch (e: Exception) {
            Log.e(TAG, "Error in successfulUnlock", e)
        }
    }

    private fun generateHMACResponse(password: String, challenge: String): String {
        return try {
            val secretKeySpec = SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(challenge.toByteArray(Charsets.UTF_8))
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate HMAC response", e)
        }
    }

    private fun handleTemporaryLock(retryAfterSeconds: Int) {
        unlockButton.isEnabled = false
        biometricButton.isEnabled = false
        passwordInput.isEnabled = false

        updateTimeInfo("Too many failed attempts. Try again in $retryAfterSeconds seconds.")
        startCountdown(retryAfterSeconds)
    }

    private fun startCountdown(seconds: Int) {
        val handler = Handler(Looper.getMainLooper())
        var remainingSeconds = seconds

        val countdownRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return

                if (remainingSeconds > 0) {
                    updateTimeInfo("Too many failed attempts. Try again in $remainingSeconds seconds.")
                    remainingSeconds--
                    handler.postDelayed(this, 1000)
                } else {
                    unlockButton.isEnabled = passwordInput.text?.isNotEmpty() == true && !isUnlocking
                    biometricButton.isEnabled = true
                    passwordInput.isEnabled = true
                    updateTimeInfo("You can now try again.")
                    getChallenge()
                }
            }
        }

        handler.post(countdownRunnable)
    }

    private fun setLoading(loading: Boolean, message: String = "") {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        unlockButton.isEnabled = !loading && passwordInput.text?.isNotEmpty() == true && !isUnlocking
        biometricButton.isEnabled = !loading
        passwordInput.isEnabled = !loading

        if (loading && message.isNotEmpty()) {
            updateTimeInfo(message)
        }
    }

    private fun updateTimeInfo(message: String) {
        timeInfoText.text = message
    }

    private fun showError(message: String) {
        passwordLayout.error = message
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unlockJob?.cancel()
        isShowing = false
    }
}