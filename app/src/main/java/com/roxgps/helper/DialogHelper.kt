package com.roxgps.helper // Pastikan package ini sesuai dengan struktur folder kamu

// =====================================================================
// Import Library untuk DialogHelper
// =====================================================================

// import kotlinx.coroutines.flow.Flow // Jika perlu Flow di helper (opsional, tapi tidak disarankan)
// import androidx.lifecycle.LifecycleCoroutineScope // Jika perlu Scope di helper (opsional, tapi tidak disarankan)
// Anotasi @Inject constructor() agar Hilt bisa menyediakan instance
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.roxgps.BuildConfig
import com.roxgps.R
import com.roxgps.adapter.FavListAdapter
import com.roxgps.room.Favorite
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

class DialogHelper @Inject constructor(
    // Hilt bisa menyediakan Context
    @ActivityContext // Scope-nya Activity, jadi pakai @ActivityContext
    private val context: Context // Context Activity diperlukan untuk MaterialAlertDialogBuilder
    // LayoutInflater DIPINDAHKAN dari constructor ke method show...Dialog yang membutuhkannya
) {

    // =====================================================================
    // Fungsi Dasar: showAlertDialog (untuk dialog alert umum dengan custom view)
    // =====================================================================
    // Method ini tidak berubah, tapi sekarang menerima LayoutInflater
    fun showAlertDialog(
        layoutInflater: LayoutInflater, // Menerima LayoutInflater di sini
        title: String? = null,
        message: String? = null,
        positiveButtonText: String? = null,
        onPositiveButtonClick: (() -> Unit)? = null,
        negativeButtonText: String? = null,
        onNegativeButtonClick: (() -> Unit)? = null,
        isCancelable: Boolean = true,
        customView: View? = null
    ): AlertDialog {
        val builder = MaterialAlertDialogBuilder(context) // Menggunakan context dari constructor

        if (title != null) builder.setTitle(title)
        if (message != null) builder.setMessage(message)
        if (customView != null) builder.setView(customView) // View sudah di-inflate di method spesifik

        if (positiveButtonText != null) {
            builder.setPositiveButton(positiveButtonText) { dialog, which ->
                onPositiveButtonClick?.invoke()
            }
        }

        if (negativeButtonText != null) {
            builder.setNegativeButton(negativeButtonText) { dialog, which ->
                onNegativeButtonClick?.invoke()
            }
        }

        builder.setCancelable(isCancelable)

        val dialog = builder.create()
        // dialog.show() // Jangan panggil show di sini, kembalikan dialog biar pemanggil yang panggil show()
        return dialog
    }

    // =====================================================================
    // Fungsi Spesifik Dialog
    // Logic tampilan di sini, Logic Aksi di Lambda/Activity.
    // Semua method show...Dialog sekarang mengembalikan AlertDialog dan TIDAK langsung show()
    // =====================================================================

    // --- FUNGSI createAboutDialog --- (Nama diubah jadi create)
    fun createAboutDialog(layoutInflater: LayoutInflater): AlertDialog { // Menerima LayoutInflater
        val view = layoutInflater.inflate(R.layout.about, null).apply { // Menggunakan LayoutInflater yang diterima
            val titlele = findViewById<TextView>(R.id.design_about_title)
            val version = findViewById<TextView>(R.id.design_about_version)
            val info = findViewById<TextView>(R.id.design_about_info)
            titlele.text = context.getString(R.string.app_name)
            version.text = BuildConfig.VERSION_NAME
            info.text = context.getString(R.string.about_info)
        }

        // Menggunakan showAlertDialog dasar. Mengembalikan dialog, tidak show()
        return showAlertDialog(
            layoutInflater = layoutInflater, // Pass LayoutInflater
            title = context.getString(R.string.app_name),
            customView = view,
            positiveButtonText = "OK"
        )
    }

    // --- FUNGSI createAddFavoriteDialog --- (Nama diubah jadi create)
    fun createAddFavoriteDialog(layoutInflater: LayoutInflater, onAddClicked: (String) -> Unit): AlertDialog { // Menerima LayoutInflater
        val view = layoutInflater.inflate(R.layout.dialog, null) // Menggunakan LayoutInflater yang diterima
        val editText = view.findViewById<EditText>(R.id.search_edittxt)

        // Menggunakan showAlertDialog dasar. Mengembalikan dialog, tidak show()
        return showAlertDialog(
            layoutInflater = layoutInflater, // Pass LayoutInflater
            title = context.getString(R.string.add_fav_dialog_title),
            customView = view,
            positiveButtonText = context.getString(R.string.dialog_button_add),
            onPositiveButtonClick = {
                val s = editText.text.toString()
                onAddClicked(s)
            },
            negativeButtonText = context.getString(R.string.dialog_button_cancel)
        )
    }

    // --- FUNGSI createFavoriteListDialog --- (Nama diubah jadi create)
    // favList: Terima hook sebagai List<Favorite> dari Activity/ViewModel
    fun createFavoriteListDialog(
        layoutInflater: LayoutInflater, // Menerima LayoutInflater
        favList: List<Favorite>, // Menerima hook
        onItemClick: (Favorite) -> Unit, // Menerima callback
        onItemDelete: (Favorite) -> Unit // Menerima callback
    ): AlertDialog {
        val view = layoutInflater.inflate(R.layout.fav, null) // Menggunakan LayoutInflater yang diterima
        val rcv = view.findViewById<RecyclerView>(R.id.favorites_list)

        val favListAdapter = FavListAdapter() // Menggunakan adapter
        rcv.layoutManager = LinearLayoutManager(context) // Menggunakan context dari constructor
        rcv.adapter = favListAdapter

        favListAdapter.onItemClick = { favorite ->
            onItemClick(favorite)
            // Logic dismiss dialog setelah klik item tetap di Activity melalui callback
        }
        favListAdapter.onItemDelete = onItemDelete // Menyetel callback delete

        favListAdapter.submitList(favList) // Menggunakan hook list

        // Menggunakan MaterialAlertDialogBuilder langsung (tidak lewat showAlertDialog umum) karena ada tombol negatif. Mengembalikan dialog, tidak show()
        val dialog = MaterialAlertDialogBuilder(context) // Menggunakan context dari constructor
            .setTitle(context.getString(R.string.favorites))
            .setView(view)
            .setNegativeButton(context.getString(R.string.dialog_button_close), null)
            .create()

        return dialog // Mengembalikan instance dialog
    }

    // --- FUNGSI createUpdateDialog --- (Nama diubah jadi create)
    // Dialog utama Update
    fun createUpdateDialog(
        layoutInflater: LayoutInflater, // Menerima LayoutInflater
        updateInfo: String?, // Data
        onUpdateClicked: () -> Unit, // Callback tombol Update
        onCancelClicked: () -> Unit // Callback tombol Batal (untuk dialog download)
    ): AlertDialog {
         // Menggunakan MaterialAlertDialogBuilder langsung. Mengembalikan dialog, tidak show()
         val mainDialog = MaterialAlertDialogBuilder(context) // Menggunakan context dari constructor
             .setTitle(R.string.update_available)
             .setMessage(updateInfo)
             .setPositiveButton(context.getString(R.string.update_button)) { _, _ ->
                 // Aksi klik tombol Update: Panggil lambda callback
                 onUpdateClicked() // Memanggil lambda onUpdateClicked

                 // Di sini, tampilkan dialog download yang TERPISAH di Activity, BUKAN di helper.
                 // Helper bisa menyediakan fungsi createDownloadProgressDialog().
             }
             .setNegativeButton(context.getString(R.string.dialog_button_cancel), null)
             .create()

         return mainDialog // Mengembalikan dialog utama
     }

     // --- FUNGSI createDownloadProgressDialog --- (Fungsi baru untuk dialog progress download)
     // Dialog untuk progress download
     fun createDownloadProgressDialog(layoutInflater: LayoutInflater, onCancelClicked: () -> Unit): AlertDialog { // Menerima LayoutInflater
         // Meng-inflate layout dialog download custom
         val dialogView = layoutInflater.inflate(R.layout.update_dialog, null) // Menggunakan LayoutInflater yang diterima
         val progressIndicator = dialogView.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
         val cancelButton = dialogView.findViewById<AppCompatButton>(R.id.update_download_cancel)

         cancelButton.setOnClickListener { // Listener tombol Batal
             onCancelClicked() // Memanggil lambda onCancelClicked
             // Logika dismiss dialog TIDAK di sini, tapi di Activity,
             // saat Activity menerima update state "Cancelled" atau "Failed" dari ViewModel.
         }

         // Membuat dialog download progress. Mengembalikan dialog, tidak show()
         val dialog = MaterialAlertDialogBuilder(context) // Menggunakan context dari constructor
             .setTitle(R.string.update_available) // Judul dialog (bisa disesuaikan)
             .setView(dialogView) // View custom dialog download
             .setCancelable(false) // Biasanya dialog download tidak bisa dicancel pakai tombol back
             .create()

         return dialog // Mengembalikan instance dialog download
     }


    // --- FUNGSI createXposedMissingDialog --- (Nama diubah jadi create)
    // Logic diambil dari implementasi showXposedMissingDialog di MapActivity.
    fun createXposedMissingDialog(): AlertDialog { // Tidak perlu parameter LayoutInflater jika tidak pakai custom layout di sini
         // Menggunakan MaterialAlertDialogBuilder langsung. Mengembalikan dialog, tidak show()
         val dialog = MaterialAlertDialogBuilder(context) // Menggunakan context dari constructor
             .setTitle(R.string.error_xposed_module_missing)
             .setMessage(R.string.error_xposed_module_missing_desc)
             .setCancelable(true)
             .create()

         return dialog // Mengembalikan instance dialog
     }
    fun createTokenDialog(context: Context, token: String?): AlertDialog { // Menerima Context dan String? token
        // Menggunakan MaterialAlertDialogBuilder langsung. Mengembalikan dialog, tidak show()
        val dialog = MaterialAlertDialogBuilder(context) // Menggunakan context dari parameter
            .setTitle(R.string.token_dialog_title) // Judul dialog (pastikan ada di strings.xml)
            .setMessage(token ?: context.getString(R.string.token_not_available)) // Tampilkan token atau pesan default
            .setPositiveButton(context.getString(R.string.dialog_button_ok), null) // Tombol OK
            .setCancelable(true) // Bisa dicancel
            .create()

        return dialog // Mengembalikan instance dialog
    }

     // Fungsi helper untuk dismiss dialog (opsional, bisa dilakukan di Activity yang punya referensi dialog)
     // fun dismissDialog(dialog: AlertDialog?) {
     //    dialog?.dismiss()
     // }
}
