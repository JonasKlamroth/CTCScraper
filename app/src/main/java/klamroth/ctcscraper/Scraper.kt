package klamroth.ctcscraper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.io.File
import java.util.regex.Pattern

class Scraper {

    private val TAG = "Scraper"
    private val FILE_NAME = "puzzles.json"
    private val REMOTE_JSON_URL = "https://jonasklamroth.github.io/CTCScraper/puzzles.json"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun getNewestPuzzles(): List<VideoEntry> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching puzzles from remote JSON: $REMOTE_JSON_URL")
            val connection = Jsoup.connect(REMOTE_JSON_URL).ignoreContentType(true).execute()
            val jsonString = connection.body()
            
            // Helper class to bridge the server side script output to the local Kotlin type
            @Serializable
            data class PythonPuzzle(
                val title: String,
                val sudokuPadLinks: List<String>,
                val thumbnailUrl: String,
                val published: String,
                val videoUrl: String = "",
                val description: String = "",
                val views: String = "0",
                val rating: String = "0"
            )

            val remotePythonPuzzles = json.decodeFromString<List<PythonPuzzle>>(jsonString)
            Log.d(TAG, "Successfully fetched ${remotePythonPuzzles.size} puzzles from remote.")
            
            remotePythonPuzzles.map { py ->
                VideoEntry(
                    title = py.title,
                    puzzles = py.sudokuPadLinks.map { Puzzle(it) },
                    thumbnailUrl = py.thumbnailUrl,
                    published = py.published,
                    videoUrl = py.videoUrl,
                    description = py.description,
                    views = py.views,
                    rating = py.rating
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching or parsing remote JSON.", e)
            emptyList()
        }
    }

    suspend fun getVideoLength(videoUrl: String): Int? = withContext(Dispatchers.IO) {
        if (videoUrl.isEmpty()) return@withContext null
        return@withContext try {
            Log.d(TAG, "Fetching video page for length: $videoUrl")
            val videoDoc = Jsoup.connect(videoUrl).get()
            val html = videoDoc.html()
            val regex = Pattern.compile("\"lengthSeconds\":\"(\\d+)\"")
            val matcher = regex.matcher(html)
            if (matcher.find()) {
                val length = matcher.group(1)?.toIntOrNull()
                Log.d(TAG, "Found video length: $length seconds for $videoUrl")
                length
            } else {
                Log.w(TAG, "Could not extract video length from $videoUrl")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching video length from $videoUrl", e)
            null
        }
    }

    fun savePuzzles(context: Context, puzzles: List<VideoEntry>) {
        try {
            val jsonString = Json.encodeToString(puzzles)
            File(context.filesDir, FILE_NAME).writeText(jsonString)
            Log.d(TAG, "Successfully saved ${puzzles.size} puzzles to disk.")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving puzzles to disk", e)
        }
    }

    fun loadPuzzles(context: Context): List<VideoEntry> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val jsonString = file.readText()
            json.decodeFromString<List<VideoEntry>>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading puzzles from disk", e)
            emptyList()
        }
    }
}
