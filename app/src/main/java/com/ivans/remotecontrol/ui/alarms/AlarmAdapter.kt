package com.ivans.remotecontrol.ui.alarms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.models.Alarm

class AlarmAdapter(
    private val onToggleClick: (Alarm) -> Unit,
    private val onDeleteClick: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val daysText: TextView = itemView.findViewById(R.id.daysText)
        private val toggleSwitch: MaterialSwitch = itemView.findViewById(R.id.toggleSwitch)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)

        fun bind(alarm: Alarm) {
            timeText.text = alarm.getFormattedTime()
            daysText.text = alarm.getDaysString()

            // Set switch state without triggering listener
            toggleSwitch.setOnCheckedChangeListener(null)
            toggleSwitch.isChecked = alarm.enabled
            toggleSwitch.setOnCheckedChangeListener { _, _ ->
                onToggleClick(alarm)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(alarm)
            }

            // Visual feedback for disabled alarms
            val alpha = if (alarm.enabled) 1.0f else 0.5f
            timeText.alpha = alpha
            daysText.alpha = alpha
        }
    }

    private class AlarmDiffCallback : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
            return oldItem == newItem
        }
    }
}