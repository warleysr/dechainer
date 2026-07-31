package io.github.warleysr.dechainer.viewmodels

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.DechainerApplication
import androidx.core.content.edit
import io.github.warleysr.dechainer.BrowserRestrictionsManager
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.models.BlockedList
import org.json.JSONArray
import org.json.JSONObject

class BrowserRestrictionsViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val manager = BrowserRestrictionsManager(context)

    var browsers = mutableStateListOf<AppItem>()
        private set

    var blockedLists = mutableStateListOf<BlockedList>()
        private set

    init {
        loadBrowsers()
        loadBlockedLists()
    }

    private fun loadBrowsers() {
        val possibleBrowsers = manager.getPossibleBrowsers()
        val browserPackages = possibleBrowsers.map { it.activityInfo.packageName }.toSet()
        
        browsers.clear()
        browsers.addAll(AppRepository.getApps().filter { it.packageName in browserPackages })
    }

    private fun loadBlockedLists() {
        val prefs = context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("blocked_lists_json", null)
        blockedLists.clear()
        if (json != null) {
            parseJsonToList(json, blockedLists)
        }
    }

    private fun parseJsonToList(json: String, targetList: MutableList<BlockedList>) {
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val sitesArray = obj.getJSONArray("sites")
                val sites = mutableListOf<String>()
                for (j in 0 until sitesArray.length()) {
                    sites.add(sitesArray.getString(j))
                }
                targetList.add(BlockedList(id, title, sites))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun saveOrUpdateList(id: String?, title: String, sitesString: String) {
        val sites = sitesString.lines().filter { it.isNotBlank() }.map { it.trim() }
        val newListId = id ?: System.currentTimeMillis().toString()
        val newList = BlockedList(newListId, title, sites)

        val index = blockedLists.indexOfFirst { it.id == newListId }
        if (index != -1) {
            blockedLists[index] = newList
        } else {
            blockedLists.add(newList)
        }

        saveBlockedLists()
        manager.applyRestrictions()
    }

    fun removeList(id: String) {
        if (blockedLists.removeIf { it.id == id }) {
            saveBlockedLists()
            manager.applyRestrictions()
        }
    }

    private fun saveBlockedLists() {
        val prefs = context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val array = JSONArray()
        blockedLists.forEach { list ->
            val obj = JSONObject().apply {
                put("id", list.id)
                put("title", list.title)
                put("sites", JSONArray(list.sites))
            }
            array.put(obj)
        }
        prefs.edit { putString("blocked_lists_json", array.toString()) }
    }
}
