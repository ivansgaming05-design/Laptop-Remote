package com.ivans.remotecontrol.ui.home

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.gridlayout.widget.GridLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.dialogs.AddFunctionDialog
import com.ivans.remotecontrol.models.CustomFunction
import android.os.Build
import android.os.VibrationEffect
import androidx.lifecycle.ViewModelProvider
import android.widget.Toast
import com.ivans.remotecontrol.utils.PreferencesManager
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.ivans.remotecontrol.MainActivity
import androidx.core.graphics.toColorInt

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel

    private lateinit var scrollView: ScrollView
    private lateinit var gridLayout: GridLayout
    private lateinit var expandablePanel: MaterialCardView
    private lateinit var expandableContent: LinearLayout
    private lateinit var expandHandle: LinearLayout
    private lateinit var brightnessSlider: Slider
    private lateinit var screenshotButton: MaterialButton

    // TeamViewer temporary buttons
    private var teamViewerButtonsContainer: LinearLayout? = null
    private var copyIdButton: MaterialButton? = null
    private var copyPasswordButton: MaterialButton? = null
    private var hasIdBeenCopied = false
    private var hasPasswordBeenCopied = false

    private var isNightLightOn = false
    private var panelHeight = 0

    private var retryTeamViewerButton: MaterialButton? = null
    private var connectionStatusIndicator: TextView? = null

    private var hasShownAuthDialog = false

    companion object {
        private var isPanelExpanded = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private fun initViews(view: View) {
        scrollView = view.findViewById(R.id.scrollView)
        gridLayout = view.findViewById(R.id.gridLayout)
        expandablePanel = view.findViewById(R.id.expandablePanel)
        expandableContent = view.findViewById(R.id.expandableContent)
        expandHandle = view.findViewById(R.id.expandHandle)
        brightnessSlider = view.findViewById(R.id.brightnessSlider)
        screenshotButton = view.findViewById(R.id.screenshotButton)

        // Create connection status indicator with proper styling
        connectionStatusIndicator = TextView(requireContext()).apply {
            "⚫ Checking connection...".also { text = it }
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER

            // Rounded corners like buttons
            val cornerRadius = 12f * resources.displayMetrics.density // 12dp corner radius
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setCornerRadius(cornerRadius)  // Use setter method instead of assignment
                setColor(ContextCompat.getColor(context, R.color.button_gray))
            }
            background = drawable

            // Apply button margins and height to match TeamViewer buttons
            val buttonMarginInPx = 12 * resources.displayMetrics.density.toInt() // 12dp like buttons
            val teamViewerButtonHeight = 48 * resources.displayMetrics.density.toInt() // Standard button height

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                teamViewerButtonHeight // Same height as TeamViewer ID/password buttons
            ).apply {
                setMargins(buttonMarginInPx, buttonMarginInPx, buttonMarginInPx, 8) // Bottom margin smaller to separate from buttons
            }

            // Padding inside the indicator
            setPadding(16, 12, 16, 12)
        }

        // Add connection status to main container (above the scroll view)
        val mainContainer = view.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.mainContainer)

        // Create a LinearLayout wrapper to hold both connection status and scroll view
        val wrapperLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }

        wrapperLayout.setPadding(0, 20, 0, 0)

        // Add connection status to wrapper
        wrapperLayout.addView(connectionStatusIndicator)

        // Move the scroll view to wrapper and adjust its constraint
        mainContainer.removeView(scrollView)

        // Update scroll view layout params to account for connection status
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f // Weight to take remaining space
        )

        wrapperLayout.addView(scrollView)

        // Add wrapper back to main container with proper constraints
        val wrapperParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
            0
        ).apply {
            topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            bottomToTop = R.id.expandablePanel
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        }
        wrapperLayout.layoutParams = wrapperParams

        mainContainer.addView(wrapperLayout)

        // Start connection checking
        checkConnectionStatus()
    }
    private fun performVibration() {
        val preferencesManager = PreferencesManager(requireContext())
        if (preferencesManager.getVibrationEnabled()) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Create a stronger vibration effect
                val vibrationEffect = VibrationEffect.createOneShot(
                    100,  // Duration in milliseconds (increased from 50)
                    VibrationEffect.DEFAULT_AMPLITUDE  // Use device's default amplitude (stronger than 255)
                )
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)  // Increased from 50ms
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel first
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[HomeViewModel::class.java]

        initViews(view)
        setupControlButtons()
        setupExpandablePanel()
        setupBrightnessSlider()
        setupObservers()

        viewModel.loadSystemStatus()
        viewModel.loadCustomFunctions()
    }

    override fun onResume() {
        super.onResume()
        // Reload custom functions when fragment becomes visible again
        viewModel.loadCustomFunctions()
    }

    private fun setupControlButtons() {
        // Set the GridLayout to have exactly 2 columns with equal weights
        gridLayout.columnCount = 2
        gridLayout.useDefaultMargins = false

        val buttonConfigs = listOf(
            ButtonConfig("Night Light", "Toggle night light mode", getCurrentNightLightIcon(), ButtonType.NORMAL) { toggleNightLightButton() },
            ButtonConfig("Toggle Display", "Switch between displays", R.drawable.ic_display, ButtonType.NORMAL) { viewModel.toggleDisplay() },
            ButtonConfig("Screenshot", "Take a screenshot", R.drawable.ic_screenshot, ButtonType.NORMAL) { viewModel.takeScreenshot() },
            ButtonConfig("TeamViewer", "Get remote access info", R.drawable.ic_teamviewer, ButtonType.NORMAL) { viewModel.getTeamViewer() },
            ButtonConfig("Selector", "Activate selection tool", R.drawable.ic_selector, ButtonType.NORMAL) { viewModel.openSelector() },
            ButtonConfig("Song Request", "Request a song for REPO", R.drawable.ic_music, ButtonType.NORMAL) { showSongRequestDialog() },
            ButtonConfig("Hand Tracking", "Start hand tracking", R.drawable.ic_hand, ButtonType.NORMAL) { viewModel.toggleHandTracking() },
            ButtonConfig("Quit Bot", "Stop remote server", R.drawable.ic_quit, ButtonType.NORMAL) { viewModel.quitServer() },
            ButtonConfig("Lock", "Lock the computer", R.drawable.ic_lock, ButtonType.NORMAL) { viewModel.lockSystem() },
            ButtonConfig("Panic", "Emergency shutdown", R.drawable.ic_panic, ButtonType.DANGER) { confirmPanicAction() },
            ButtonConfig("Add Function", "Create custom function", R.drawable.ic_add, ButtonType.SUCCESS) { showAddFunctionDialog() }
        )

        buttonConfigs.forEachIndexed { index, config ->
            val button = createControlButton(config)

            val params = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(index / 2)
                columnSpec = GridLayout.spec(index % 2, 1f) // Ensure weight is applied
                width = 0 // Important: Set width to 0 to allow weight to work
                setMargins(12, 12, 12, 12)
            }

            gridLayout.addView(button, params)
        }
    }

    private fun getCurrentNightLightIcon(): Int {
        return if (isNightLightOn) R.drawable.ic_moon else R.drawable.ic_sun
    }

    private fun toggleNightLightButton() {
        isNightLightOn = !isNightLightOn

        val nightLightButton = findNightLightButton()
        nightLightButton?.setIconResource(getCurrentNightLightIcon())

        viewModel.toggleNightlight()
    }

    private fun findNightLightButton(): MaterialButton? {
        for (i in 0 until gridLayout.childCount) {
            val child = gridLayout.getChildAt(i)
            if (child is MaterialButton && child.text == "Night Light") {
                return child
            }
        }
        return null
    }

    private fun createControlButton(config: ButtonConfig): MaterialButton {
        return MaterialButton(requireContext()).apply {
            text = config.text
            contentDescription = config.description
            setIconResource(config.iconRes)
            iconGravity = MaterialButton.ICON_GRAVITY_TOP
            iconSize = resources.getDimensionPixelSize(R.dimen.button_icon_size)
            iconTint = ContextCompat.getColorStateList(context, android.R.color.white)

            when (config.type) {
                ButtonType.DANGER -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.red_600))
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    strokeWidth = 0
                }
                ButtonType.SUCCESS -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.green_600))
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    strokeWidth = 0
                }
                ButtonType.NORMAL -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.button_gray))
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    strokeWidth = 1
                    strokeColor = ContextCompat.getColorStateList(context, R.color.button_stroke)
                }
            }

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.control_button_height)
            )

            textSize = 12f
            isAllCaps = false

            setOnClickListener {
                performVibration()
                config.action() }
        }
    }

    private fun setupExpandablePanel() {
        expandableContent.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        panelHeight = expandableContent.measuredHeight

        if (panelHeight <= 0) {
            panelHeight = resources.getDimensionPixelSize(R.dimen.expandable_panel_height)
        }

        if (isPanelExpanded) {
            expandableContent.visibility = View.VISIBLE
            expandableContent.layoutParams = expandableContent.layoutParams.apply {
                height = panelHeight
            }
        } else {
            expandableContent.layoutParams = expandableContent.layoutParams.apply {
                height = 0
            }
            expandableContent.visibility = View.GONE
        }

        expandHandle.setOnClickListener {
            togglePanel()
        }
    }

    private fun setupBrightnessSlider() {
        brightnessSlider.apply {
            valueFrom = 1f
            valueTo = 10f
            stepSize = 1f
            value = 5f

            clearOnChangeListeners()
            clearOnSliderTouchListeners()

            addOnChangeListener { slider, value, fromUser ->
                if (fromUser) {
                    updateBrightnessStatus(value.toInt())
                }
            }

            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    viewModel.setBrightness(slider.value.toInt())
                }
            })
        }

        screenshotButton.setOnClickListener {
            // Handle screenshot button click - implement based on your needs
        }
    }

    private fun togglePanel() {
        if (isPanelExpanded) {
            collapsePanel()
        } else {
            expandPanel()
        }
    }

    private fun expandPanel() {
        expandableContent.visibility = View.VISIBLE

        val animator = ValueAnimator.ofInt(0, panelHeight)
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            expandableContent.layoutParams = expandableContent.layoutParams.apply {
                height = value
            }
            expandableContent.requestLayout()
        }
        animator.duration = 300
        animator.start()

        isPanelExpanded = true
    }

    private fun collapsePanel() {
        val animator = ValueAnimator.ofInt(panelHeight, 0)
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            expandableContent.layoutParams = expandableContent.layoutParams.apply {
                height = value
            }
            expandableContent.requestLayout()
            if (value == 0) {
                expandableContent.visibility = View.GONE
            }
        }
        animator.duration = 300
        animator.start()

        isPanelExpanded = false
    }

    private fun updateBrightnessStatus(brightness: Int) {
        val currentStatus = viewModel.systemStatus.value
        if (currentStatus != null) {
            val statusBuilder = StringBuilder()
            statusBuilder.append("Night Light: ${if (currentStatus.nightlight) "ON" else "OFF"}\n")
            statusBuilder.append("Display: ${currentStatus.displayMode}\n")
            statusBuilder.append("Brightness: $brightness/10")
            // statusText.text = statusBuilder.toString() // Comment out if statusText doesn't exist
        }
    }

    private fun setupObservers() {
        viewModel.systemStatus.observe(viewLifecycleOwner) { status ->
            updateStatusDisplay(status)
            if (brightnessSlider.value.toInt() != status.brightness) {
                brightnessSlider.value = status.brightness.toFloat()
            }
        }

        viewModel.teamViewerInfo.observe(viewLifecycleOwner) { teamViewerInfo ->
            Log.d("HomeFragment", "TeamViewer info received: ID='${teamViewerInfo.id}', Password='${teamViewerInfo.password}'")
            if (teamViewerInfo.id.isNotEmpty() || teamViewerInfo.password.isNotEmpty()) {
                Log.d("HomeFragment", "Calling showTemporaryTeamViewerButtons")
                showTemporaryTeamViewerButtons(teamViewerInfo)
            } else {
                Log.d("HomeFragment", "TeamViewer info is empty - should trigger retry button")
                showTemporaryTeamViewerButtons(teamViewerInfo)
            }
        }

        viewModel.customFunctions.observe(viewLifecycleOwner) { functions ->
            addCustomFunctionButtons(functions)
        }

        viewModel.authenticationRequired.observe(viewLifecycleOwner) { required ->
            if (required && !hasShownAuthDialog) {
                hasShownAuthDialog = true
                Log.d("HomeFragment", "Authentication required, showing dialog")

                (activity as? MainActivity)?.showSecurityDialog {
                    hasShownAuthDialog = false
                    viewModel.clearAuthenticationRequired()
                    // Retry the last action or refresh data
                    viewModel.loadSystemStatus()
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
            }
        }
    }

    private fun showTemporaryTeamViewerButtons(teamViewerInfo: com.ivans.remotecontrol.models.TeamViewerInfo) {
        Log.d("TeamViewer", "showTemporaryTeamViewerButtons called")
        Log.d("TeamViewer", "ID: '${teamViewerInfo.id}', Password: '${teamViewerInfo.password}'")

        hasIdBeenCopied = false
        hasPasswordBeenCopied = false

        hideTeamViewerButtons()

        if (isPanelExpanded) {
            collapsePanel()
        }

        // Check if we got valid info
        val hasValidInfo = teamViewerInfo.id.isNotEmpty() && teamViewerInfo.password.isNotEmpty()
        Log.d("TeamViewer", "hasValidInfo: $hasValidInfo")

        teamViewerButtonsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(12, 16, 12, 16)
            }
            elevation = 8f

            // Set initial position for animation - start below the screen
            translationY = 200f // Start 200px below final position
            alpha = 0f // Start transparent
        }

        if (hasValidInfo) {
            Log.d("TeamViewer", "Showing ID and Password buttons")
            val teamViewerButtonHeight = 58 * resources.displayMetrics.density.toInt()

            copyIdButton = MaterialButton(requireContext()).apply {
                text = teamViewerInfo.id
                setBackgroundColor(ContextCompat.getColor(context, R.color.blue_500))
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                elevation = 4f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    teamViewerButtonHeight,
                    1f
                ).apply {
                    setMargins(0, 0, 4, 0)
                }
                setOnClickListener {
                    Log.d("TeamViewer", "ID button clicked")
                    copyToClipboard("TeamViewer ID", teamViewerInfo.id)
                    showMessage("ID copied: ${teamViewerInfo.id}")
                    hasIdBeenCopied = true
                }
            }

            copyPasswordButton = MaterialButton(requireContext()).apply {
                text = teamViewerInfo.password
                setBackgroundColor(ContextCompat.getColor(context, R.color.orange_500))
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                elevation = 4f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    teamViewerButtonHeight,
                    1f
                ).apply {
                    setMargins(4, 0, 0, 0)
                }
                setOnClickListener {
                    Log.d("TeamViewer", "Password button clicked")
                    copyToClipboard("TeamViewer Password", teamViewerInfo.password)
                    showMessage("Password copied: ${teamViewerInfo.password}")
                    hasPasswordBeenCopied = true
                    hideTeamViewerButtons()
                    viewModel.clearError()
                }
            }

            teamViewerButtonsContainer?.addView(copyIdButton)
            teamViewerButtonsContainer?.addView(copyPasswordButton)
        } else {
            Log.d("TeamViewer", "Showing retry button")
            val teamViewerButtonHeight = 54 * resources.displayMetrics.density.toInt()

            retryTeamViewerButton = MaterialButton(requireContext()).apply {
                "Retry TeamViewer".also { text = it }
                setBackgroundColor(ContextCompat.getColor(context, R.color.blue_500))
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                elevation = 4f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    teamViewerButtonHeight
                )
                setOnClickListener {
                    Log.d("TeamViewer", "Retry button clicked")
                    hideTeamViewerButtons()
                    viewModel.getTeamViewer()
                    showMessage("Retrying TeamViewer connection...")
                }
            }

            teamViewerButtonsContainer?.addView(retryTeamViewerButton)
        }

        // Add buttons to the main constraint layout
        val parentContainer = expandablePanel.parent as androidx.constraintlayout.widget.ConstraintLayout
        val buttonParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomToTop = R.id.expandablePanel
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            setMargins(16, 0, 16, 8)
        }

        teamViewerButtonsContainer?.layoutParams = buttonParams
        parentContainer.addView(teamViewerButtonsContainer)

        // Animate slide-in from bottom
        teamViewerButtonsContainer?.animate()
            ?.translationY(0f) // Move to final position
            ?.alpha(1f) // Fade in
            ?.setDuration(400) // 400ms animation
            ?.setInterpolator(android.view.animation.DecelerateInterpolator()) // Smooth deceleration
            ?.start()

        Log.d("TeamViewer", "TeamViewer buttons added with slide animation")

        // Auto-hide after 15 seconds if user doesn't interact
        view?.postDelayed({
            if (teamViewerButtonsContainer?.parent != null) {
                hideTeamViewerButtons()
            }
        }, 15000)
    }

    private fun hideTeamViewerButtons() {
        teamViewerButtonsContainer?.let { container ->
            // Animate slide-out to bottom
            container.animate()
                ?.translationY(200f) // Move down 200px
                ?.alpha(0f) // Fade out
                ?.setDuration(300) // 300ms animation
                ?.setInterpolator(android.view.animation.AccelerateInterpolator()) // Smooth acceleration
                ?.withEndAction {
                    // Remove from parent after animation completes
                    (container.parent as? ViewGroup)?.removeView(container)
                    teamViewerButtonsContainer = null
                    copyIdButton = null
                    copyPasswordButton = null
                    retryTeamViewerButton = null
                    hasIdBeenCopied = false
                    hasPasswordBeenCopied = false
                }
                ?.start()
        }
    }

    private fun showRetryButton() {
        teamViewerButtonsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        retryTeamViewerButton = MaterialButton(requireContext()).apply {
            "Retry TeamViewer".also { text = it }
            setBackgroundColor(ContextCompat.getColor(context, R.color.blue_500)) // Same as ID button
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, // Full width (spans both ID and password button areas)
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 1600, 0, 0) // Same top margin as other buttons
            }
            setOnClickListener {
                hideTeamViewerButtons() // Hide retry button
                viewModel.getTeamViewer() // Retry getting TeamViewer info
                showMessage("Retrying TeamViewer connection...")
            }
        }

        teamViewerButtonsContainer?.addView(retryTeamViewerButton)
    }


    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun updateStatusDisplay(status: com.ivans.remotecontrol.models.SystemStatus) {
        val statusBuilder = StringBuilder()
        statusBuilder.append("Night Light: ${if (status.nightlight) "ON" else "OFF"}\n")
        statusBuilder.append("Display: ${status.displayMode}\n")
        statusBuilder.append("Brightness: ${status.brightness}/10")

        // statusText.text = statusBuilder.toString() // Comment out if statusText doesn't exist
    }

    private fun addCustomFunctionButtons(functions: List<CustomFunction>) {
        // Remove existing custom function buttons
        val buttonsToRemove = mutableListOf<View>()
        for (i in 0 until gridLayout.childCount) {
            val child = gridLayout.getChildAt(i)
            if (child.tag == "custom_function") {
                buttonsToRemove.add(child)
            }
        }
        buttonsToRemove.forEach { gridLayout.removeView(it) }

        // Remove the "Add Function" button temporarily to reposition it
        var addFunctionButton: MaterialButton? = null
        for (i in 0 until gridLayout.childCount) {
            val child = gridLayout.getChildAt(i)
            if (child is MaterialButton && child.text == "Add Function") {
                addFunctionButton = child
                gridLayout.removeView(child)
                break
            }
        }

        // Calculate starting position for new custom buttons
        // After removing "Add Function", we have 10 base buttons (positions 0-9)
        // Custom buttons should start at position 10
        var currentPosition = 10  // FIXED: Simply use 10 instead of dynamic calculation

        functions.forEach { function ->
            val button = MaterialButton(requireContext()).apply {
                text = function.name
                setIconResource(getIconResourceFromName(function.iconName))
                iconGravity = MaterialButton.ICON_GRAVITY_TOP
                iconSize = resources.getDimensionPixelSize(R.dimen.button_icon_size)
                iconTint = ContextCompat.getColorStateList(context, android.R.color.white)

                try {
                    setBackgroundColor(function.color.toColorInt())
                } catch (e: Exception) {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.blue_500))
                }

                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                tag = "custom_function"
                isAllCaps = false
                textSize = 12f

                setOnClickListener {
                    viewModel.executeCustomFunction(function.id.toString())
                }

                setOnLongClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Remove Custom Function")
                        .setMessage("Do you want to remove '${function.name}'?")
                        .setPositiveButton("Remove") { _, _ ->
                            viewModel.removeCustomFunction(function.id)
                            showMessage("Function '${function.name}' removed")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.control_button_height)
                )
            }

            val row = currentPosition / 2
            val column = currentPosition % 2

            val params = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(row)
                columnSpec = GridLayout.spec(column, 1f)
                width = 0
                setMargins(12, 12, 12, 12)
            }

            gridLayout.addView(button, params)
            currentPosition++
        }

        // Re-add the "Add Function" button at the next position
        addFunctionButton?.let { button ->
            val row = currentPosition / 2
            val column = currentPosition % 2

            val params = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(row)
                columnSpec = GridLayout.spec(column, 1f)
                width = 0
                setMargins(12, 12, 12, 12)
            }

            gridLayout.addView(button, params)
        }
    }

    private fun confirmPanicAction() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Panic - Shutdown System")
            .setMessage("This will immediately shutdown your laptop. Are you sure?")
            .setPositiveButton("Shutdown") { _, _ ->
                viewModel.panicShutdown()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddFunctionDialog() {
        AddFunctionDialog.show(childFragmentManager) { name, color, scriptFile, iconRes ->
            // Pass the icon resource to your view model or handle it accordingly
            viewModel.addCustomFunction(name, color, scriptFile, iconRes)
            Toast.makeText(context, "Function '$name' added successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getIconResourceFromName(iconName: String?): Int {
        return when (iconName) {
            "ic_home" -> R.drawable.ic_home
            "ic_settings" -> R.drawable.ic_settings
            "ic_screenshot" -> R.drawable.ic_screenshot
            "ic_display" -> R.drawable.ic_display
            "ic_lock" -> R.drawable.ic_lock
            "ic_hand" -> R.drawable.ic_hand
            "ic_moon" -> R.drawable.ic_moon
            "ic_sun" -> R.drawable.ic_sun
            "ic_alarm" -> R.drawable.ic_alarm
            "ic_teamviewer" -> R.drawable.ic_teamviewer
            "ic_selector" -> R.drawable.ic_selector
            "ic_clean" -> R.drawable.ic_clean
            "ic_quit" -> R.drawable.ic_quit
            "ic_panic" -> R.drawable.ic_panic
            else -> R.drawable.ic_add // default
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showError(error: String) {
        Snackbar.make(requireView(), error, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.red_500))
            .show()
    }

    private fun checkConnectionStatus() {
        viewModel.checkConnection { isConnected ->
            activity?.runOnUiThread {
                connectionStatusIndicator?.apply {
                    // Create rounded drawable for each state
                    val cornerRadius = 12f * resources.displayMetrics.density // 12dp corner radius

                    val drawable = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setCornerRadius(cornerRadius)

                        // Set color based on connection status
                        if (isConnected) {
                            setColor(ContextCompat.getColor(context, R.color.green_600))
                        } else {
                            setColor(ContextCompat.getColor(context, R.color.red_600))
                        }
                    }

                    // Update text and apply the rounded background
                    if (isConnected) {
                        "🟢 Connected".also { text = it }
                    } else {
                        "🔴 Disconnected".also { text = it }
                    }

                    background = drawable
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                }
            }
        }

        // Check again in 10 seconds
        view?.postDelayed({
            if (isAdded && activity != null) {
                checkConnectionStatus()
            }
        }, 10000)
    }

    private fun showSongRequestDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Enter song name or artist"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(60, 40, 60, 40)

            // Set text appearance
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Song Request for REPO")
            .setMessage("Enter the song you want to play:")
            .setView(input)
            .setPositiveButton("Request") { _, _ ->
                val songQuery = input.text.toString().trim()
                if (songQuery.isNotEmpty()) {
                    performVibration()
                    viewModel.requestSong(songQuery)
                    showMessage("Requesting: $songQuery")
                } else {
                    showError("Please enter a song name")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    data class ButtonConfig(
        val text: String,
        val description: String,
        val iconRes: Int,
        val type: ButtonType,
        val action: () -> Unit
    )

    enum class ButtonType {
        NORMAL, DANGER, SUCCESS
    }
}