package com.ivans.remotecontrol.ui.alarms

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.dialogs.AddAlarmDialog
import com.ivans.remotecontrol.models.Alarm
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import com.ivans.remotecontrol.utils.PreferencesManager

class AlarmsFragment : Fragment() {

    private val viewModel: AlarmsViewModel by viewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var addAlarmButton: FloatingActionButton
    private lateinit var alarmsAdapter: AlarmsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_alarms, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupObservers()

        viewModel.loadAlarms()
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

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.alarmsRecyclerView)
        addAlarmButton = view.findViewById(R.id.addAlarmFab)

        addAlarmButton.setOnClickListener {
            performVibration()  // ADD THIS
            AddAlarmDialog.show(parentFragmentManager) { time, days ->
                // Create an Alarm object from the dialog parameters
                val timeParts = time.split(":")
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()

                val newAlarm = Alarm(
                    id = 0, // Server will assign ID
                    hour = hour,
                    minute = minute,
                    time = time,
                    days = days,
                    enabled = true
                )

                viewModel.addAlarm(newAlarm)
            }
        }
    }

    private fun setupRecyclerView() {
        alarmsAdapter = AlarmsAdapter(
            onToggleAlarm = { alarm ->
                val updatedAlarm = alarm.copy(enabled = !alarm.enabled)
                viewModel.updateAlarm(alarm.id.toString(), updatedAlarm)
            },
            onDeleteAlarm = { alarm ->
                viewModel.deleteAlarm(alarm.id.toString())
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alarmsAdapter
        }
    }

    private fun setupObservers() {
        viewModel.alarms.observe(viewLifecycleOwner) { alarms ->
            alarmsAdapter.submitList(alarms)
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            // Handle loading state if needed
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun showAddAlarmDialog() {
        // Create a simple alarm for testing
        val testAlarm = Alarm(
            id = System.currentTimeMillis().toInt(),
            hour = 9,
            minute = 0,
            days = listOf(0, 1, 2, 3, 4), // Mon-Fri
            enabled = true,
            time = "09:00"
        )

        viewModel.addAlarm(testAlarm)
    }
}

// Simple adapter for alarms
class AlarmsAdapter(
    private val onToggleAlarm: (Alarm) -> Unit,
    private val onDeleteAlarm: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmsAdapter.AlarmViewHolder>() {

    private var alarms = listOf<Alarm>()

    fun submitList(newAlarms: List<Alarm>) {
        alarms = newAlarms
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(alarms[position])
    }

    override fun getItemCount() = alarms.size

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val daysText: TextView = itemView.findViewById(R.id.daysText)
        private val enabledSwitch: MaterialSwitch = itemView.findViewById(R.id.toggleSwitch)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)

        fun bind(alarm: Alarm) {
            timeText.text = alarm.getFormattedTime()
            daysText.text = alarm.getDaysString()
            enabledSwitch.isChecked = alarm.enabled

            enabledSwitch.setOnCheckedChangeListener { _, _ ->
                onToggleAlarm(alarm)
            }

            deleteButton.setOnClickListener {
                onDeleteAlarm(alarm)
            }
        }
    }
}