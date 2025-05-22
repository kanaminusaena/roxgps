package com.roxgps.room

// TODO: Import Entity yang akan diakses oleh DAO
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// TODO: Import Flow jika DAO mengekspos hook secara reaktif
// import kotlinx.coroutines.flow.Flow // Import Flow

// =====================================================================
// LocationDao (Data Access Object untuk LocationEntity)
// =====================================================================

/**
 * Room DAO (Data Access Object) untuk tabel 'locations'.
 * Mendefinisikan metode untuk berinteraksi dengan hook lokasi di database.
 */
@Dao // Tandai sebagai DAO
interface LocationDao {

    /**
     * Menyimpan satu objek LocationEntity baru ke tabel 'locations'.
     * Menggunakan OnConflictStrategy.IGNORE: jika ada konflik (misal ID sama), abaikan insert baru.
     * Metode ini adalah suspend fun karena operasi database harus dijalankan di Coroutine.
     *
     * @param location Objek LocationEntity yang akan disimpan.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE) // Anotasi Insert
    suspend fun insertLocation(location: LocationEntity) // <<< Metode untuk menyimpan 1 lokasi

    /**
     * Mengambil semua objek LocationEntity dari tabel 'locations' secara reaktif.
     * Mengembalikan Flow<List<LocationEntity>> yang akan memancarkan (emit)
     * daftar lokasi setiap kali hook di tabel berubah.
     *
     * @return Flow yang memancarkan daftar semua LocationEntity.
     */
    @Query("SELECT * FROM locations ORDER BY timestamp ASC") // <<< Query SQL untuk mengambil semua hook, diurutkan berdasarkan timestamp
    fun getAllLocations(): Flow<List<LocationEntity>> // <<< Deklarasi metode yang mengembalikan Flow

    // TODO: Tambahkan metode lain jika diperlukan (misal, menghapus lokasi).
    /*
    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations() // Menghapus semua lokasi
    */
}