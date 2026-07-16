package com.boom.anydown.util

import android.content.Context
import com.boom.anydown.model.DownloadedItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object HistoryManager {
    private const val PREFS = "anydown_history"
    private const val KEY = "items"

    fun save(context: Context, items: List<DownloadedItem>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, Gson().toJson(items)).apply()
    }

    fun load(context: Context): List<DownloadedItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<DownloadedItem>>() {}.type
        return try { Gson().fromJson(json, type) } catch (e: Exception) { emptyList() }
    }
}
