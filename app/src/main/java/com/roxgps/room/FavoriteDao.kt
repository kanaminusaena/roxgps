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
    suspend fun updateUserDetails(favorite: Favorite)

    // Delete single favorite
    @Delete
    suspend fun deleteSingleFavorite(favorite: Favorite)

    // Mendapatkan semua favorit yang dimasukkan ke database Room
    // Dikembalikan sebagai Flow untuk observasi reaktif dari ViewModel/UI.
    // ORDER BY id DESC: mengurutkan dari ID terbesar ke terkecil.
    @Transaction // Anotasi Transaction mungkin tidak diperlukan untuk query SELECT sederhana ini, tapi tidak berbahaya.
    @Query("SELECT * FROM favorite ORDER BY id DESC")
    fun getAllFavorites() : Flow<List<Favorite>>

    // Mendapatkan single favorite berdasarkan ID
    // Mengembalikan Favorite? (nullable) karena query mungkin tidak menemukan data
    @Transaction // Anotasi Transaction mungkin tidak diperlukan untuk query SELECT tunggal sederhana ini, tapi tidak berbahaya.
    @Query("SELECT * FROM favorite WHERE id = :id ORDER BY id DESC") // ORDER BY juga tidak perlu untuk single result, tapi biarkan saja.
    suspend fun getSingleFavorite(id: Long) : Favorite? // <-- PERBAIKAN KRUSIAL: Return type jadi Favorite? (nullable)

}
