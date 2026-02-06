package io.github.warleysr.dechainer.viewmodels

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class BrowserApp(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isEnabled: Boolean
)

data class BlockedList(
    val id: String,
    val title: String,
    val sites: List<String>
)

class BrowserRestrictionsViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val packageManager = context.packageManager
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminName = ComponentName(context, DechainerDeviceAdminReceiver::class.java)

    var browsers = mutableStateListOf<BrowserApp>()
        private set

    var blockedLists = mutableStateListOf<BlockedList>()
        private set

    init {
        loadBrowsers()
        loadBlockedLists()
    }

    private fun loadBrowsers() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_BROWSER)
        }
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        browsers.clear()
        resolveInfos.forEach { info ->
            val packageName = info.activityInfo.packageName
            val isHidden = try { 
                dpm.isApplicationHidden(adminName, packageName) 
            } catch (_: Exception) { false }
            
            browsers.add(
                BrowserApp(
                    name = info.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    icon = info.loadIcon(packageManager),
                    isEnabled = !isHidden
                )
            )
        }
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        applyRestrictionsToBrowsers()
    }

    fun removeList(id: String) {
        if (blockedLists.removeIf { it.id == id }) {
            saveBlockedLists()
            applyRestrictionsToBrowsers()
        }
    }

    private fun saveBlockedLists() {
        val prefs = context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val key = "blocked_lists_json"
        
        val array = JSONArray()
        blockedLists.forEach { list ->
            val obj = JSONObject()
            obj.put("id", list.id)
            obj.put("title", list.title)
            val sitesArray = JSONArray()
            list.sites.forEach { sitesArray.put(it) }
            obj.put("sites", sitesArray)
            array.put(obj)
        }
        prefs.edit { putString(key, array.toString()) }
    }

    fun applyRestrictionsToBrowsers() {
        val allSites = blockedLists.flatMap { it.sites }.distinct()
        
        val restrictions = Bundle().apply {
            putStringArray("URLBlocklist", allSites.toTypedArray())
        }
        
        browsers.forEach { browser ->
            try {
                dpm.setApplicationRestrictions(adminName, browser.packageName, restrictions)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
