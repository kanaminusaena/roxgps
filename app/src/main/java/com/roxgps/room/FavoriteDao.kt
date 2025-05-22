package com.roxgps.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    // Insert hook ke database Room
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertToRoomDatabase(favorite: Favorite) : Long

    // Untuk update single favorite
    @Update
    suspend fun updateFavorite(favorite: Favorite)

    // Delete single favorite (sudah ada)
    @Delete
    suspend fun deleteSingleFavorite(favorite: Favorite) // <<< Fungsi untuk menghapus SATU item

    // Delete ALL favorites (Fungsi BARU)
    @Query("DELETE FROM Favorite") // Perintah SQL untuk menghapus semua hook
    suspend fun deleteAllFavorites() // <<< Fungsi untuk menghapus SEMUA item

    // Mendapatkan semua favorit
    @Query("SELECT * FROM Favorite ORDER BY id DESC")
    fun getAllFavorites() : Flow<List<Favorite>>

    // Mendapatkan single favorite berdasarkan ID
    @Query("SELECT * FROM Favorite WHERE id = :id")
    suspend fun getSingleFavorite(id: Long) : Favorite?

}