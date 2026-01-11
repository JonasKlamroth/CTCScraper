package klamroth.ctcscraper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import klamroth.ctcscraper.ui.theme.CTCScraperTheme
import androidx.core.net.toUri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CTCScraperTheme {
                PuzzleListScreen()
            }
        }
    }
}

@Composable
fun PuzzleListScreen() {
    val context = LocalContext.current
    var videoEntries by remember { mutableStateOf(emptyList<VideoEntry>()) }
    var isLoading by remember { mutableStateOf(false) }
    val scraper = remember { Scraper() }
    
    var selectedVideoForLinks by remember { mutableStateOf<VideoEntry?>(null) }

    LaunchedEffect(Unit) {
        // Load cached puzzles immediately
        val savedPuzzles = scraper.loadPuzzles(context).sortedByDescending { it.published }
        if (savedPuzzles.isNotEmpty()) {
            videoEntries = savedPuzzles
        }

        // Try to load new puzzles in the background
        isLoading = true
        val fetchedPuzzles = scraper.getNewestPuzzles()
        if (fetchedPuzzles.isNotEmpty()) {
            // Combine existing puzzles with fetched ones, favoring existing ones to preserve state
            val combinedPuzzles = (videoEntries + fetchedPuzzles)
                .distinctBy { it.videoUrl }
                .sortedByDescending { it.published }
            
            videoEntries = combinedPuzzles
            scraper.savePuzzles(context, combinedPuzzles)
        }
        isLoading = false

        // Fetch video length for puzzles that don't have it yet and save once finished
        fetchVideoLengths(scraper, videoEntries, onUpdate = { updatedEntry ->
            videoEntries = videoEntries.map { if (it.videoUrl == updatedEntry.videoUrl) updatedEntry else it }
        })
        scraper.savePuzzles(context, videoEntries)
    }

    if (selectedVideoForLinks != null) {
        val currentVideo = selectedVideoForLinks!!
        AlertDialog(
            onDismissRequest = { selectedVideoForLinks = null },
            title = { Text("Select Puzzle") },
            text = {
                Column {
                    currentVideo.puzzles.forEachIndexed { index, puzzle ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    videoEntries = videoEntries.map { video ->
                                        if (video.videoUrl == currentVideo.videoUrl) {
                                            video.copy(puzzles = video.puzzles.map { p ->
                                                if (p.sudokuLink == puzzle.sudokuLink) p.copy(wasOpened = true) else p
                                            })
                                        } else video
                                    }
                                    scraper.savePuzzles(context, videoEntries)
                                    val intent = Intent(Intent.ACTION_VIEW, puzzle.sudokuLink.toUri())
                                    context.startActivity(intent)
                                    selectedVideoForLinks = null
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Puzzle ${index + 1}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (puzzle.wasOpened) {
                                Text(
                                    text = "✓",
                                    color = Color(0xFF4CAF50),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedVideoForLinks = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Latest Puzzles",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            PuzzleList(
                videoEntries = videoEntries.filter { !it.isDeleted },
                onVideoClick = { clickedVideo ->
                    if (clickedVideo.puzzles.size > 1) {
                        selectedVideoForLinks = clickedVideo
                    } else {
                        val puzzle = clickedVideo.puzzles.firstOrNull()
                        if (puzzle != null) {
                            videoEntries = videoEntries.map { video ->
                                if (video.videoUrl == clickedVideo.videoUrl) {
                                    video.copy(puzzles = video.puzzles.map { p ->
                                        if (p.sudokuLink == puzzle.sudokuLink) p.copy(wasOpened = true) else p
                                    })
                                } else video
                            }
                            scraper.savePuzzles(context, videoEntries)
                            val intent = Intent(Intent.ACTION_VIEW, puzzle.sudokuLink.toUri())
                            context.startActivity(intent)
                        }
                    }
                },
                onDeleteVideo = { videoToDelete ->
                    videoEntries = videoEntries.map {
                        if (it.videoUrl == videoToDelete.videoUrl) it.copy(isDeleted = true) else it
                    }
                    scraper.savePuzzles(context, videoEntries)
                }
            )
        }
    }
}

suspend fun fetchVideoLengths(
    scraper: Scraper,
    videoEntries: List<VideoEntry>,
    onUpdate: (VideoEntry) -> Unit
) = coroutineScope {
    videoEntries.filter { it.videoLength == 0 && it.videoUrl.isNotEmpty() }.map { entry ->
        async {
            val length = scraper.getVideoLength(entry.videoUrl)
            if (length != null && length > 0) {
                onUpdate(entry.copy(videoLength = length))
            }
        }
    }.awaitAll()
}

@Composable
fun PuzzleList(
    videoEntries: List<VideoEntry>,
    modifier: Modifier = Modifier,
    onVideoClick: (VideoEntry) -> Unit = {},
    onDeleteVideo: (VideoEntry) -> Unit = {}
) {
    if (videoEntries.isEmpty()) {
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Loading...")
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(videoEntries, key = { it.videoUrl }) { entry ->
                VideoEntryCard(
                    videoEntry = entry,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = { onVideoClick(entry) },
                    onDelete = { onDeleteVideo(entry) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoEntryCard(
    videoEntry: VideoEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            border = when {
                !videoEntry.isAnyOpened -> null
                videoEntry.isAllOpened -> BorderStroke(2.dp, Color(0xFF4CAF50))
                else -> BorderStroke(2.dp, Color.Yellow)
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                AsyncImage(
                    model = videoEntry.thumbnailUrl,
                    contentDescription = videoEntry.title,
                    modifier = Modifier.size(120.dp, 68.dp)
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = videoEntry.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (videoEntry.videoLength > 0) {
                            val minutes = videoEntry.videoLength / 60
                            val seconds = videoEntry.videoLength % 60
                            Text(
                                text = String.format("%d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        val formattedDate = try {
                            val zdt = ZonedDateTime.parse(videoEntry.published)
                            zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        } catch (e: Exception) {
                            videoEntry.published
                        }
                        Text(text = formattedDate, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Watch on YouTube") },
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, videoEntry.videoUrl.toUri())
                    context.startActivity(intent)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    onDelete()
                    showMenu = false
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PuzzleListPreview() {
    val sampleEntry = VideoEntry(
        title = "The Miracle Sudoku",
        puzzles = listOf(Puzzle("https://sudokupad.app/some-puzzle-id", wasOpened = true)),
        thumbnailUrl = "",
        videoLength = 930,
        published = "2026-01-09T13:45:00+00:00",
        videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    )
    PuzzleList(videoEntries = listOf(sampleEntry))
}
