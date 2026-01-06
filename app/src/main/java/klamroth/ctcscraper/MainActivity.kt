package klamroth.ctcscraper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import klamroth.ctcscraper.ui.theme.CTCScraperTheme
import androidx.core.net.toUri

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
    val numPuzzles = 15
    var puzzles by remember { mutableStateOf(emptyList<PuzzleInfo>()) }
    val scraper = remember { Scraper() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        puzzles = scraper.getNewestPuzzles(numPuzzles)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = "Latest Puzzles",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
            PuzzleList(
                puzzles = puzzles,
                onPuzzleClick = { puzzle ->
                    val intent = Intent(Intent.ACTION_VIEW, puzzle.sudokuPadLink.toUri())
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun PuzzleList(puzzles: List<PuzzleInfo>, modifier: Modifier = Modifier, onPuzzleClick: (PuzzleInfo) -> Unit = {}) {
    if (puzzles.isEmpty()) {
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Loading...")
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(puzzles) { puzzle ->
                PuzzleInfoCard(
                    puzzle = puzzle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = { onPuzzleClick(puzzle) }
                )
            }
        }
    }
}

@Composable
fun PuzzleInfoCard(puzzle: PuzzleInfo, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = puzzle.thumbnailUrl,
                contentDescription = puzzle.title,
                modifier = Modifier.size(120.dp, 68.dp) // 16:9 aspect ratio
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = puzzle.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                val minutes = puzzle.videoLength / 60
                val seconds = puzzle.videoLength % 60
                Text(text = String.format("%d:%02d", minutes, seconds), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
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
        videoLength = 930
    )
    CTCScraperTheme {
        PuzzleList(puzzles = listOf(samplePuzzle))
    }
}
