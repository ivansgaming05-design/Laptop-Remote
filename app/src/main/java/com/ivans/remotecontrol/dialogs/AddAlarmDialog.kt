package com.ivans.remotecontrol.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.ivans.remotecontrol.R

class AddAlarmDialog : DialogFragment() {

    companion object {
        fun show(
            fragmentManager: FragmentManager,
            onAlarmAdded: (String, List<Int>) -> Unit
        ) {
            val dialog = AddAlarmDialog()
            dialog.onAlarmAdded = onAlarmAdded
            dialog.show(fragmentManager, "AddAlarmDialog")
        }
    }

    private var onAlarmAdded: ((String, List<Int>) -> Unit)? = null

    private lateinit var hourPicker: NumberPicker
    private lateinit var minutePicker: NumberPicker
    private lateinit var chipGroup: ChipGroup
    private lateinit var addButton: MaterialButton
    private lateinit var cancelButton: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_alarm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupTimePickers()
        setupDayChips()
        setupButtons()
    }

    private fun initViews(view: View) {
        hourPicker = view.findViewById(R.id.hourPicker)
        minutePicker = view.findViewById(R.id.minutePicker)
        chipGroup = view.findViewById(R.id.dayChipGroup)
        addButton = view.findViewById(R.id.addButton)
        cancelButton = view.findViewById(R.id.cancelButton)
    }

    private fun setupTimePickers() {
        hourPicker.apply {
            minValue = 0
            maxValue = 23
            value = 7
            setFormatter { String.format("%02d", it) }
        }

        minutePicker.apply {
            minValue = 0
            maxValue = 59
            value = 0
            setFormatter { String.format("%02d", it) }
        }
    }

    private fun setupDayChips() {
        val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        dayNames.forEachIndexed { index, day ->
            val chip = Chip(requireContext()).apply {
                text = day
                isCheckable = true
                tag = index
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupButtons() {
        addButton.setOnClickListener {
            val time = String.format("%02d:%02d", hourPicker.value, minutePicker.value)
            val selectedDays = getSelectedDays()
            onAlarmAdded?.invoke(time, selectedDays)
            dismiss()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun getSelectedDays(): List<Int> {
        val selectedDays = mutableListOf<Int>()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            if (chip.isChecked) {
                selectedDays.add(chip.tag as Int)
            }
        }
        return selectedDays
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}