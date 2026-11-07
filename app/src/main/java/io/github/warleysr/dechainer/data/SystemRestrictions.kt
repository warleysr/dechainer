package io.github.warleysr.dechainer.data

import android.content.Context
import android.os.UserManager

object SystemRestrictions {
    val recommendedKeys = listOf(
        UserManager.DISALLOW_CONFIG_VPN,
        UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
    )

    // Additional known vendor / Knox / OEM restrictions
    val additionalKeys = listOf(
        "disallow_biometric",
        "no_camera",
        "no_wallpaper",
        "no_add_clone_profile",
        "no_config_wifi_private",
        "no_config_wifi_shared",
        "no_non_market_app_by_knox"
    )

    val restrictionFieldNames: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        val fields = UserManager::class.java.declaredFields + UserManager::class.java.fields
        fields
            .filter { field ->
                java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                        field.type == String::class.java &&
                        (field.name.startsWith("DISALLOW_") || field.name.startsWith("ALLOW_") || field.name.startsWith("ENSURE_"))
            }
            .forEach { field ->
                try {
                    field.isAccessible = true
                    val constantVal = field.get(null) as? String
                    if (constantVal != null) {
                        map[constantVal] = field.name.lowercase()
                    }
                } catch (_: Exception) {}
            }
        additionalKeys.forEach { key ->
            map.putIfAbsent(key, key.lowercase())
        }
        map
    }

    val otherKeys: List<String> by lazy {
        val keys = (restrictionFieldNames.keys + additionalKeys).distinct()
        keys.filter { it !in recommendedKeys }.sorted()
    }

    val allKeys: List<String> by lazy {
        (recommendedKeys + otherKeys).distinct()
    }

    fun resourceNamesFor(key: String): List<String> {
        val candidates = mutableListOf<String>()
        restrictionFieldNames[key]?.let { candidates.add(it) }
        candidates.add(key.lowercase())
        if (key.startsWith("no_")) {
            candidates.add("disallow_" + key.removePrefix("no_"))
        } else if (key.startsWith("disallow_")) {
            candidates.add("no_" + key.removePrefix("disallow_"))
        }
        return candidates.distinct()
    }

    fun resourceNameFor(key: String): String? = resourceNamesFor(key).firstOrNull()

    fun resolveStringRes(context: Context, key: String): Int? {
        val candidates = resourceNamesFor(key)
        return candidates.firstNotNullOfOrNull { resName ->
            context.resources.getIdentifier(resName, "string", context.packageName).takeIf { it != 0 }
        }
    }

    fun normalizeForSearch(text: String): String {
        val temp = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "").lowercase()
    }
}
