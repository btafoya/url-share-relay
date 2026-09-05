package com.example.urlsharerelay

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

object MetadataFetcher {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetch(url: String): PageMetadata {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36"
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")

            val body = response.body?.string() ?: error("Empty response")
            val finalUrl = response.request.url.toString()
            val document = Jsoup.parse(body, finalUrl)

            fun meta(property: String): String? =
                document.selectFirst("meta[property=$property]")?.attr("content")
                    ?.takeIf { it.isNotBlank() }

            fun nameMeta(name: String): String? =
                document.selectFirst("meta[name=$name]")?.attr("content")
                    ?.takeIf { it.isNotBlank() }

            fun metaAbs(property: String): String? =
                document.selectFirst("meta[property=$property]")
                    ?.takeIf { it.attr("content").isNotBlank() }
                    ?.absUrl("content")
                    ?.takeIf { it.isNotBlank() }

            fun linkAbs(rel: String): String? =
                document.selectFirst("link[rel=$rel]")
                    ?.takeIf { it.attr("href").isNotBlank() }
                    ?.absUrl("href")
                    ?.takeIf { it.isNotBlank() }

            val title = meta("og:title")
                ?: meta("twitter:title")
                ?: document.title().takeIf { it.isNotBlank() }
                ?: ""

            val description = meta("og:description")
                ?: meta("twitter:description")
                ?: nameMeta("description")
                ?: ""

            val image = metaAbs("og:image")
                ?: metaAbs("twitter:image")
                ?: linkAbs("image_src")

            val canonical = metaAbs("og:url")
                ?: linkAbs("canonical")
                ?: finalUrl

            return PageMetadata(
                title = title,
                description = description,
                imageUrl = image,
                canonicalUrl = canonical
            )
        }
    }
}
