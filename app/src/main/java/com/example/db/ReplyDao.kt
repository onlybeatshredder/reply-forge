package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyDao {
    @Query("SELECT * FROM replies ORDER BY timestamp DESC LIMIT 50")
    fun getAllReplies(): Flow<List<ReplyEntity>>

    @Query("SELECT * FROM replies WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteReplies(): Flow<List<ReplyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReply(reply: ReplyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplies(replies: List<ReplyEntity>)

    @Query("UPDATE replies SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM replies WHERE id = :id")
    suspend fun deleteReply(id: String)

    @Query("DELETE FROM replies WHERE isFavorite = 0")
    suspend fun clearHistory()
}
