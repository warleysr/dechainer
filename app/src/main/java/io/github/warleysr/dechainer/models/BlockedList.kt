package io.github.warleysr.dechainer.models

data class BlockedList(
    val id: String,
    val title: String,
    val sites: List<String>
)
