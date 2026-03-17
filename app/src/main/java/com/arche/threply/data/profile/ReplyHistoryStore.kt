package com.arche.threply.data.profile

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File

data class ReplyHistoryEntry(
    @SerializedName("selectedReply") val selectedReply: String,
    @SerializedName("inputContext") val inputContext: String,
    @SerializedName("mode") val mode: String,
    @SerializedName("timestamp") val timestamp: Long
)

object ReplyHistoryStore {
    private const val TAG = "ReplyHistoryStore"
    private const val FILE_NAME = "reply_history.json"
    private const val MAX_ENTRIES = 200
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<ReplyHistoryEntry>>() {}.type

    @Synchronized
    fun append(context: Context, entry: ReplyHistoryEntry) {
        try {
            val list = readAll(context)
            list.add(entry)
            // Ring buffer: drop oldest when exceeding max
            while (list.size > MAX_ENTRIES) list.removeAt(0)
            writeAll(context, list)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to append history: ${e.message}")
        }
    }

    fun getRecent(context: Context, n: Int): List<ReplyHistoryEntry> {
        val list = readAll(context)
        return list.takeLast(n)
    }

    fun count(context: Context): Int = readAll(context).size

    @Synchronized
    fun clear(context: Context) {
        getFile(context).delete()
    }

    private fun getFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun readAll(context: Context): MutableList<ReplyHistoryEntry> {
        val file = getFile(context)
        if (!file.exists()) return mutableListOf()
        return try {
            val json = file.readText()
            gson.fromJson(json, listType) ?: mutableListOf()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read history: ${e.message}")
            mutableListOf()
        }
    }

    private fun writeAll(context: Context, list: List<ReplyHistoryEntry>) {
        getFile(context).writeText(gson.toJson(list))
    }
}
