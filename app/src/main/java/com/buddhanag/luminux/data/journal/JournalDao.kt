package com.buddhanag.luminux.data.journal

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Your title and note for an entry; null fields fall back to the generated title / no note. */
@Entity(tableName = "JournalNote")
data class JournalNoteEntity(@PrimaryKey val entryId: String, val title: String? = null, val text: String? = null)

/** A reverse-geocoded name, keyed by the ~5 km cell ([PlaceGrouping.CELL]). */
@Entity(tableName = "PlaceName")
data class PlaceNameEntity(@PrimaryKey val cellKey: String, val name: String)

/** A photo's location read once from its file; re-read when [dateModified] changes. Null lat/lon = none in the file. */
@Entity(tableName = "PhotoLocation")
data class PhotoLocationEntity(@PrimaryKey val id: Long, val dateModified: Long, val latitude: Double?, val longitude: Double?)

@Dao
interface JournalDao {
    @Query("SELECT * FROM JournalNote")
    fun notes(): Flow<List<JournalNoteEntity>>

    @Query("SELECT * FROM JournalNote WHERE entryId = :entryId")
    suspend fun note(entryId: String): JournalNoteEntity?

    @Upsert
    suspend fun upsert(note: JournalNoteEntity)

    @Query("SELECT * FROM PlaceName")
    fun placeNames(): Flow<List<PlaceNameEntity>>

    @Upsert
    suspend fun upsert(name: PlaceNameEntity)

    @Query("SELECT * FROM PhotoLocation")
    suspend fun locations(): List<PhotoLocationEntity>

    @Upsert
    suspend fun upsertLocations(rows: List<PhotoLocationEntity>)
}
