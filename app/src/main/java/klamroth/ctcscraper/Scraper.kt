package klamroth.ctcscraper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File

@Serializable
data class PuzzleInfo(
    val title: String,
    val sudokuPadLink: String,
    val thumbnailUrl: String,
    val videoLength: Int,
    val published: String,
    val videoUrl: String = "",
    val isDeleted: Boolean = false,
    val isOpened: Boolean = false
)

class Scraper {

    private val TAG = "Scraper"
    private val FILE_NAME = "puzzles.json"
    private val CTCChannelUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCC-UOdK8-mIjxBQm_ot1T-Q"

    suspend fun getNewestPuzzles(): List<PuzzleInfo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching newest puzzles from RSS feed: $CTCChannelUrl")
            val doc = Jsoup.connect(CTCChannelUrl).parser(Parser.xmlParser()).get()

            val entries = doc.select("entry")
            Log.d(TAG, "Found ${entries.size} entries in the feed.")

            entries.map { entry ->
                async {
                    try {
                        val title = entry.select("title").text()
                        val videoUrl = entry.select("link").attr("href")
                        val thumbnailUrl = entry.select("media|thumbnail").attr("url")
                        val description = entry.select("media|description").text()
                        val published = entry.select("published").text()

                        val videoLength = getVideoLength(videoUrl)
                        val initialSudokuPadLink = extractSudokuPadLink(description)

                        if (initialSudokuPadLink != null && videoLength != null) {
                            val deeplink = getSudokuPadDeeplink(initialSudokuPadLink)
                            PuzzleInfo(
                                title = title,
                                sudokuPadLink = deeplink,
                                thumbnailUrl = thumbnailUrl,
                                videoLength = videoLength,
                                published = published,
                                videoUrl = videoUrl
                            )
                        } else {
                            if (initialSudokuPadLink == null) Log.w(TAG, "No SudokuPad link found for '$title'")
                            if (videoLength == null) Log.w(TAG, "Could not find video length for '$title'")
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing entry: ${entry.select("title").text()}", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching or parsing RSS feed.", e)
            emptyList()
        }
    }

    private fun extractSudokuPadLink(description: String): String? {
        val regex = "https://sudokupad\\.app/\\S+".toRegex()
        return regex.find(description)?.value
    }

    private fun getVideoLength(videoUrl: String): Int? {
        return try {
            Log.d(TAG, "Fetching video page for length: $videoUrl")
            val videoDoc = Jsoup.connect(videoUrl).get()
            val html = videoDoc.html()
            val regex = "\"lengthSeconds\":\"(\\d+)\"".toRegex()
            val match = regex.find(html)?.groups?.get(1)?.value
            val length = match?.toIntOrNull()
            if (length != null) {
                Log.d(TAG, "Found video length: $length seconds for $videoUrl")
            } else {
                Log.w(TAG, "Could not extract video length from $videoUrl")
            }
            length
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching video length from $videoUrl", e)
            null
        }
    }

    private fun getSudokuPadDeeplink(initialUrl: String): String {
        return initialUrl.replace("sudokupad.app/", "sudokupad.svencodes.com/puzzle/")
    }

    fun savePuzzles(context: Context, puzzles: List<PuzzleInfo>) {
        try {
            val json = Json.encodeToString(puzzles)
            File(context.filesDir, FILE_NAME).writeText(json)
            Log.d(TAG, "Successfully saved ${puzzles.size} puzzles to disk.")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving puzzles to disk", e)
        }
    }

    fun loadPuzzles(context: Context): List<PuzzleInfo> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText()
            Json.decodeFromString<List<PuzzleInfo>>(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading puzzles from disk", e)
            emptyList()
        }
    }
}
