package klamroth.ctcscraper

import kotlinx.serialization.Serializable

@Serializable
data class VideoEntry(
    val title: String,
    val puzzles: List<Puzzle>,
    val thumbnailUrl: String,
    val published: String,
    val videoUrl: String = "",
    val description: String = "",
    val views: String = "0",
    val rating: String = "0",
    var videoLength: Int = 0,
    val isDeleted: Boolean = false
) {
    val isAnyOpened: Boolean get() = puzzles.any { it.wasOpened }
    val isAllOpened: Boolean get() = puzzles.isNotEmpty() && puzzles.all { it.wasOpened }
    val isAnySolved: Boolean get() = puzzles.any { it.markedAsSolved }
    val isAllSolved: Boolean get() = puzzles.isNotEmpty() && puzzles.all { it.markedAsSolved }
}
