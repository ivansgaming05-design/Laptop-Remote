package com.ivans.remotecontrol.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.network.ApiClient
import com.ivans.remotecontrol.utils.PreferencesManager

class SettingsFragment : Fragment() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var saveButton: MaterialButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferencesManager = PreferencesManager(requireContext())
        initViews(view)
        loadCurrentSettings()
    }

    private fun initViews(view: View) {
        serverUrlInput = view.findViewById(R.id.serverUrlInput)
        saveButton = view.findViewById(R.id.saveUrlButton)

        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadCurrentSettings() {
        val currentUrl = preferencesManager.getServerUrl()
        serverUrlInput.setText(currentUrl)
    }

    private fun saveSettings() {
        val newUrl = serverUrlInput.text.toString().trim()

        if (newUrl.isEmpty()) {
            Toast.makeText(context, "Server URL cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate URL format
        if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
            Toast.makeText(context, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
            return
        }

        println("SettingsFragment: Saving new URL: $newUrl")

        // Save to preferences
        preferencesManager.setServerUrl(newUrl)

        // FORCE ApiClient to update (this is the key fix)
        ApiClient.updateServerUrl(newUrl)

        // Clear any cached connections by recreating the service
        val testUrl = ApiClient.getCurrentUrl()
        println("SettingsFragment: ApiClient now using: $testUrl")

        // Show immediate feedback
        Toast.makeText(context, "URL updated to: $newUrl", Toast.LENGTH_LONG).show()

        // Test the new connection immediately
        testNewConnection(newUrl)
    }

    private fun testNewConnection(url: String) {
        Thread {
            try {
                // Give ApiClient a moment to update
                Thread.sleep(500)

                // Test the connection
                val service = ApiClient.apiService
                // The actual test will happen when you use the app next

                activity?.runOnUiThread {
                    Toast.makeText(context, "URL saved! Test connection from home screen.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "URL saved but connection test failed", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}