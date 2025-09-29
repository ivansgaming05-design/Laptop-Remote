// SecurityDialog.kt - Fixed version with proper dismissal
package com.ivans.remotecontrol.dialogs

import android.app.Dialog
import android.content.Context
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
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Log

class SecurityDialog : DialogFragment() {

    companion object {
        private const val TAG = "SecurityDialog"
        private var isShowing = false // Add class-level flag

        fun show(fragmentManager: FragmentManager, onUnlocked: (String) -> Unit) {
            if (isShowing) {
                Log.d(TAG, "Dialog already showing, ignoring request")
                return
            }

            // Dismiss any existing security dialog first
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
    private lateinit var progressBar: ProgressBar
    private lateinit var timeInfoText: TextView
    private lateinit var rememberCheckbox: CheckBox

    private lateinit var preferencesManager: PreferencesManager
    private var currentChallenge: String? = null
    private var unlockJob: Job? = null
    private var isUnlocking = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        // Make dialog non-cancelable but don't try to set overlay type
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

        initViews(view)
        setupViews()
        checkAuthStatus()
    }

    private fun initViews(view: View) {
        passwordInput = view.findViewById(R.id.editTextPassword)
        passwordLayout = view.findViewById(R.id.passwordLayout)
        unlockButton = view.findViewById(R.id.buttonUnlock)
        cancelButton = view.findViewById(R.id.buttonCancel)
        progressBar = view.findViewById(R.id.progressBar)
        timeInfoText = view.findViewById(R.id.textTimeInfo)
        rememberCheckbox = view.findViewById(R.id.checkboxRemember)
    }

    private fun setupViews() {
        // Load remembered password if available
        val rememberedPassword = preferencesManager.getRememberedPassword()
        if (rememberedPassword.isNotEmpty()) {
            passwordInput.setText(rememberedPassword)
            rememberCheckbox.isChecked = true
        }

        // Setup text watcher for password input
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                passwordLayout.error = null
                unlockButton.isEnabled = !s.isNullOrEmpty() && !isUnlocking
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup button listeners
        unlockButton.setOnClickListener {
            if (!isUnlocking) {
                attemptUnlock()
            }
        }
        cancelButton.setOnClickListener {
            // For security dialog, cancel should minimize app or similar
            activity?.moveTaskToBack(true)
        }

        // Enter key should trigger unlock
        passwordInput.setOnEditorActionListener { _, _, _ ->
            if (unlockButton.isEnabled && !isUnlocking) {
                attemptUnlock()
                true
            } else {
                false
            }
        }

        // Focus on password input
        passwordInput.requestFocus()

        // Show keyboard
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun checkAuthStatus() {
        setLoading(true, "Checking server status...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.apiService.getAuthStatus()

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext // Check if fragment is still attached

                    setLoading(false)

                    if (response.isSuccessful) {
                        val authStatus = response.body()

                        if (authStatus?.locked == true) {
                            if (authStatus.temporarilyLocked == true && authStatus.retryAfter != null) {
                                handleTemporaryLock(authStatus.retryAfter)
                            } else {
                                // Server is locked, get challenge
                                getChallenge()
                            }
                        } else {
                            // Server is not locked, dismiss dialog
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
                        } else {
                            showError("Failed to get security challenge")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        if (response.code() == 429) {
                            // Parse retry after from error response
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

    private fun attemptUnlock() {
        if (isUnlocking) return // Prevent multiple attempts

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
                // Generate HMAC-SHA256 response
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
                            // Save session token
                            preferencesManager.setSessionToken(sessionToken)

                            // Save password if checkbox is checked
                            if (rememberCheckbox.isChecked) {
                                preferencesManager.setRememberedPassword(password)
                            } else {
                                preferencesManager.clearRememberedPassword()
                            }

                            // Success!
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

                            // Get new challenge for next attempt
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
            val secretKeySpec = javax.crypto.spec.SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256")
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(challenge.toByteArray(Charsets.UTF_8))

            // Convert to hex string
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate HMAC response", e)
        }
    }

    private fun handleTemporaryLock(retryAfterSeconds: Int) {
        unlockButton.isEnabled = false
        passwordInput.isEnabled = false

        updateTimeInfo("Too many failed attempts. Try again in $retryAfterSeconds seconds.")

        // Start countdown
        startCountdown(retryAfterSeconds)
    }

    private fun startCountdown(seconds: Int) {
        val handler = Handler(Looper.getMainLooper())
        var remainingSeconds = seconds

        val countdownRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return // Stop if fragment is detached

                if (remainingSeconds > 0) {
                    updateTimeInfo("Too many failed attempts. Try again in $remainingSeconds seconds.")
                    remainingSeconds--
                    handler.postDelayed(this, 1000)
                } else {
                    // Re-enable input and get new challenge
                    unlockButton.isEnabled = passwordInput.text?.isNotEmpty() == true && !isUnlocking
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
        isShowing = false // Reset flag when dialog is destroyed
    }
}