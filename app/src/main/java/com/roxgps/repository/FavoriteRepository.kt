package com.roxgps.repository


// Import yang dibutuhkan
import androidx.annotation.WorkerThread // Anotasi ini lebih ke info, bukan mandatory kalau sudah suspend Room
import com.roxgps.room.Favorite // Import kelas Favorite
import com.roxgps.room.FavoriteDao
import kotlinx.coroutines.flow.Flow // Untuk Flow
import kotlinx.coroutines.Dispatchers // Untuk Dispatchers
import kotlinx.coroutines.withContext // Untuk withContext
import javax.inject.Inject // Untuk Inject

class FavoriteRepository @Inject constructor(private val favoriteDao: FavoriteDao) {

    // Method untuk mendapatkan semua favorit, diekspos sebagai Flow
    // ViewModel akan mengamati Flow ini.
    val getAllFavorites: Flow<List<Favorite>>
    get() = favoriteDao.getAllFavorites() // Langsung dari DAO

    // Method asli untuk insert Favorite (jika dibutuhkan insert objek Favorite lengkap dari luar)
    // @Suppress("RedundantSuspendModifier") // Anotasi ini dihapus karena methodnya memang perlu suspend
    // @WorkerThread // Anotasi ini bisa dipertahankan atau dihapus, Room suspend handle threading
    suspend fun addNewFavorite(favorite: Favorite) : Long {
        // Memanggil method insert di DAO
        return favoriteDao.insertToRoomDatabase(favorite)
    }

    // Method untuk menghapus favorit
    suspend fun deleteFavorite(favorite: Favorite) {
      // Memanggil method delete di DAO
      favoriteDao.deleteSingleFavorite(favorite)
    }

    // Method untuk mendapatkan satu favorit berdasarkan ID
    // Mengembalikan Favorite? (nullable) sesuai perbaikan di FavoriteDao
    // Method ini dipakai internal di Repository ini untuk mencari slot kosong.
    suspend fun getSingleFavorite(id: Long) : Favorite? {
       // Memanggil method get single di DAO
       return favoriteDao.getSingleFavorite(id) // DAO sekarang mengembalikan Favorite?
    }

    // =====================================================================
    // Method BARU: Menyimpan favorit, termasuk logic mencari slot ID kosong
    // Logic ini dipindahkan dari ViewModel ke Repository.
    // Method ini akan dipanggil dari ViewModel.
    // =====================================================================
    /**
     * Saves a new favorite by finding the next available sequential integer ID slot.
     * The logic iterates through IDs starting from 0 and finds the first ID
     * for which no favorite currently exists in the database.
     * Once a slot is found, it creates a Favorite object with that ID and
     * inserts it into the database.
     *
     * @param address The address string for the favorite.
     * @param lat The latitude coordinate.
     * @param lon The longitude coordinate.
     */
    // Method ini suspend karena melakukan operasi database.
    // Tidak perlu @WorkerThread di sini, karena Room suspend function otomatis handle threading
    // ke background thread (biasanya IO).
    suspend fun saveFavorite(address: String, lat: Double, lon: Double) {
        // Pastikan logic ini berjalan di background thread (Room suspend function sudah urus,
        // tapi withContext Dispatchers.IO bisa dipakai untuk blok kode non-Room yang butuh IO).
        // withContext(Dispatchers.IO) { // Bisa ditambahkan kalau logic di dalam sini complex
            var slotId = 0L // Mulai dari ID 0 (menggunakan Long karena ID di Entity Long)
            // Loop untuk mencari slot ID yang kosong di database
            while (true) {
                // Panggil getSingleFavorite dari Repository ini (yang memanggil DAO)
                // DAO sekarang mengembalikan Favorite? (nullable), jadi kita bisa cek == null
                val existingFavorite = getSingleFavorite(slotId) // Cek apakah ada favorit dengan ID ini

                // Jika tidak ada favorit dengan slotId ini, berarti slot kosong!
                if (existingFavorite == null) {
                    // Slot kosong ditemukan, break dari loop
                    break
                } else {
                    // Slot sudah terisi, coba ID berikutnya
                    slotId++
                }
                // Opsi: Tambahkan batasan agar loop tidak berjalan selamanya jika semua slot penuh
                // const val MAX_FAVORITE_SLOTS = 100 // Definisikan konstanta di atas kelas
                // if (slotId > MAX_FAVORITE_SLOTS) {
                //    // Handle error: misalnya lempar Exception atau kembalikan indikator gagal
                //    throw Exception("Failed to find an empty favorite slot after checking $MAX_FAVORITE_SLOTS IDs.")
                // }
            }

            // Setelah slotId kosong ditemukan, buat objek Favorite baru dengan ID tersebut
            // Properti address, lat, lng di Favorite Entity lo nullable, jadi aman passing nilai non-null juga.
            val newFavorite = Favorite(id = slotId, address = address, lat = lat, lng = lon)

            // Panggil method insertToRoomDatabase di DAO untuk menyimpan favorit baru
            // Method addNewFavorite di Repository ini bisa kita pakai, atau langsung panggil DAO.
            // Lebih rapi langsung panggil DAO kalau addNewFavorite di Repository tidak dipakai di tempat lain.
            favoriteDao.insertToRoomDatabase(newFavorite)
            // Jika mau method ini mengembalikan ID yang baru di-insert: return favoriteDao.insertToRoomDatabase(newFavorite)
        // } // Penutup withContext
    }
    // =====================================================================
}
