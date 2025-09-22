package com.ivans.remotecontrol.receivers

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.ivans.remotecontrol.COPY_ACTION" -> {
                val text = intent.getStringExtra("text") ?: return
                val label = intent.getStringExtra("label") ?: "Text"

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, text)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}