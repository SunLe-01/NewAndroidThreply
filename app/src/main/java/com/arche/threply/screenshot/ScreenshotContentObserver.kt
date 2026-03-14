package com.arche.threply.screenshot

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.util.Log

class ScreenshotContentObserver(
    handler: Handler,
    private val context: Context,
    private val onScreenshot: (Uri) -> Unit
) : ContentObserver(handler) {

    private val TAG = "ScreenshotObserver"
    private var lastProcessedId = -1L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (uri == null) return
        queryLatestScreenshot()
    }

    private fun queryLatestScreenshot() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return

                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val path = cursor.getString(pathCol) ?: ""

                // Dedup
                if (id == lastProcessedId) return
                lastProcessedId = id

                // Filter: path or name must contain "screenshot"
                val combined = "$path/$name".lowercase()
                if (!combined.contains("screenshot")) {
                    Log.d(TAG, "Not a screenshot: $combined")
                    return
                }

                val imageUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                Log.d(TAG, "Screenshot detected: $imageUri ($name)")
                onScreenshot(imageUri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query failed: ${e.message}")
        }
    }
}
