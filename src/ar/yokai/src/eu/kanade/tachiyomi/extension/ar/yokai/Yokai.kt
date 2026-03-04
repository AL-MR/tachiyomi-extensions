package eu.kanade.tachiyomi.extension.ar.yokai

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Response

class Yokai : ZeistManga("Yokai", "https://yokai-team.blogspot.com", "ar") {

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.use { it.asJsoup() }

        val url = getChapterFeedUrl(document)

        val result = client.newCall(GET(url, headers)).execute()
            .use { json.decodeFromStream<ZeistMangaDto>(it.body.byteStream()) }

        val originalList = result.feed?.entry
            ?.filter { it.category.orEmpty().any { category -> category.term == chapterCategory } }
            ?.map { it.toSChapter(baseUrl) }
            ?.map { chapter ->
                chapter.apply {
                    chapter_number = parseChapterNumber(name)
                }
            }
            ?: throw Exception("Failed to parse from chapter API")

        val additionalChapters = document.select("div#download > div.index-list > a").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.attr("href"))
                val text = it.text().trim()
                name = text
                chapter_number = parseChapterNumber(text)
            }
        }

        return originalList + additionalChapters
    }

    private fun parseChapterNumber(name: String): Float {
        // Extract chapter number from Arabic format "المجلد X - الفصل Y"
        // where الفصل means "Chapter" and المجلد means "Volume"
        CHAPTER_REGEX.find(name)?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
        // Fallback: try the last number in the string
        FALLBACK_NUMBER_REGEX.findAll(name).lastOrNull()?.value?.toFloatOrNull()?.let { return it }
        return -1F
    }

    companion object {
        private val CHAPTER_REGEX = Regex("""الفصل\s+(\d+(?:\.\d+)?)""")
        private val FALLBACK_NUMBER_REGEX = Regex("""\d+(?:\.\d+)?""")
    }
}
