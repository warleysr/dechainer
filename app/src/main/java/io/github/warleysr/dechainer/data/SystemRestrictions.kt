package io.github.warleysr.dechainer.data

import android.os.UserManager

object SystemRestrictions {
    val recommendedKeys = listOf(
        UserManager.DISALLOW_CONFIG_VPN,
        UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
    )

    val restrictionFieldNames: Map<String, String> by lazy {
        UserManager::class.java.fields
            .filter { field ->
                java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                        java.lang.reflect.Modifier.isFinal(field.modifiers) &&
                        (field.name.startsWith("DISALLOW_") || field.name.startsWith("ALLOW_"))
            }
            .associate { (it.get(null) as String) to it.name.lowercase() }
    }

    val otherKeys: List<String> by lazy {
        restrictionFieldNames.keys
            .filter { it !in recommendedKeys }
            .sorted()
    }

    val allKeys: List<String> by lazy {
        (recommendedKeys + otherKeys).distinct()
    }

    fun resourceNameFor(key: String): String? = restrictionFieldNames[key]
}
