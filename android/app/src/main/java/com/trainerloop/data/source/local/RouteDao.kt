package com.trainerloop.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RouteEntity)

    @Query("SELECT * FROM routes ORDER BY importedAt DESC")
    fun getAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getById(id: String): RouteEntity?

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteById(id: String)
}
