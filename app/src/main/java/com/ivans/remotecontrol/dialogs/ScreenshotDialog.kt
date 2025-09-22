package com.ivans.remotecontrol.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.utils.PreferencesManager

class ScreenshotDialog : DialogFragment() {

    private lateinit var imageView: ImageView
    private lateinit var messageText: TextView
    private lateinit var preferencesManager: PreferencesManager
    private var screenshotId: String = ""

    companion object {
        fun show(fragmentManager: FragmentManager, screenshotId: String) {
            val dialog = ScreenshotDialog()
            dialog.screenshotId = screenshotId
            dialog.show(fragmentManager, "ScreenshotDialog")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_screenshot, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferencesManager = PreferencesManager(requireContext())
        imageView = view.findViewById(R.id.screenshotImageView)
        messageText = view.findViewById(R.id.messageText)

        setupViews()
        loadScreenshot()
    }

    private fun setupViews() {
        messageText.text = "Screenshot saved to gallery! Check your notifications to view."

        // Make dialog dismissible by tapping image
        imageView.setOnClickListener {
            dismiss()
        }
    }

    private fun loadScreenshot() {
        val serverUrl = preferencesManager.getServerUrl()
        val screenshotUrl = "$serverUrl/api/screenshots/$screenshotId"

        Glide.with(this)
            .load(screenshotUrl)
            .error(R.drawable.ic_screenshot)
            .placeholder(R.drawable.ic_screenshot)
            .into(imageView)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}