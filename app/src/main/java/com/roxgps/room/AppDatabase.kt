package com.roxgps.room // Sesuaikan package repository kamu

// =====================================================================
// Import Library untuk AppDatabase
// =====================================================================

// TODO: Import semua Entity yang ada di database ini

// TODO: Import semua DAO yang ada di database ini

// TODO: Import TypeConverters jika digunakan
// import androidx.room.TypeConverters // Contoh import TypeConverters

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.roxgps.module.util.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

// =====================================================================
// AppDatabase (Room Database)
// Mendefinisikan database aplikasi dan daftar Entity serta DAO.
// =====================================================================

@Database(
    // Daftar semua Entity yang ada di database ini dalam SATU array
    entities = [
        Favorite::class, // Tambahkan Favorite Entity
        LocationEntity::class // Tambahkan LocationEntity
        // TODO: Tambahkan Entity lain di sini jika ada
    ],
    version = 1, // Nomor versi database. Tingkatkan jika skema berubah (perlu migrasi!).
    exportSchema = false // Disarankan true untuk export schema ke file JSON (untuk dokumentasi dan migrasi)
    // TODO: Tambahkan TypeConverters jika digunakan
    // typeConverters = [MyTypeConverter::class] // Contoh penggunaan TypeConverters
)
abstract class AppDatabase : RoomDatabase() {

    // Daftar semua DAO di database ini sebagai abstract fun
    abstract fun favoriteDao(): FavoriteDao // Abstract fun untuk FavoriteDao
    abstract fun locationDao(): LocationDao // Abstract fun untuk LocationDao
    // TODO: Tambahkan abstract fun untuk DAO lain jika ada

    // =====================================================================
    // Callback Database (Opsional, untuk inisialisasi awal database)
    // Digunakan untuk menjalankan kode saat database pertama kali dibuat.
    // =====================================================================

    /**
     * Callback untuk inisialisasi awal database Room.
     * Digunakan untuk mengisi database dengan hook awal saat pertama kali dibuat.
     * Menggunakan @Inject dan @ApplicationScope untuk menjalankan operasi di background.
     */
    class Callback @Inject constructor(
        // Menginjeksi ApplicationScope Coroutine untuk menjalankan operasi di background
        @ApplicationScope private val applicationScope: CoroutineScope
    ) : RoomDatabase.Callback(){

        /**
         * Dipanggil saat database pertama kali dibuat.
         * @param db Objek SupportSQLiteDatabase.
         */
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // TODO: Jalankan operasi inisialisasi database di sini jika diperlukan
            // Contoh: Mengisi database dengan hook awal dari file JSON atau list statis.
            applicationScope.launch {
                // Contoh: Panggil DAO untuk insert hook awal
                // val favoriteDao = (db as AppDatabase).favoriteDao() // Tidak bisa diakses langsung dari SupportSQLiteDatabase
                // Anda perlu mendapatkan instance DAO melalui Hilt atau cara lain di sini jika perlu.
                // Atau, cara yang lebih umum adalah menginjeksi Callback ini ke Module Hilt yang menyediakan database.
                // Di Module Hilt penyedia AppDatabase, tambahkan .addCallback(Callback) saat membangun database.
            }
        }

        // TODO: Override onOpen() jika perlu menjalankan kode saat database dibuka
        /*
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Kode yang dijalankan setiap kali database dibuka
        }
        */
    }

    // TODO: Tambahkan metode lain jika diperlukan (misal, metode untuk mendapatkan instance database jika tidak menggunakan Hilt)
}
