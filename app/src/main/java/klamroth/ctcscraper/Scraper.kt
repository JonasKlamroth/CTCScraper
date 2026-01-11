package klamroth.ctcscraper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.regex.Pattern

class Scraper {

    private val TAG = "Scraper"
    private val FILE_NAME = "puzzles.json"
    private val REMOTE_JSON_URL = "https://jonasklamroth.github.io/CTCScraper/puzzles.json"
    private val CTC_FEED_URL = "https://www.youtube.com/feeds/videos.xml?channel_id=UCC-UOdK8-mIjxBQm_ot1T-Q"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun getNewestPuzzles(): List<VideoEntry> = withContext(Dispatchers.IO) {
        val remoteJsonPuzzles = try {
            fetchRemoteJsonPuzzles()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching remote JSON", e)
            emptyList()
        }

        val rssPuzzles = try {
            fetchRssPuzzles()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching RSS feed", e)
            emptyList()
        }

        // Combine both sources, RSS first to potentially get newer data for same videoUrl
        (rssPuzzles + remoteJsonPuzzles).distinctBy { it.videoUrl }
    }

    private fun fetchRemoteJsonPuzzles(): List<VideoEntry> {
        Log.d(TAG, "Fetching puzzles from remote JSON: $REMOTE_JSON_URL")
        val connection = Jsoup.connect(REMOTE_JSON_URL).ignoreContentType(true).execute()
        val jsonString = connection.body()

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
        Log.d(TAG, "Successfully fetched ${remotePythonPuzzles.size} puzzles from remote JSON.")

        return remotePythonPuzzles.map { py ->
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
    }

    private fun fetchRssPuzzles(): List<VideoEntry> {
        Log.d(TAG, "Fetching puzzles from RSS feed: $CTC_FEED_URL")
        val doc = Jsoup.connect(CTC_FEED_URL).parser(Parser.xmlParser()).get()
        val entries = doc.select("entry")
        Log.d(TAG, "Found ${entries.size} entries in the feed.")

        return entries.mapNotNull { entry ->
            try {
                val title = entry.select("title").text()
                val videoUrl = entry.select("link").attr("href")
                val published = entry.select("published").text()

                val mediaGroup = entry.select("media|group")
                val thumbnailUrl = mediaGroup.select("media|thumbnail").attr("url")
                val description = mediaGroup.select("media|description").text()

                val community = mediaGroup.select("media|community")
                val views = community.select("media|statistics").attr("views").ifEmpty { "0" }
                val rating = community.select("media|starRating").attr("average").ifEmpty { "0" }

                val puzzles = extractSudokuPuzzles(description)
                if (puzzles.isEmpty()) return@mapNotNull null

                VideoEntry(
                    title = title,
                    puzzles = puzzles,
                    thumbnailUrl = thumbnailUrl,
                    published = published,
                    videoUrl = videoUrl,
                    description = description,
                    views = views,
                    rating = rating
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing RSS entry", e)
                null
            }
        }
    }

    private fun extractSudokuPuzzles(description: String): List<Puzzle> {
        val puzzles = mutableListOf<Puzzle>()
        val matcher = Pattern.compile("https://sudokupad\\.app/\\S+").matcher(description)
        
        while (matcher.find()) {
            val initialUrl = matcher.group()
            var puzzleName = ""
            var puzzleAuthor = ""
            
            // Scrape the title from the original link
            try {
                Log.d(TAG, "Scraping SudokuPad link for title: $initialUrl")
                val doc = Jsoup.connect(initialUrl).get()
                val title = doc.title()
                val authorAndTitle = title.split(" by ")
                puzzleName = authorAndTitle[0].trim()
                puzzleAuthor = authorAndTitle[1].replace(Regex("\\(Sven.*\\)"), "").trim()
                Log.d(TAG, "Extracted title: $puzzleName")
                Log.d(TAG, "Extracted author: $puzzleAuthor")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scrape HTML title for $initialUrl", e)
            }

            // Convert to deeplink format
            val deeplink = initialUrl.replace("sudokupad.app/", "sudokupad.svencodes.com/puzzle/")
            puzzles.add(Puzzle(sudokuLink = deeplink, name = puzzleName, author = puzzleAuthor))
        }
        return puzzles
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
