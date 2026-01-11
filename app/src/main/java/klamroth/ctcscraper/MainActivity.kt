package klamroth.ctcscraper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import klamroth.ctcscraper.ui.theme.CTCScraperTheme
import androidx.core.net.toUri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

enum class SortOrder(val label: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    LENGTH_DESC("Length (Longest)"),
    LENGTH_ASC("Length (Shortest)")
}

enum class PuzzleFilter(val label: String) {
    ALL("All"),
    UNSOLVED("Unsolved"),
    OPENED("Opened"),
    UNOPENED("Unopened"),
    SHORT_VIDEOS("Short Videos <30min")
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleListScreen() {
    val context = LocalContext.current
    var videoEntries by remember { mutableStateOf(emptyList<VideoEntry>()) }
    var isLoading by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var filter by remember { mutableStateOf(PuzzleFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    val scraper = remember { Scraper() }
    val scope = rememberCoroutineScope()
    
    var selectedVideoForLinks by remember { mutableStateOf<VideoEntry?>(null) }
    val density = LocalDensity.current

    suspend fun refreshPuzzles() {
        isLoading = true
        val fetchedPuzzles = scraper.getNewestPuzzles()
        if (fetchedPuzzles.isNotEmpty()) {
            val combinedPuzzles = (videoEntries + fetchedPuzzles)
                .distinctBy { it.videoUrl }
                .sortedByDescending { it.published }
            
            videoEntries = combinedPuzzles
            scraper.savePuzzles(context, combinedPuzzles)
        }
        isLoading = false

        fetchVideoLengths(scraper, videoEntries, onUpdate = { updatedEntry ->
            videoEntries = videoEntries.map { if (it.videoUrl == updatedEntry.videoUrl) updatedEntry else it }
        })
        scraper.savePuzzles(context, videoEntries)
    }

    LaunchedEffect(Unit) {
        val savedPuzzles = scraper.loadPuzzles(context).sortedByDescending { it.published }
        if (savedPuzzles.isNotEmpty()) {
            videoEntries = savedPuzzles
        }
        refreshPuzzles()
    }

    val filteredAndSortedEntries = remember(videoEntries, sortOrder, filter, searchQuery) {
        videoEntries
            .filter { entry ->
                val matchesSearch = if (searchQuery.isEmpty()) true else {
                    entry.title.contains(searchQuery, ignoreCase = true) || 
                    entry.description.contains(searchQuery, ignoreCase = true)
                }
                val matchesFilter = when (filter) {
                    PuzzleFilter.ALL -> true
                    PuzzleFilter.UNSOLVED -> !entry.isAllSolved
                    PuzzleFilter.OPENED -> entry.isAnyOpened
                    PuzzleFilter.UNOPENED -> !entry.isAnyOpened
                    PuzzleFilter.SHORT_VIDEOS -> entry.videoLength in 1..1800
                }
                matchesSearch && matchesFilter
            }
            .let { list ->
                when (sortOrder) {
                    SortOrder.DATE_DESC -> list.sortedByDescending { it.published }
                    SortOrder.DATE_ASC -> list.sortedBy { it.published }
                    SortOrder.TITLE_ASC -> list.sortedBy { it.title }
                    SortOrder.TITLE_DESC -> list.sortedByDescending { it.title }
                    SortOrder.LENGTH_DESC -> list.sortedByDescending { it.videoLength }
                    SortOrder.LENGTH_ASC -> list.sortedBy { it.videoLength }
                }
            }
    }

    if (selectedVideoForLinks != null) {
        val currentVideo = selectedVideoForLinks!!
        AlertDialog(
            onDismissRequest = { selectedVideoForLinks = null },
            title = { Text("Select Puzzle") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(text = "Solved", style = MaterialTheme.typography.labelMedium)
                    }
                    currentVideo.puzzles.forEachIndexed { index, puzzle ->
                        Box {
                            var showPuzzleMenu by remember { mutableStateOf(false) }
                            var puzzleMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = { tapOffset ->
                                                    puzzleMenuOffset = DpOffset(
                                                        with(density) { tapOffset.x.toDp() },
                                                        with(density) { tapOffset.y.toDp() }
                                                    )
                                                    showPuzzleMenu = true
                                                },
                                                onTap = {
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
                                            )
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val label = if (puzzle.name.isNotEmpty()) puzzle.name else "Sudoku ${index + 1}"
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (puzzle.author.isNotEmpty()) {
                                            Text(
                                                text = "by " + puzzle.author,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (puzzle.wasOpened) {
                                        Text(
                                            text = "✓",
                                            color = Color(0xFF4CAF50),
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = puzzle.markedAsSolved,
                                    onCheckedChange = { isChecked ->
                                        videoEntries = videoEntries.map { video ->
                                            if (video.videoUrl == currentVideo.videoUrl) {
                                                video.copy(puzzles = video.puzzles.map { p ->
                                                    if (p.sudokuLink == puzzle.sudokuLink) p.copy(markedAsSolved = isChecked) else p
                                                })
                                            } else video
                                        }
                                        scraper.savePuzzles(context, videoEntries)
                                        // Update the dialog state
                                        selectedVideoForLinks = videoEntries.find { it.videoUrl == currentVideo.videoUrl }
                                    }
                                )
                            }

                            Box(modifier = Modifier.align(Alignment.TopStart).size(0.dp)) {
                                DropdownMenu(
                                    expanded = showPuzzleMenu,
                                    onDismissRequest = { showPuzzleMenu = false },
                                    offset = puzzleMenuOffset
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (puzzle.wasOpened) "Mark as Unopened" else "Mark as Opened") },
                                        onClick = {
                                            videoEntries = videoEntries.map { video ->
                                                if (video.videoUrl == currentVideo.videoUrl) {
                                                    video.copy(puzzles = video.puzzles.map { p ->
                                                        if (p.sudokuLink == puzzle.sudokuLink) p.copy(wasOpened = !p.wasOpened) else p
                                                    })
                                                } else video
                                            }
                                            scraper.savePuzzles(context, videoEntries)
                                            selectedVideoForLinks = videoEntries.find { it.videoUrl == currentVideo.videoUrl }
                                            showPuzzleMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (puzzle.markedAsSolved) "Mark as Unsolved" else "Mark as Solved") },
                                        onClick = {
                                            videoEntries = videoEntries.map { video ->
                                                if (video.videoUrl == currentVideo.videoUrl) {
                                                    video.copy(puzzles = video.puzzles.map { p ->
                                                        if (p.sudokuLink == puzzle.sudokuLink) p.copy(markedAsSolved = !p.markedAsSolved) else p
                                                    })
                                                } else video
                                            }
                                            scraper.savePuzzles(context, videoEntries)
                                            selectedVideoForLinks = videoEntries.find { it.videoUrl == currentVideo.videoUrl }
                                            showPuzzleMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedVideoForLinks = null }) {
                    Text("Close")
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (searchQuery.isNotEmpty()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    var showFilterMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (filter != PuzzleFilter.ALL) {
                                    Color(0xFF4CAF50) // Bright Green
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            PuzzleFilter.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = option.label,
                                                color = if (filter == option) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (filter == option) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("•", color = Color(0xFF4CAF50))
                                            }
                                        }
                                    },
                                    onClick = {
                                        filter = option
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }

                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                                tint = if (sortOrder != SortOrder.DATE_DESC) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOrder.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = option.label,
                                                color = if (sortOrder == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (sortOrder == option) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("•", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    onClick = {
                                        sortOrder = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isSearchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search title or description...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true
                )
            }

            if (filter != PuzzleFilter.ALL || searchQuery.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filter != PuzzleFilter.ALL) {
                        FilterChip(
                            selected = true,
                            onClick = { filter = PuzzleFilter.ALL },
                            label = { Text(filter.label) },
                            trailingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        FilterChip(
                            selected = true,
                            onClick = { searchQuery = "" },
                            label = { Text("Search: $searchQuery") },
                            trailingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
            
            if (filteredAndSortedEntries.isEmpty() && !isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No puzzles match your filter or search",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    TextButton(onClick = {
                        filter = PuzzleFilter.ALL
                        searchQuery = ""
                    }) {
                        Text("Clear All")
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = {
                        scope.launch {
                            refreshPuzzles()
                        }
                    }
                ) {
                    PuzzleList(
                        videoEntries = filteredAndSortedEntries.filter { !it.isDeleted },
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
                        },
                        onToggleOpened = { videoEntry ->
                            videoEntries = videoEntries.map { entry ->
                                if (entry.videoUrl == videoEntry.videoUrl) {
                                    entry.copy(puzzles = entry.puzzles.map { it.copy(wasOpened = !it.wasOpened) })
                                } else entry
                            }
                            scraper.savePuzzles(context, videoEntries)
                        },
                        onToggleSolved = { videoEntry ->
                            videoEntries = videoEntries.map { entry ->
                                if (entry.videoUrl == videoEntry.videoUrl) {
                                    entry.copy(puzzles = entry.puzzles.map { it.copy(markedAsSolved = !it.markedAsSolved) })
                                } else entry
                            }
                            scraper.savePuzzles(context, videoEntries)
                        }
                    )
                }
            }
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
    onDeleteVideo: (VideoEntry) -> Unit = {},
    onToggleOpened: (VideoEntry) -> Unit = {},
    onToggleSolved: (VideoEntry) -> Unit = {}
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
                    onDelete = { onDeleteVideo(entry) },
                    onToggleOpened = { onToggleOpened(entry) },
                    onToggleSolved = { onToggleSolved(entry) }
                )
            }
        }
    }
}

@Composable
fun VideoEntryCard(
    videoEntry: VideoEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleOpened: () -> Unit = {},
    onToggleSolved: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    val context = LocalContext.current

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { tapOffset ->
                        pressOffset = DpOffset(
                            with(density) { tapOffset.x.toDp() },
                            with(density) { tapOffset.y.toDp() }
                        )
                        showMenu = true
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = when {
                videoEntry.isAllOpened -> BorderStroke(2.dp, Color(0xFF4CAF50))
                videoEntry.isAnyOpened -> BorderStroke(2.dp, Color.Yellow)
                else -> null
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                AsyncImage(
                    model = videoEntry.thumbnailUrl,
                    contentDescription = videoEntry.title,
                    modifier = Modifier.size(120.dp, 68.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = videoEntry.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (videoEntry.puzzles.size > 1) {
                            Text(
                                text = "❐",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (videoEntry.videoLength > 0) {
                            val minutes = videoEntry.videoLength / 60
                            val seconds = videoEntry.videoLength % 60
                            Text(
                                text = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        val formattedDate = try {
                            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            val date = inputFormat.parse(videoEntry.published)
                            if (date != null) outputFormat.format(date) else videoEntry.published
                        } catch (e: Exception) {
                            videoEntry.published
                        }
                        Text(text = formattedDate, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (videoEntry.isAllSolved) {
                    Text(
                        text = "✓",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
        
        // This tiny anchor ensures the DropdownMenu positions itself correctly relative to the touch point
        Box(modifier = Modifier.align(Alignment.TopStart).size(0.dp)) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = pressOffset
            ) {
                DropdownMenuItem(
                    text = { Text("Watch on YouTube") },
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, videoEntry.videoUrl.toUri())
                        context.startActivity(intent)
                        showMenu = false
                    }
                )
                if (videoEntry.puzzles.size > 1) {
                    DropdownMenuItem(
                        text = { Text("Select Puzzle") },
                        onClick = {
                            onClick()
                            showMenu = false
                        }
                    )
                }
                if (videoEntry.puzzles.size == 1) {
                    val puzzle = videoEntry.puzzles[0]
                    DropdownMenuItem(
                        text = { Text(if (puzzle.wasOpened) "Mark as Unopened" else "Mark as Opened") },
                        onClick = {
                            onToggleOpened()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (puzzle.markedAsSolved) "Mark as Unsolved" else "Mark as Solved") },
                        onClick = {
                            onToggleSolved()
                            showMenu = false
                        }
                    )
                }
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
}

@Preview(showBackground = true)
@Composable
fun PuzzleListPreview() {
    val sampleEntry = VideoEntry(
        title = "The Miracle Sudoku",
        puzzles = listOf(Puzzle("https://sudokupad.app/some-puzzle-id", wasOpened = true, markedAsSolved = true)),
        thumbnailUrl = "",
        videoLength = 930,
        published = "2026-01-09T13:45:00+00:00",
        videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    )
    PuzzleList(videoEntries = listOf(sampleEntry))
}
