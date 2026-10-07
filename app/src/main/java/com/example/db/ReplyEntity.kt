package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "replies")
data class ReplyEntity(
    @PrimaryKey
    val id: String,
    val toneKey: String,
    val text: String,
    val timestamp: Long,
    val contextSnippet: String,
    val isFavorite: Boolean = false
)
