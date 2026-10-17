package com.buddhanag.luminux.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** An app-only album. The title is kept as typed; [Album.title] lowercases it. */
@Entity(tableName = "Album")
data class AlbumRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val createdAt: Long)

@Entity(
    tableName = "AlbumMember",
    primaryKeys = ["albumId", "mediaStoreId"],
    foreignKeys = [ForeignKey(AlbumRow::class, ["id"], ["albumId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mediaStoreId")],
)
data class AlbumMemberRow(val albumId: Long, val mediaStoreId: Long, val addedAt: Long)

@Dao
interface AlbumDao {
    @Query("SELECT * FROM Album ORDER BY createdAt")
    fun albums(): Flow<List<AlbumRow>>

    @Query("SELECT * FROM AlbumMember")
    fun members(): Flow<List<AlbumMemberRow>>

    @Insert
    suspend fun insert(album: AlbumRow): Long

    @Query("SELECT EXISTS(SELECT 1 FROM Album WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMembers(rows: List<AlbumMemberRow>)

    @Query("UPDATE Album SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM Album WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM AlbumMember WHERE albumId = :albumId AND mediaStoreId IN (:ids)")
    suspend fun removeMembers(albumId: Long, ids: List<Long>)

    @Query("DELETE FROM AlbumMember WHERE mediaStoreId IN (:ids)")
    suspend fun deleteMembersFor(ids: List<Long>)

    @Transaction
    suspend fun create(title: String, ids: List<Long>, now: Long): Long =
        insert(AlbumRow(title = title, createdAt = now)).also { id -> addMembers(ids.map { AlbumMemberRow(id, it, now) }) }

    /** Adding to an album that is gone does nothing, like iOS (and avoids the foreign-key failure). */
    @Transaction
    suspend fun addIfExists(albumId: Long, ids: List<Long>, now: Long) {
        if (exists(albumId)) addMembers(ids.map { AlbumMemberRow(albumId, it, now) })
    }
}

@Database(entities = [AlbumRow::class, AlbumMemberRow::class], version = 1)
abstract class LuminuxDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
}
