package com.ivans.remotecontrol.utils

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ivans.remotecontrol.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream

class ScreenshotManager(private val context: Context) {

    private val notificationId = 1001

    suspend fun downloadAndSaveScreenshot(screenshotUrl: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                // Download the image
                val client = OkHttpClient()
                val request = Request.Builder().url(screenshotUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) return@withContext null

                val inputStream = response.body?.byteStream() ?: return@withContext null
                val bitmap = BitmapFactory.decodeStream(inputStream)

                // Save to gallery
                val savedUri = saveImageToGallery(bitmap)

                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        Toast.makeText(context, "Screenshot saved to gallery", Toast.LENGTH_SHORT).show()
                        showNotification(savedUri)
                    } else {
                        Toast.makeText(context, "Failed to save screenshot", Toast.LENGTH_SHORT).show()
                    }
                }

                savedUri

            } catch (e: Exception) {
                android.util.Log.e("ScreenshotManager", "Error saving screenshot", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving screenshot: ${e.message}", Toast.LENGTH_LONG).show()
                }
                null
            }
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap): Uri? {
        val filename = "RemoteControl_Screenshot_${System.currentTimeMillis()}.jpg"
        var imageUri: Uri? = null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (Scoped Storage)
                val resolver: ContentResolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RemoteControl")
                }

                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                imageUri?.let { uri ->
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                }
            } else {
                // Android 9 and below
                val imagesDir = Environment.getExternalStoragePublicDirectory("${Environment.DIRECTORY_PICTURES}/RemoteControl")
                imagesDir?.mkdirs()
                val image = java.io.File(imagesDir, filename)

                java.io.FileOutputStream(image).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }

                imageUri = Uri.fromFile(image)
            }

            imageUri

        } catch (e: Exception) {
            android.util.Log.e("ScreenshotManager", "Error saving to gallery", e)
            null
        }
    }

    private fun showNotification(imageUri: Uri?) {
        // Create intent that opens the specific image in gallery
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(imageUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(context, "screenshots")
            .setSmallIcon(R.drawable.ic_screenshot)
            .setContentTitle("Remote Control Screenshot")
            .setContentText("Tap to view screenshot in gallery")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Makes it pop up
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound + vibration
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotManager", "Failed to show notification", e)
        }
    }
}