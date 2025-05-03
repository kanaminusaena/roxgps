package com.roxgps.helper // Pastikan package ini sesuai dengan struktur folder kamu

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.roxgps.R // Import R dari project kamu
import com.roxgps.adapter.FavListAdapter // Butuh adapter favorit
import com.roxgps.room.Favorite // Mengimpor Favorite dari package ROOM, sesuai keputusan kita
import kotlinx.coroutines.flow.Flow // Butuh Flow kalau FavList dipass sebagai Flow (opsional, tergantung implementasi)
import androidx.lifecycle.LifecycleCoroutineScope // Jika perlu collect Flow di helper (opsional)


// Helper class buat ngurusin semua logika tampilan dialog
class DialogHelper(
    private val context: Context, // Context Activity
    private val layoutInflater: LayoutInflater // LayoutInflater dari Activity
) {

    // --- FUNGSI showAlertDialog: Untuk dialog alert umum ---
    fun showAlertDialog(
        title: String? = null,
        message: String? = null,
        positiveButtonText: String? = null,
        onPositiveButtonClick: (() -> Unit)? = null,
        negativeButtonText: String? = null,
        onNegativeButtonClick: (() -> Unit)? = null,
        isCancelable: Boolean = true,
        customView: View? = null
    ): AlertDialog {
        val builder = MaterialAlertDialogBuilder(context)

        if (title != null) builder.setTitle(title)
        if (message != null) builder.setMessage(message)
        if (customView != null) builder.setView(customView)

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
        dialog.show()
        return dialog
    }

    // --- FUNGSI showAboutDialog ---
    fun showAboutDialog() {
        val view = layoutInflater.inflate(R.layout.about, null).apply {
            val titlele = findViewById<TextView>(R.id.design_about_title)
            val version = findViewById<TextView>(R.id.design_about_version)
            val info = findViewById<TextView>(R.id.design_about_info)
            titlele.text = context.getString(R.string.app_name)
            version.text = BuildConfig.VERSION_NAME
            info.text = context.getString(R.string.about_info)
        }

        showAlertDialog(
            title = context.getString(R.string.app_name),
            customView = view,
            positiveButtonText = "OK" // Menggunakan teks literal untuk tombol OK umum
        )
    }

    // --- FUNGSI showAddFavoriteDialog ---
    fun showAddFavoriteDialog(onAddClicked: (String) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog, null) // Asumsi layout dialog ini punya EditText dg id search_edittxt
        val editText = view.findViewById<EditText>(R.id.search_edittxt)

        showAlertDialog(
            title = context.getString(R.string.add_fav_dialog_title),
            customView = view,
            positiveButtonText = context.getString(R.string.dialog_button_add), // Menggunakan resource string
            onPositiveButtonClick = {
                val s = editText.text.toString()
                onAddClicked(s)
            },
            negativeButtonText = context.getString(R.string.dialog_button_cancel) // Menggunakan resource string
        )
    }

    // --- FUNGSI showFavoriteListDialog ---
    // favList: Terima data sebagai List<Favorite> dari Activity/ViewModel
    fun showFavoriteListDialog(
        favList: List<Favorite>, // Menggunakan Favorite dari package com.roxgps.room
        onItemClick: (Favorite) -> Unit,
        onItemDelete: (Favorite) -> Unit
    ): AlertDialog {
        val view = layoutInflater.inflate(R.layout.fav, null) // Asumsi layout ini punya RecyclerView dg id favorites_list
        val rcv = view.findViewById<RecyclerView>(R.id.favorites_list)

        val favListAdapter = FavListAdapter() // Menggunakan adapter yang sudah ada
        rcv.layoutManager = LinearLayoutManager(context)
        rcv.adapter = favListAdapter

        favListAdapter.onItemClick = { favorite ->
            onItemClick(favorite)
            // Logika dismiss dialog setelah klik item biasanya di Activity
        }
        favListAdapter.onItemDelete = onItemDelete

        favListAdapter.submitList(favList) // Menggunakan List<Favorite> yang diterima

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.favorites)) // Menggunakan resource string
            .setView(view)
            .setNegativeButton(context.getString(R.string.dialog_button_close), null) // Contoh tombol Close
            .create()

        dialog.show()
        return dialog
    }
     // Catatan: Layout 'fav' harus punya string "dialog_button_close"
    // Tambahkan di strings.xml: <string name="dialog_button_close">Tutup</string>


    // --- FUNGSI showUpdateDialog ---
    fun showUpdateDialog(
        updateInfo: String?,
        onCancelClicked: () -> Unit
        // updateState: Flow<MainViewModel.State>? = null, // Jika logic state di helper
        // lifecycleScope: LifecycleCoroutineScope? = null // Jika logic state di helper
    ): AlertDialog {
         val dialogView = layoutInflater.inflate(R.layout.update_dialog, null) // Asumsi layout ini punya progressbar dan tombol cancel
         val progressIndicator = dialogView.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
         val cancelButton = dialogView.findViewById<AppCompatButton>(R.id.update_download_cancel)

         cancelButton.setOnClickListener {
             onCancelClicked()
             // Logika dismiss dialog di Activity berdasarkan state atau callback cancel
         }

         // Jika logic progress/state download di helper dan pass Flow/Scope:
         // lifecycleScope?.launch {
         //      updateState?.collect { state ->
         //          when(state) {
         //              is MainViewModel.State.Downloading -> {
         //                  progressIndicator.isIndeterminate = false
         //                  progressIndicator.progress = state.progress
         //              }
         //              // Tambah logic buat state Done dan Failed (dismiss dialog, dll)
         //          }
         //      }
         // }
         // Jika logic progress di Activity, Activity yang observe state dan update dialogView (disarankan)


         val dialog = MaterialAlertDialogBuilder(context)
             .setTitle(R.string.update_available) // Menggunakan resource string
             .setMessage(updateInfo)
             .setPositiveButton(context.getString(R.string.update_button)) { _, _ ->
                 // Aksi klik tombol Update: sebaiknya trigger event/lambda ke Activity
                 // yang akan memulai proses download (logic download di Activity/ViewModel)
                 // onUpdateClicked() // Contoh: panggil lambda start download
             }
             .setView(dialogView)
             .create()

         dialog.show()
         return dialog
     }


    // Fungsi helper untuk dismiss dialog (opsional, bisa dilakukan di Activity)
    fun dismissDialog(dialog: AlertDialog?) {
       dialog?.dismiss()
    }
}
