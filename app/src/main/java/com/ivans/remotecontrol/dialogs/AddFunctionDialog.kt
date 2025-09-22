package com.ivans.remotecontrol.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddFunctionDialog : DialogFragment() {

    var onFunctionAdded: ((String, String, String, Int) -> Unit)? = null

    private lateinit var nameInput: TextInputEditText
    private lateinit var nameLayout: TextInputLayout
    private lateinit var colorSpinner: Spinner
    private lateinit var scriptSpinner: Spinner
    private lateinit var iconSpinner: Spinner
    private lateinit var addButton: MaterialButton
    private lateinit var cancelButton: MaterialButton

    private var availableScripts = listOf<String>()
    private var selectedIconRes = R.drawable.ic_add // Default icon

    companion object {
        fun show(
            fragmentManager: FragmentManager,
            onFunctionAdded: (String, String, String, Int) -> Unit
        ) {
            val dialog = AddFunctionDialog()
            dialog.onFunctionAdded = onFunctionAdded
            dialog.show(fragmentManager, "AddFunctionDialog")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_function, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupColorSpinner()
        setupIconSpinner()
        setupButtons()
        loadAvailableScripts()
    }

    private fun initViews(view: View) {
        nameInput = view.findViewById(R.id.nameInput)
        nameLayout = view.findViewById(R.id.nameLayout)
        colorSpinner = view.findViewById(R.id.colorSpinner)
        scriptSpinner = view.findViewById(R.id.scriptSpinner)
        iconSpinner = view.findViewById(R.id.iconSpinner)
        addButton = view.findViewById(R.id.addButton)
        cancelButton = view.findViewById(R.id.cancelButton)
    }

    private fun setupColorSpinner() {
        val colors = arrayOf(
            "Purple" to "#6200EA",
            "Blue" to "#2196F3",
            "Green" to "#4CAF50",
            "Orange" to "#FF9800",
            "Red" to "#F44336",
            "Gray" to "#424242"
        )

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            colors.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        colorSpinner.adapter = adapter
    }

    private fun setupIconSpinner() {
        val icons = arrayOf(
            "Default" to R.drawable.ic_add,
            "Home" to R.drawable.ic_home,
            "Settings" to R.drawable.ic_settings,
            "Screenshot" to R.drawable.ic_screenshot,
            "Display" to R.drawable.ic_display,
            "Lock" to R.drawable.ic_lock,
            "Hand" to R.drawable.ic_hand,
            "Moon" to R.drawable.ic_moon,
            "Sun" to R.drawable.ic_sun,
            "Alarm" to R.drawable.ic_alarm,
            "TeamViewer" to R.drawable.ic_teamviewer,
            "Selector" to R.drawable.ic_selector,
            "Clean" to R.drawable.ic_clean,
            "Quit" to R.drawable.ic_quit,
            "Panic" to R.drawable.ic_panic
        )

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            icons.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        iconSpinner.adapter = adapter

        // Force dropdown to appear above the spinner if near bottom of screen
        iconSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedIconRes = icons[position].second
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Set dropdown height limit
        try {
            val popup = Spinner::class.java.getDeclaredField("mPopup")
            popup.isAccessible = true
            val popupWindow = popup.get(iconSpinner) as? android.widget.ListPopupWindow
            popupWindow?.height = (resources.displayMetrics.heightPixels * 0.4).toInt() // 40% of screen height
        } catch (e: Exception) {
            // Ignore if reflection fails
        }
    }

    private fun loadAvailableScripts() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scripts = ApiClient.apiService.getAvailableScripts()

                withContext(Dispatchers.Main) {
                    if (scripts.isSuccessful) {
                        availableScripts = scripts.body() ?: emptyList()
                    } else {
                        availableScripts = emptyList()
                    }
                    setupScriptSpinner()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    availableScripts = emptyList()
                    setupScriptSpinner()
                }
            }
        }
    }

    private fun setupScriptSpinner() {
        val scriptNames = if (availableScripts.isEmpty()) {
            listOf("No scripts found")
        } else {
            availableScripts
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            scriptNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scriptSpinner.adapter = adapter
    }

    private fun setupButtons() {
        addButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val selectedColor = getSelectedColor()
            val selectedScript = getSelectedScript()

            when {
                name.isEmpty() -> {
                    nameLayout.error = "Name is required"
                    return@setOnClickListener
                }
                selectedScript.isEmpty() || selectedScript == "No scripts found" -> {
                    // Show error for script selection
                    return@setOnClickListener
                }
                else -> {
                    nameLayout.error = null
                    onFunctionAdded?.invoke(name, selectedColor, selectedScript, selectedIconRes)
                    dismiss()
                }
            }
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun getSelectedColor(): String {
        val colors = arrayOf(
            "#6200EA", "#2196F3", "#4CAF50", "#FF9800",
            "#F44336", "#424242"
        )
        return colors[colorSpinner.selectedItemPosition]
    }

    private fun getSelectedScript(): String {
        return if (availableScripts.isEmpty()) {
            ""
        } else {
            availableScripts[scriptSpinner.selectedItemPosition]
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}