package klamroth.ctcscraper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    var puzzles by remember { mutableStateOf(emptyList<PuzzleInfo>()) }
    val scraper = remember { Scraper() }

    LaunchedEffect(Unit) {
        // Load cached puzzles immediately and ensure they are sorted
        val savedPuzzles = scraper.loadPuzzles(context).sortedByDescending { it.published }
        if (savedPuzzles.isNotEmpty()) {
            puzzles = savedPuzzles
        }

        // Always fetch fresh puzzles from the network
        val fetchedPuzzles = scraper.getNewestPuzzles()
        if (fetchedPuzzles.isNotEmpty()) {
            // Combine existing puzzles with fetched ones, favoring existing ones to preserve state
            val combinedPuzzles = (puzzles + fetchedPuzzles)
                .distinctBy { it.sudokuPadLink }
                .sortedByDescending { it.published }
            
            puzzles = combinedPuzzles
            scraper.savePuzzles(context, combinedPuzzles)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = "Latest Puzzles",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
            PuzzleList(
                puzzles = puzzles.filter { !it.isDeleted },
                onPuzzleClick = { clickedPuzzle ->
                    puzzles = puzzles.map {
                        if (it.sudokuPadLink == clickedPuzzle.sudokuPadLink) it.copy(isOpened = true) else it
                    }
                    scraper.savePuzzles(context, puzzles)
                    val intent = Intent(Intent.ACTION_VIEW, clickedPuzzle.sudokuPadLink.toUri())
                    context.startActivity(intent)
                },
                onDeletePuzzle = { puzzleToDelete ->
                    puzzles = puzzles.map {
                        if (it.sudokuPadLink == puzzleToDelete.sudokuPadLink) it.copy(isDeleted = true) else it
                    }
                    scraper.savePuzzles(context, puzzles)
                }
            )
        }
    }
}

@Composable
fun PuzzleList(
    puzzles: List<PuzzleInfo>,
    modifier: Modifier = Modifier,
    onPuzzleClick: (PuzzleInfo) -> Unit = {},
    onDeletePuzzle: (PuzzleInfo) -> Unit = {}
) {
    if (puzzles.isEmpty()) {
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Loading...")
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(puzzles, key = { it.sudokuPadLink }) { puzzle ->
                PuzzleInfoCard(
                    puzzle = puzzle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = { onPuzzleClick(puzzle) },
                    onDelete = { onDeletePuzzle(puzzle) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PuzzleInfoCard(
    puzzle: PuzzleInfo,
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
            border = if (puzzle.isOpened) BorderStroke(2.dp, Color(0xFF4CAF50)) else null
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                AsyncImage(
                    model = puzzle.thumbnailUrl,
                    contentDescription = puzzle.title,
                    modifier = Modifier.size(120.dp, 68.dp)
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = puzzle.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val minutes = puzzle.videoLength / 60
                        val seconds = puzzle.videoLength % 60
                        Text(
                            text = String.format("%d:%02d", minutes, seconds),
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        val formattedDate = try {
                            val zdt = ZonedDateTime.parse(puzzle.published)
                            zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        } catch (e: Exception) {
                            puzzle.published
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
                    val intent = Intent(Intent.ACTION_VIEW, puzzle.videoUrl.toUri())
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
    val samplePuzzle = PuzzleInfo(
        title = "The Miracle Sudoku",
        sudokuPadLink = "https://sudokupad.app/some-puzzle-id",
        thumbnailUrl = "",
        videoLength = 930,
        published = "2026-01-09T13:45:00+00:00",
        videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        isOpened = true
    )
    CTCScraperTheme {
        PuzzleList(puzzles = listOf(samplePuzzle))
    }
}
