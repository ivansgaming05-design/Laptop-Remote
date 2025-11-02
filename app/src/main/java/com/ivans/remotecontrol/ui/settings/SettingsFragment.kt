package com.ivans.remotecontrol.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.network.ApiClient
import com.ivans.remotecontrol.utils.PreferencesManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context

class SettingsFragment : Fragment() {

    private lateinit var preferencesManager: PreferencesManager

    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var saveUrlButton: MaterialButton
    private lateinit var currentUrlText: TextView
    private lateinit var vibrationSwitch: MaterialSwitch
    private lateinit var versionText: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferencesManager = PreferencesManager(requireContext())

        initViews(view)
        loadCurrentSettings()
        setupClickListeners()
    }

    private fun initViews(view: View) {
        serverUrlInput = view.findViewById(R.id.serverUrlInput)
        saveUrlButton = view.findViewById(R.id.saveUrlButton)
        currentUrlText = view.findViewById(R.id.currentUrlText)
        vibrationSwitch = view.findViewById(R.id.vibrationSwitch)
        versionText = view.findViewById(R.id.versionText)
    }

    private fun loadCurrentSettings() {
        // Load current server URL
        val currentUrl = preferencesManager.getServerUrl()
        serverUrlInput.setText(currentUrl)
        currentUrlText.text = "Current: $currentUrl"

        // Load vibration setting
        vibrationSwitch.isChecked = preferencesManager.getVibrationEnabled()

        // Set version info
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            versionText.text = "Version $versionName ($versionCode)"
        } catch (e: Exception) {
            versionText.text = "Version 1.0"
        }
    }

    private fun performVibration() {
        if (preferencesManager.getVibrationEnabled()) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(
                    100,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }

    private fun setupClickListeners() {
        // Save URL button
        saveUrlButton.setOnClickListener {
            performVibration()  // ADD THIS

            val url = serverUrlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                // Validate URL format
                val formattedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "http://$url"
                }

                // Ensure URL ends with /
                val finalUrl = if (formattedUrl.endsWith("/")) formattedUrl else "$formattedUrl/"

                // Save to preferences
                preferencesManager.setServerUrl(finalUrl)
                currentUrlText.text = "Current: $finalUrl"

                // Update API client with new URL
                ApiClient.updateServerUrl(finalUrl)

                Toast.makeText(context, "URL saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
            }
        }

        // Vibration switch - vibrate ONLY when turning ON
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setVibrationEnabled(isChecked)

            if (isChecked) {
                performVibration()  // ADD THIS - only when enabling
            }

            val message = if (isChecked) "Vibration enabled" else "Vibration disabled"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload settings when fragment becomes visible
        loadCurrentSettings()
    }
}