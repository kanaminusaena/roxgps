package com.roxgps.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    // Insert data ke database Room
    // onConflict = OnConflictStrategy.IGNORE akan mengabaikan insert jika ada konflik primary key (misal ID yang sama sudah ada)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertToRoomDatabase(favorite: Favorite) : Long // Mengembalikan ID baris yang baru di-insert (atau -1 jika diabaikan)

    // Untuk update single favorite
    @Update
    suspend fun updateFavorite(favorite: Favorite) // TODO: Rename method to updateFavorite

    // Delete single favorite
    @Delete
    suspend fun deleteFavorite(favorite: Favorite) // TODO: Rename method to deleteFavorite

    // Mendapatkan semua favorit yang dimasukkan ke database Room
    // Dikembalikan sebagai Flow untuk observasi reaktif dari ViewModel/UI.
    // ORDER BY id DESC: mengurutkan dari ID terbesar ke terkecil.
    // @Transaction // <-- DIHAPUS (Tidak perlu untuk SELECT sederhana)
    @Query("SELECT * FROM Favorite ORDER BY id DESC") // <<< PERBAIKAN KRUSIAL DI SINI: 'favorite' diganti 'Favorite'
    fun getAllFavorites() : Flow<List<Favorite>>

    // Mendapatkan single favorite berdasarkan ID
    // Mengembalikan Favorite? (nullable) karena query mungkin tidak menemukan data
    // @Transaction // <-- DIHAPUS (Tidak perlu untuk SELECT tunggal sederhana)
    @Query("SELECT * FROM Favorite WHERE id = :id") // <<< PERBAIKAN KRUSIAL DI SINI: 'favorite' diganti 'Favorite'. ORDER BY id DESC juga tidak perlu untuk single result.
    suspend fun getSingleFavorite(id: Long) : Favorite? // Return type jadi Favorite? (nullable)

}
