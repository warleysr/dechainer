package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.core.content.edit
import io.github.warleysr.dechainer.models.FocusSchedule
import org.json.JSONArray
import org.json.JSONObject

object FocusScheduleStorage {
    const val PREFS_NAME = "focus_schedules_prefs"
    private const val KEY_SCHEDULES = "schedules"

    fun getSchedules(context: Context): List<FocusSchedule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return decode(json)
    }

    fun saveSchedules(context: Context, schedules: List<FocusSchedule>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_SCHEDULES, encode(schedules)) }
    }

    fun addOrUpdateSchedule(context: Context, schedule: FocusSchedule) {
        val current = getSchedules(context).toMutableList()
        val index = current.indexOfFirst { it.id == schedule.id }
        if (index >= 0) {
            current[index] = schedule
        } else {
            current.add(schedule)
        }
        saveSchedules(context, current)
        FocusScheduleManager.reschedule(context)
    }

    fun deleteSchedule(context: Context, id: String) {
        val current = getSchedules(context).filterNot { it.id == id }
        saveSchedules(context, current)
        FocusScheduleManager.reschedule(context)
    }

    fun toggleSchedule(context: Context, id: String, enabled: Boolean) {
        val current = getSchedules(context).map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        saveSchedules(context, current)
        FocusScheduleManager.reschedule(context)
    }

    fun decode(json: String): List<FocusSchedule> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val daysArray = obj.getJSONArray("days")
                val days = (0 until daysArray.length()).map { daysArray.getInt(it) }.toSet()
                FocusSchedule(
                    id = obj.getString("id"),
                    daysOfWeek = days,
                    hour = obj.getInt("h"),
                    minute = obj.getInt("m"),
                    enabled = obj.optBoolean("en", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encode(schedules: List<FocusSchedule>): String {
        val array = JSONArray()
        schedules.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("days", JSONArray(s.daysOfWeek))
                put("h", s.hour)
                put("m", s.minute)
                put("en", s.enabled)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
