package com.roxgps.helper // Atau package lain yang sesuai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog // MaterialAlertDialogBuilder butuh AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.roxgps.BuildConfig
import com.roxgps.R // Import R dari project kamu
import com.roxgps.adapter.FavListAdapter // Butuh adapter favorit
import com.roxgps.room.Favorite // Butuh model Favorite, lokasinya di package room

import kotlinx.coroutines.flow.Flow // Butuh Flow kalau FavList dipass sebagai Flow
// Import yang mungkin dibutuhkan oleh kode dialog custom
// import android.graphics.drawable.Drawable
// import android.widget.ImageView


// Helper class buat ngurusin semua logika tampilan dialog
class DialogHelper(
    private val context: Context, // Context Activity
    private val layoutInflater: LayoutInflater // LayoutInflater dari Activity
) {

    // --- FUNGSI showAlertDialog: Untuk dialog alert umum (yang tadi belum ada) ---
    // title, message: Judul dan pesan dialog
    // positiveButtonText, onPositiveButtonClick: Teks dan aksi tombol Positif
    // negativeButtonText, onNegativeButtonClick: Teks dan aksi tombol Negatif
    // isCancelable: Apakah dialog bisa dicancel
    // Mengembalikan AlertDialog instance kalau perlu di-dismiss dari luar (misal untuk xposedDialog)
    fun showAlertDialog(
        title: String? = null, // Judul dialog (opsional)
        message: String? = null, // Pesan dialog (opsional)
        positiveButtonText: String? = null, // Teks tombol positif (opsional)
        onPositiveButtonClick: (() -> Unit)? = null, // Lambda aksi tombol positif (opsional)
        negativeButtonText: String? = null, // Teks tombol negatif (opsional)
        onNegativeButtonClick: (() -> Unit)? = null, // Lambda aksi tombol negatif (opsional)
        isCancelable: Boolean = true, // Apakah bisa dicancel (default true)
        customView: View? = null // Custom view jika ada
    ): AlertDialog {
        val builder = MaterialAlertDialogBuilder(context)

        if (title != null) builder.setTitle(title)
        if (message != null) builder.setMessage(message)
        if (customView != null) builder.setView(customView) // Set custom view

        if (positiveButtonText != null && onPositiveButtonClick != null) {
            builder.setPositiveButton(positiveButtonText) { dialog, which ->
                onPositiveButtonClick.invoke() // Jalankan lambda aksi positif
            }
        } else if (positiveButtonText != null) {
             // Jika teks ada tapi aksi tidak ada, tombol tetap ditampilkan
             builder.setPositiveButton(positiveButtonText, null)
        }


        if (negativeButtonText != null && onNegativeButtonClick != null) {
            builder.setNegativeButton(negativeButtonText) { dialog, which ->
                onNegativeButtonClick.invoke() // Jalankan lambda aksi negatif
            }
        } else if (negativeButtonText != null) {
            // Jika teks ada tapi aksi tidak ada, tombol tetap ditampilkan
            builder.setNegativeButton(negativeButtonText, null)
        }


        builder.setCancelable(isCancelable)

        val dialog = builder.create()
        dialog.show() // Langsung tampilkan dialognya
        return dialog // Kembalikan instance dialog
    }
    // ---------------------------------------------------------------------------


    // --- FUNGSI showAboutDialog: Untuk nampilin About Dialog ---
    fun showAboutDialog() {
        val view = layoutInflater.inflate(R.layout.about, null).apply {
            val titlele = findViewById<TextView>(R.id.design_about_title) // Nama variabel sesuai kode asli
            val version = findViewById<TextView>(R.id.design_about_version)
            val info = findViewById<TextView>(R.id.design_about_info)
            titlele.text = context.getString(R.string.app_name)
            version.text = BuildConfig.VERSION_NAME // BuildConfig harus bisa diakses
            info.text = context.getString(R.string.about_info)
        }

        // Gunakan showAlertDialog untuk menampilkan dialog ini
        showAlertDialog(
            title = context.getString(R.string.app_name), // Judul dari string resources
            customView = view, // Set custom view
            positiveButtonText = "OK" // Tombol OK
            // Tidak ada onPositiveButtonClick karena cuma dismiss
        )
    }
    // --------------------------------------------------------


    // --- FUNGSI showAddFavoriteDialog: Untuk nampilin Add Favorite Dialog ---
    // onAddClicked: lambda yang akan dipanggil saat tombol "Add" diklik, parameternya String inputan user
    fun showAddFavoriteDialog(onAddClicked: (String) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog, null)
        val editText = view.findViewById<EditText>(R.id.search_edittxt)

        // Gunakan showAlertDialog untuk menampilkan dialog ini
        showAlertDialog(
            title = context.getString(R.string.add_fav_dialog_title), // Judul dari string resources
            customView = view, // Set custom view
            positiveButtonText = context.getString(R.string.dialog_button_add), // Teks tombol Positif
            onPositiveButtonClick = {
                val s = editText.text.toString()
                onAddClicked(s) // Panggil lambda dengan input user
            },
            negativeButtonText = context.getString(R.string.dialog_button_cancel) // Teks tombol Negatif
            // Tidak ada onNegativeButtonClick karena cuma dismiss
        )
    }
    // ---------------------------------------------------------------------


    // --- FUNGSI showFavoriteListDialog: Untuk nampilin Favorite List Dialog ---
    // favList: data daftar favorit (bisa LiveData/Flow atau List<Favorite> langsung)
    // onItemClick: lambda saat item favorit diklik, parameternya objek Favorite
    // onItemDelete: lambda saat tombol delete di item favorit diklik, parameternya objek Favorite
    // Catatan: Adapter FavListAdapter butuh Context. Adapter bisa dibuat di sini atau di luar dan dipass.
    // Kalau terima Flow<List<Favorite>>, perlu CoroutineScope buat collect, atau Activity yang collect dan pass List.
    // Contoh di sini asumsikan Activity yang collect dan pass List<Favorite>
    // Atau, jika Activity pass Flow, helper ini butuh LifecycleCoroutineScope.
    fun showFavoriteListDialog(
        // Jika Activity collect dan pass List:
        favList: List<Favorite>, // Terima data sebagai List<Favorite>
        // Jika Activity pass Flow dan helper collect (butuh LifecycleCoroutineScope):
        // favListFlow: Flow<List<Favorite>>,
        // lifecycleScope: LifecycleCoroutineScope,
        onItemClick: (Favorite) -> Unit,
        onItemDelete: (Favorite) -> Unit
    ): AlertDialog { // Kembalikan AlertDialog biar bisa didismiss dari Activity
        val view = layoutInflater.inflate(R.layout.fav, null)
        val rcv = view.findViewById<RecyclerView>(R.id.favorites_list)

        val favListAdapter = FavListAdapter() // Buat adapter di sini
        rcv.layoutManager = LinearLayoutManager(context)
        rcv.adapter = favListAdapter

        // Pasang listener dari lambda yang dikasih Activity
        favListAdapter.onItemClick = { favorite ->
            onItemClick(favorite) // Panggil lambda onItemClick
            // Setelah item diklik, dialog biasanya ditutup.
            // Dialog ini dibuat dengan builder.create(), kita bisa dismiss di sini
            // Atau biarkan Activity yang memegang instance dialog yang mendismiss
            // Misalnya, kalau fungsi ini mengembalikan dialog instance, Activity bisa dialog.dismiss()
        }
        favListAdapter.onItemDelete = onItemDelete // Lambda onItemDelete

        // Submit list data ke adapter
        favListAdapter.submitList(favList)

        // Jika terima Flow, logic collect di sini:
        // lifecycleScope.launch {
        //    favListFlow.collect { list ->
        //        favListAdapter.submitList(list)
        //    }
        // }


        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.favorites)) // Judul dari string resources
            .setView(view) // Set custom view
            .create() // Pakai create() biar bisa di-return

        dialog.show() // Tampilkan dialognya
        return dialog // Kembalikan dialog instance
    }
    // -------------------------------------------------------------------------


    // --- FUNGSI showUpdateDialog: Untuk nampilin Update Dialog ---
    // updateInfo: data update (update?.changelog, dll)
    // onCancelClicked: lambda saat tombol Cancel diklik
    // Catatan: Logic download progress dan tombol Update itu kompleks.
    // Sebaiknya DialogHelper cuma nampilin UI progress & tombol Cancel.
    // State download (Downloading, Done, Failed) dikirim dari Activity/ViewModel dan
    // Activity yang update UI dialog atau dismiss dialog berdasarkan state itu.
    // updateState: Flow<MainViewModel.State> // Kalau terima state download
    // lifecycleScope: LifecycleCoroutineScope // Kalau perlu collect state download
    fun showUpdateDialog(
        updateInfo: String?, // Info changelog update
        onCancelClicked: () -> Unit, // Lambda aksi Cancel
        // onUpdateClicked: () -> Unit // Logic start download sebaiknya di luar helper
        // Jika logic progress di helper:
        // updateState: Flow<MainViewModel.State>,
        // lifecycleScope: LifecycleCoroutineScope,
        // Tambahan parameter kalau custom view butuh data lain
    ): AlertDialog { // Kembalikan AlertDialog biar bisa didismiss
         val dialogView = layoutInflater.inflate(R.layout.update_dialog, null)
         val progressIndicator = dialogView.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
         val cancelButton = dialogView.findViewById<AppCompatButton>(R.id.update_download_cancel)

         cancelButton.setOnClickListener {
             onCancelClicked() // Panggil lambda cancel
             // dismiss dialog di Activity berdasarkan callback state download
             // dialog.dismiss() // Jangan dismiss di sini kalau Activity yang handle state
         }

         // Jika logic menampilkan progress berdasarkan state download di helper:
         // if (updateState != null && lifecycleScope != null) {
         //    lifecycleScope.launch {
         //         updateState.collect { state ->
         //             when(state) {
         //                 is MainViewModel.State.Downloading -> {
         //                     progressIndicator.isIndeterminate = false
         //                     progressIndicator.progress = state.progress
         //                 }
         //                 // Tambah logic buat state Done dan Failed (dismiss dialog, dll)
         //                 // Dismiss dialog di sini kalau helper yang handle
         //                 is MainViewModel.State.Done -> { /* dismiss dialog */ }
         //                 is MainViewModel.State.Failed -> { /* dismiss dialog */ }
         //                 else -> { /* State Idle */ }
         //             }
         //         }
         //     }
         // }
         // Jika logic menampilkan progress di Activity, Activity yang observe state dan update dialogView

         val dialog = MaterialAlertDialogBuilder(context)
             .setTitle(R.string.update_available) // Judul dari string resources
             .setMessage(updateInfo) // Set pesan changelog
             .setPositiveButton(context.getString(R.string.update_button)) { _, _ ->
                 // Aksi klik tombol Update: sebaiknya memanggil lambda atau event ke Activity
                 // yang akan memulai proses download (logic download di Activity/ViewModel)
                 // onUpdateClicked() // Contoh: panggil lambda start download
             }
             .setView(dialogView) // Set custom view dengan progress & tombol cancel
             .create() // Pakai create()

         dialog.show() // Tampilkan dialognya
         return dialog // Kembalikan dialog instance
     }
    // -----------------------------------------------------------------------


    // Fungsi helper untuk dismiss dialog (opsional, bisa dilakukan di Activity)
    fun dismissDialog(dialog: AlertDialog?) {
       dialog?.dismiss()
    }
}
