package klamroth.ctcscraper

import kotlinx.serialization.Serializable

@Serializable
data class Puzzle(
    val sudokuLink: String,
    val name: String = "",
    val author: String = "",
    val wasOpened: Boolean = false,
    val markedAsSolved: Boolean = false
)
