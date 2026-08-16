package io.github.warleysr.dechainer.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONObject
import org.json.JSONException

data class AppRatingInfo(
    val packageName: String,
    val title: String?,
    val contentRating: String?,
    val descriptorLines: List<String>,
    val descriptorsAvailable: Boolean,
    val hasExplicitContent: Boolean
)

object PlayStoreRatingFetcher {

    private val BLOCKED_CONTENTS = arrayOf("Nudity", "Explicit Sex", "Sexual Content")

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    private const val HL = "en"
    private const val GL = "BR"

    fun fetch(packageName: String): AppRatingInfo {
        val url = "https://play.google.com/store/apps/details?id=$packageName&hl=$HL&gl=$GL"

        val doc = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(15_000)
            .get()

        var title: String? = null
        var contentRating: String? = null

        for (script in doc.select("script[type=application/ld+json]")) {
            val raw = script.data()
            if (raw.isBlank()) continue
            try {
                val json = JSONObject(raw)
                if (json.has("contentRating")) contentRating = json.optString("contentRating", null)
                if (json.has("name")) title = json.optString("name", null)
                if (contentRating != null) break
            } catch (e: JSONException) {
                continue
            }
        }

        val descriptors = extractContentRatingDescriptors(doc)
        val hasExplicitContent = descriptors.any { desc ->
            BLOCKED_CONTENTS.any { desc.contains(it, ignoreCase = true) }
        }

        return AppRatingInfo(
            packageName = packageName,
            title = title,
            contentRating = contentRating,
            descriptorLines = descriptors,
            descriptorsAvailable = descriptors.isNotEmpty(),
            hasExplicitContent = hasExplicitContent
        )
    }

    private fun extractContentRatingDescriptors(doc: Document): List<String> {
        val img = doc.select("img[itemprop=image][alt=Content rating]")
            .firstOrNull { it.nextElementSibling()?.tagName() == "div" }
            ?: return emptyList()

        val box = img.nextElementSibling() ?: return emptyList()
        val children = box.children()
        if (children.size <= 1) return emptyList()

        return children
            .drop(1)
            .filter { it.tagName() == "div" }
            .map { it.text() }
            .filter { it.isNotBlank() }
    }
}