package com.elbruz.vrvideo.model

import android.net.Uri
import kotlinx.serialization.Serializable

/**
 * Search engines selectable from Settings / the browser toolbar.
 * Each entry has a query-URL template where `{query}` is replaced
 * with the (URL-encoded) user search term.
 */
@Serializable
data class SearchEngine(
    val id: String,
    val label: String,
    val queryTemplate: String,        // https://duckduckgo.com/?q={query}
    val isDefault: Boolean = false,
) {
    fun buildSearchUrl(query: String): String {
        val safeQuery = Uri.encode(query).replace("%20", "+")
        return queryTemplate.replace("{query}", safeQuery)
    }

    companion object {
        val DEFAULT_ENGINES = listOf(
            SearchEngine(
                id = "google",
                label = "Google",
                queryTemplate = "https://www.google.com/search?q={query}",
                isDefault = true,
            ),
            SearchEngine(
                id = "duckduckgo",
                label = "DuckDuckGo",
                queryTemplate = "https://html.duckduckgo.com/html/?q={query}",
            ),
            SearchEngine(
                id = "bing",
                label = "Bing",
                queryTemplate = "https://www.bing.com/search?q={query}",
            ),
            SearchEngine(
                id = "startpage",
                label = "Startpage",
                queryTemplate = "https://www.startpage.com/sp/search?q={query}",
            ),
        )
    }
}
