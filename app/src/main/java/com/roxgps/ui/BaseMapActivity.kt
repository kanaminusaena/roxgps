package com.roxgps.ui

//import com.roxgps.repository.DownloadRepository.DownloadState
import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.roxgps.R
import com.roxgps.databinding.ActivityMapBinding
import com.roxgps.helper.DialogHelper
import com.roxgps.helper.ILocationHelper
import com.roxgps.helper.LocationListener
import com.roxgps.helper.PermissionHelper
import com.roxgps.helper.PermissionResultListener
import com.roxgps.helper.SearchProgress
import com.roxgps.room.Favorite
import com.roxgps.ui.viewmodel.MainViewModel
import com.roxgps.update.YourUpdateModel
import com.roxgps.utils.NotificationsChannel
import com.roxgps.utils.PrefManager
import com.roxgps.utils.Relog
import com.roxgps.utils.ext.showToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// BaseMapActivity sekarang mengimplementasikan PermissionResultListener
@AndroidEntryPoint
abstract class BaseMapActivity : AppCompatActivity(), PermissionResultListener, LocationListener
{
	companion object {
		private const val TAG = "BaseMapActivity"
	}

	@Inject internal lateinit var locationHelper: ILocationHelper
	// =================== Dependency Injection & Bindings ===================
	// ActivityMapBinding dihasilkan dari activity_map.xml
	protected lateinit var binding: ActivityMapBinding

	protected val viewModel: MainViewModel by viewModels()
	@Inject internal lateinit var prefManager: PrefManager // <<< UBAH PROTECTED JADI PRIVATE
	@Inject internal lateinit var notificationsChannel: NotificationsChannel // <<< UBAH PROTECTED JADI PRIVATE
	@Inject internal lateinit var permissionHelper: PermissionHelper // <<< UBAH PROTECTED JADI PRIVATE
	@Inject internal lateinit var dialogHelper: DialogHelper // <<< UBAH PROTECTED JADI PRIVATE

	protected val PERMISSION_ID = 42 // PERMISSION_ID mungkin tidak lagi diperlukan dengan Activity Result APIs
	protected var lat: Double = 0.0 // Variabel lat di Activity
	protected var lon: Double = 0.0 // Variabel lon di Activity

	private var downloadProgressDialog: AlertDialog? = null
	internal var xposedDialog: AlertDialog? = null
	private var tokenDialog: AlertDialog? = null // Mengganti nama variabel

	//@Inject // <-- Injeksi Helper Lokasi Spesifik Flavor (ILocationHelper)
	//lateinit var locationHelper: ILocationHelper // Hilt akan meng-inject GoogleLocationHelper atau MapLibreLocationHelper
	// =================== Lifecycle ===================
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		WindowCompat.setDecorFitsSystemWindows(window, false)
		binding = ActivityMapBinding.inflate(layoutInflater) // Inflate binding dari activity_map.xml
		setContentView(binding.root)
		// HAPUS AKSES LANGSUNG KE PROPERTI LAT/LNG LAMA DI PrefManager
		// lat = prefManager.lat ?: 0.0 // Hapus baris ini
		// lon = prefManager.lng ?: 0.0 // Hapus baris ini
		// Initialisasi lat/lon di Activity akan dilakukan oleh observer StateFlow dari ViewModel
		initializeMap()
		setupNavView()
		setupButtons()
		observeViewModelState() // Ini akan mengamati update dari ViewModel StateFlows
		observeViewModelEvents() // Ini akan mengamati event seperti showToastEvent
		//handleIntent(intent)
	}

	// Mengubah signature onNewIntent agar sesuai dengan yang diharapkan compiler
	// Error "'onNewIntent' overrides nothing" biasanya disebabkan oleh masalah build/cache.
	// Lakukan Clean dan Rebuild proyek untuk memperbaikinya.
	override fun onNewIntent(intent: Intent) { // Parameter Intent non-nullable
		super.onNewIntent(intent)
		setIntent(intent) // Penting untuk mengupdate Activity's intent
		handleIntent(intent) // Panggil handler dengan Intent baru
	}

	// =================== ViewModel Observers ===================
	@SuppressLint("SetTextI18n")
	private fun observeViewModelState() {
		lifecycleScope.launch { // launch coroutine
			repeatOnLifecycle(Lifecycle.State.STARTED) { // repeatOnLifecycle memerlukan lifecycle-runtime-ktx
				viewModel.isModuleHooked.collectLatest { isHooked: Boolean -> // collectLatest memerlukan coroutines-core/android + Flow import
					val shouldShowXposedDialog = !isHooked || viewModel.lastHookError.value != null
					if (shouldShowXposedDialog) showXposedDialog() else dismissXposedDialog()
				}
				viewModel.lastHookError.collectLatest { errorMsg: String? -> // collectLatest
					val shouldShowXposedDialog = !viewModel.isModuleHooked.value || errorMsg != null
					if (shouldShowXposedDialog) showXposedDialog() else dismissXposedDialog()
				}
				viewModel.token.collectLatest { token: String? -> // Mengamati StateFlow token
					showToken(token) // Memanggil method showToken
				}
				/*viewModel.updateInfo.collectLatest { updateInfo: YourUpdateModel? -> // collectLatest
					if (updateInfo != null) {
						showUpdateAvailableDialog(updateInfo)
					} else {
						dismissUpdateAvailableDialog() // <-- stub
					}
				}
				viewModel.downloadState.collectLatest { state: DownloadState -> // collectLatest
					when (state) {
						is DownloadState.Idle -> {
							downloadProgressDialog?.dismiss()
							downloadProgressDialog = null
						}
						is DownloadState.Downloading -> {
							if (downloadProgressDialog == null || !downloadProgressDialog!!.isShowing) {
								// Mengubah panggilan method dari showDownloadProgressDialog menjadi createDownloadProgressDialog
								downloadProgressDialog = dialogHelper.createDownloadProgressDialog(layoutInflater) {
									viewModel.cancelDownload()
								}
								downloadProgressDialog?.show() // Panggil show() setelah dialog dibuat
							}
							downloadProgressDialog?.let { dialog ->
								val progressIndicator = dialog.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
								val progressText = dialog.findViewById<TextView>(R.id.update_download_progress_text)
								if (progressIndicator != null && progressText != null) {
									if (state.progress >= 0) {
										progressIndicator.isIndeterminate = false
										progressIndicator.progress = state.progress
										progressText.text = "${state.progress}%"
									} else {
										progressIndicator.isIndeterminate = true
										progressText.text = "Downloading..."
									}
								}
							}
							onDownloadProgress(state.progress) // <-- stub
						}
						is DownloadState.Done, is DownloadState.Failed, is DownloadState.Cancelled -> {
							downloadProgressDialog?.dismiss()
							downloadProgressDialog = null
							onDownloadFinished() // <-- stub
						}
					}
				}*/
				viewModel.searchResult.collectLatest { state: SearchProgress -> // collectLatest
					when (state) {
						is SearchProgress.Idle -> {
							binding.search.searchProgress.visibility = View.GONE
							// Mungkin sembunyikan pesan error atau hasil sebelumnya jika ada
						}
						is SearchProgress.Loading -> { // <<< IMPLEMENTASI UNTUK LOADING
							binding.search.searchProgress.visibility = View.VISIBLE // Tampilkan progress bar
							// Mungkin sembunyikan pesan error atau hasil sebelumnya
							// showToast("Searching...") // Opsional: Tampilkan toast
						}
						is SearchProgress.Progress -> {
							binding.search.searchProgress.visibility = View.VISIBLE
							// Untuk progress bar yang menampilkan persentase, kamu bisa update progressIndicator di sini
						}
						is SearchProgress.Complete -> {
							binding.search.searchProgress.visibility = View.GONE
							// Tampilkan hasil di peta
							val searchLocation = Location("search").apply {
								latitude = state.lat
								longitude = state.lon
							}
							this@BaseMapActivity.lat = searchLocation.latitude
							this@BaseMapActivity.lon = searchLocation.longitude
							moveMapToNewLocation(searchLocation, true)
							viewModel.setSearchedLocation(searchLocation)
							// Mungkin sembunyikan pesan error
						}
						is SearchProgress.PartialResult -> {
							binding.search.searchProgress.visibility = View.GONE
							if (state.results.isNotEmpty()) {
								showPartialSearchResultsDialog(state.results) // <-- stub dialog
							} else {
								viewModel.resetSearchState()
								viewModel.triggerShowToastEvent(getString(R.string.address_not_found))
							}
							// Mungkin sembunyikan pesan error
						}
						is SearchProgress.NoResultFound -> {
							binding.search.searchProgress.visibility = View.GONE
							viewModel.triggerShowToastEvent(getString(R.string.address_not_found))
							// Mungkin sembunyikan pesan error
						}
						is SearchProgress.Fail -> {
							binding.search.searchProgress.visibility = View.GONE
							// Tampilkan pesan error dari state.error
							val errorMessage = state.message ?: getString(R.string.address_not_found)
							viewModel.triggerShowToastEvent(errorMessage) // Tampilkan pesan error via event
							// Atau tampilkan Snackbar/Dialog
							// Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
							// Mungkin sembunyikan hasil sebelumnya
						}
						is SearchProgress.Error -> { // <<< IMPLEMENTASI UNTUK ERROR
							binding.search.searchProgress.visibility = View.GONE // Sembunyikan progress bar
							// Tampilkan pesan error. Asumsi SearchProgress.Error juga punya properti error.
							// Jika SearchProgress.Error tidak punya properti error, gunakan pesan default.
							val errorMessage = state.message ?: getString(R.string.search_error_generic) // <<< Asumsi ada properti error
							viewModel.triggerShowToastEvent(errorMessage) // Tampilkan pesan error via event
							// Atau tampilkan Snackbar/Dialog
							// Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
							// Mungkin sembunyikan hasil sebelumnya
						}
					}
				}
				viewModel.isStarted.collectLatest { isStarted: Boolean -> // collectLatest
					Relog.i(TAG, "isStarted state changed to: $isStarted")
					// Ketika state isStarted berubah (diupdate oleh Service), update UI
					updateStartStopButtonUI(isStarted) // <--- Panggil metode update UI di sini
					// Hapus logika update UI di tempat lain (onStopButtonClicked, processStopLocationUpdate)
				}
				// Amati StateFlow latitude dan longitude dari ViewModel (yang berasal dari PrefManager)
				viewModel.latitude.collectLatest { latitudeFloat: Float -> this@BaseMapActivity.lat = latitudeFloat.toDouble() } // Amati StateFlow<Float> dan konversi ke Double
				viewModel.longitude.collectLatest { longitudeFloat: Float -> this@BaseMapActivity.lon = longitudeFloat.toDouble() } // Amati StateFlow<Float> dan konversi ke Double
			}
		}
	}

	private fun observeViewModelEvents() {
		lifecycleScope.launch { // launch coroutine
			repeatOnLifecycle(Lifecycle.State.STARTED) { // repeatOnLifecycle
				// Ini adalah observer untuk event showToastEvent
				// Event ini sekarang diharapkan membawa String pesan Toast langsung
				viewModel.showToastEvent.collectLatest { message: String -> // collectLatest
					// Ketika event diterima dari ViewModel, tampilkan Toast di UI
					showToast(message)
				}
				viewModel.installAppEvent.collectLatest { fileUri: Uri -> // collectLatest
					val installIntent = Intent(Intent.ACTION_VIEW).apply {
						setDataAndType(fileUri, "application/vnd.android.package-archive")
						flags = Intent.FLAG_ACTIVITY_NEW_TASK or (Intent.FLAG_GRANT_READ_URI_PERMISSION)
					}
					if (packageManager.resolveActivity(installIntent, 0) != null) {
						runCatching { startActivity(installIntent) }
					}
				}
				viewModel.showXposedDialogEvent.collectLatest { isShow: Boolean -> // collectLatest
					if (isShow) showXposedDialog() else dismissXposedDialog()
				}
			}
		}
	}

	private fun setupNavView() {
		// Menggunakan binding.navView yang mereferensikan NavigationView di activity_map.xml
		binding.navView.setNavigationItemSelectedListener { menuItem ->
			when (menuItem.itemId) {
				R.id.nav_fav -> showFavoriteListDialog()
				R.id.nav_about -> showAboutDialog()
				//R.id.nav_update -> viewModel.checkForUpdates()
				R.id.nav_clear_log -> Relog.clearAllLogs(this)
				R.id.nav_view_log_folder -> { /* show log path */ }
				R.id.nav_token -> showTokenDialog(viewModel.token.value) // Memanggil method showTokenDialog
				R.id.nav_exit -> finishAndRemoveTask()
			}
			// Menggunakan binding.drawerLayout yang mereferensikan DrawerLayout di activity_map.xml
			binding.drawerLayout.closeDrawer(binding.navView) // <<< Penggunaan drawerLayout
			true
		}

		// Menyetel listener klik pada ikon navigasi di Toolbar
		binding.toolbar.setNavigationOnClickListener {
			// Menggunakan binding.drawerLayout yang mereferensikan DrawerLayout di activity_map.xml
			binding.drawerLayout.openDrawer(binding.navView)
		}

		binding.search.searchBox.setOnEditorActionListener { v, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_SEARCH) {
				val query = v.text.toString()
				if (query.isNotEmpty()) {
					viewModel.searchAddress(query)
				}
				val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
				imm.hideSoftInputFromWindow(v.windowToken, 0)
				true
			} else false
		}
	}

	// Menghapus override onRequestPermissionsResult
	// Penanganan hasil permission sekarang dilakukan oleh PermissionHelper
	/*
	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		// Baris ini dihapus karena PermissionHelper baru tidak memiliki method ini
		// permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults,
		//     onLocationPermissionGranted = { getLastLocation() },
		//     onLocationPermissionDenied = { }
		// )
	}
	*/

	// =================== Implementasi PermissionResultListener ===================
	// Menambahkan implementasi method dari PermissionResultListener
	override fun onPermissionResult(permission: String, isGranted: Boolean) {
		// 1. Cek izin notifikasi
		if (permissionHelper.checkNotificationPermission()) {
			// Izin sudah ada, lanjutkan memulai service
			//startJoystickService() // <-- Method untuk memulai service
		} else {
			// Izin belum ada, minta izin ke pengguna
			// Meneruskan 'this' karena Activity mengimplementasikan PermissionResultListener
			requestLocationPermission()
			permissionHelper.requestNotificationPermission(this)
			// Logic untuk memulai service akan dipanggil di callback onPermissionResult
			// dari PermissionResultListener setelah izin diberikan.
		}
		// Handle hasil untuk permintaan izin tunggal jika ada
		Timber.tag(TAG).d("Single permission result for $permission: $isGranted")
		// Jika kamu punya logic spesifik untuk izin tunggal, tambahkan di sini
		// Contoh: if (permission == Manifest.permission.POST_NOTIFICATIONS) { ... }
	}

	override fun onPermissionsResult(permissions: Map<String, Boolean>) {
		// Handle hasil untuk permintaan beberapa izin (misal lokasi)
		Timber.tag(TAG).d("Multiple permissions result: $permissions")
		if (permissions.all { it.value }) {
			// Semua izin yang diminta diberikan
			lifecycleScope.launch { viewModel.triggerShowToastEvent("Notification Permission Granted.") }
			Timber.tag(TAG).d("All requested permissions granted.")
			// Panggil logic yang membutuhkan izin (misal mendapatkan lokasi terakhir)
			getLastLocation()
		} else {
			// Setidaknya ada satu izin yang ditolak
			lifecycleScope.launch { viewModel.triggerShowToastEvent("Notification Permission Denied.") }
			Timber.tag(TAG).d("Some requested permissions denied.")
			// Panggil logic penanganan error (misal menampilkan pesan ke user)
			handleLocationError()
		}
	}

	// =================== Dialog Management ===================
	// Menggunakan createXposedMissingDialog dari DialogHelper dan memanggil show()
	private fun showXposedDialog() {
		if (xposedDialog == null || !xposedDialog!!.isShowing) {
			xposedDialog = dialogHelper.createXposedMissingDialog() // Menggunakan create... method
			xposedDialog?.show() // Panggil show()
		}
	}

	private fun dismissXposedDialog() {
		xposedDialog?.dismiss()
		xposedDialog = null
	}

	// Mengganti nama method dari showGojekTokenDialog menjadi showTokenDialog
	// Asumsi ada method createTokenDialog di DialogHelper (atau implementasi AlertDialog sementara)
	// TODO: Implement createTokenDialog in DialogHelper and use it here
	private fun showTokenDialog(token: String?) { // <<< NAMA BARU
		tokenDialog?.dismiss() // Menggunakan nama variabel tokenDialog
		// Contoh menggunakan AlertDialog biasa (sementara):
		tokenDialog = AlertDialog.Builder(this) // Menggunakan nama variabel tokenDialog
			.setTitle("Token") // Judul dialog universal
			.setMessage(token ?: getString(R.string.token_not_available)) // Pesan universal
			.setPositiveButton("OK", null)
			.create()
		tokenDialog?.show() // Menggunakan nama variabel tokenDialog
	}

	// Menggunakan createAboutDialog dari DialogHelper dan memanggil show()
	private fun showAboutDialog() {
		// Asumsi createAboutDialog di DialogHelper mengembalikan AlertDialog
		val aboutDialog = dialogHelper.createAboutDialog(layoutInflater) // Menggunakan create... method
		aboutDialog.show() // Panggil show()
	}

	// Menggunakan createFavoriteListDialog dari DialogHelper dan memanggil show()
	private fun showFavoriteListDialog() {
		// Kamu perlu mendapatkan daftar favorit (List<Favorite>) dari ViewModel atau sumber hook lain
		// Untuk contoh ini, kita pakai list kosong atau dummy
		val dummyFavList = emptyList<Favorite>() // Ganti dengan hook favorit yang sebenarnya

		// PERBAIKAN: Deklarasikan favListDialog sebagai var sebelum digunakan di lambda
		var favListDialog: AlertDialog? = null // <<< Deklarasi di sini

		favListDialog = dialogHelper.createFavoriteListDialog( // <<< Assignment di sini
			layoutInflater,
			dummyFavList,
			onItemClick = { favorite ->
				// Handle item click, misal pindah map ke lokasi favorit
				// Menggunakan favorite.address
				Timber.tag(TAG).d("Favorite clicked: ${favorite.address}") // Menggunakan favorite.address
				// TODO: Implement logic to move map to favorite location
				favListDialog?.dismiss() // <<< Mengakses variabel yang sudah dideklarasikan di luar lambda
			},
			onItemDelete = { favorite ->
				// Handle item delete, misal hapus favorit dari database
				// Menggunakan favorite.address
				Timber.tag(TAG).d("Favorite deleted: ${favorite.address}") // Menggunakan favorite.address
				// TODO: Implement logic to delete favorite
				// Mungkin perlu refresh dialog atau list setelah delete
			}
		)
		favListDialog.show() // Panggil show()
	}

	// Implementasi stub showPartialSearchResultsDialog
	private fun showPartialSearchResultsDialog(results: List<Any>) {
		// TODO: Implement dialog to show partial search results
		Timber.tag(TAG).d("Showing partial search results: $results")
		AlertDialog.Builder(this)
			.setTitle("Search Results")
			.setMessage("Found ${results.size} results. (Dialog not fully implemented)")
			.setPositiveButton("OK", null)
			.show()
	}

	// Implementasi stub showUpdateAvailableDialog dan dismissUpdateAvailableDialog
	open fun showUpdateAvailableDialog(updateInfo: YourUpdateModel) {
		// TODO: Implement dialog to show update available info
		Timber.tag(TAG).d("Update available: $updateInfo")
		AlertDialog.Builder(this)
			.setTitle("Update Available")
			.setMessage("Version ${updateInfo.versionName} is available. (Dialog not fully implemented)")
			.setPositiveButton("Update", null) // TODO: Add actual update logic here
			.setNegativeButton("Cancel", null)
			.show()
	}

	open fun dismissUpdateAvailableDialog() {
		// TODO: Implement logic to dismiss update dialog if needed
		Timber.tag(TAG).d("Dismissing update available dialog")
	}

	// Handle intent from notification
	protected open fun handleIntent(intent: Intent?) {
		Relog.i(TAG, "handleIntent called with action: ${intent?.action}")
		// Hapus logika penanganan ACTION_STOP di sini.
		// Aksi STOP dari tombol notifikasi DITANGANI OLEH RECEIVER DI SERVICE.
		// === PERIKSA EXTRA DARI INTENT ===
		val fromNotification = intent?.getBooleanExtra("from_notification", false) == true
		if (fromNotification) {
			Relog.i(TAG, "Intent originates from notification body click.")
			// === LOGIKA UNTUK MENGATUR STATE UI DEFAULT DI SINI ===
			// Contoh (sesuaikan dengan struktur UI Activity Anda):
			// Jika pakai Drawer Navigation View:
			// binding.drawerLayout.openDrawer(binding.navView) // Mungkin ingin buka drawer
			// binding.navView.setCheckedItem(R.id.nav_home) // Mungkin set item default terpilih di NavView
			// Jika pakai TabLayout dengan ViewPager:
			// binding.viewPager.currentItem = 0 // Set tab pertama sebagai default
			// Jika hanya perlu memastikan View peta terlihat:
			// (Logic ini biasanya sudah default saat Activity dibuka, tapi bisa eksplisit di sini)
			// Contoh memindahkan marker ke lokasi default atau terakhir dari PrefManager saat dibuka dari notif
			moveMapToNewLocation(Location("default").apply { latitude = prefManager.latitude.value.toDouble(); longitude = prefManager.longitude.value.toDouble() }, false) // Contoh
			// Hapus logika yang tidak relevan seperti cek ACTION_STOP
			// if (intent?.action == NotificationsChannel.ACTION_STOP) { ... } // HAPUS LOGIKA INI
		} else {
			Relog.i(TAG, "Intent does NOT originate from notification body.")
			// Jika Intent dari sumber lain (misal, launcher icon, deep link lain)
			// Lakukan penanganan Intent lain jika ada (misal, deep link ke fitur lain)
			// super.handleIntent(intent) // Panggil super jika BaseMapActivity punya logic lain
		}
	}

	// This will be implemented differently in each flavor
	protected open fun onStopButtonClicked() {
		// Metode ini sekarang kosong secara default di kelas dasar.
		// Jika ada kelas turunan (flavor lain) yang perlu menambahkan logic
		// LOKAL DI ACTIVITY saat tombol stop UI diklik *sebelum* mengirim perintah ke Service,
		// bisa meng-override metode ini.
		// Namun, logika utama pengiriman perintah ke Service ada di listener tombol di setupButtons().
		Relog.i(TAG, "Base onStopButtonClicked() called.") // Contoh log
	}
	// =================== Method Stubs & Abstracts ===================
	// --------- Map related ---------
	abstract fun initializeMap()
	abstract fun hasMarker(): Boolean
	abstract fun moveMapToNewLocation(location: Location, moveNewLocation: Boolean = true)

	// --------- Buttons/UI ---------
	abstract fun setupButtons()
	// === Perbaiki updateStartStopButtonUI ===
	// Implementasi dasar untuk update UI tombol Start/Stop berdasarkan state isStarted
	protected open fun updateStartStopButtonUI(isStarted: Boolean) {
		Relog.i(TAG, "Updating Start/Stop button UI based on isStarted: $isStarted")
		runOnUiThread { // Pastikan update UI di main thread
			if (isStarted) {
				binding.startButton.visibility = View.GONE
				binding.stopButton.visibility = View.VISIBLE
				lifecycleScope.launch { viewModel.triggerShowToastEvent(getString(R.string.location_unset)) } // Emit Toast via event
				//binding.addfavorite.visibility = View.VISIBLE // Asumsi add favorite hanya saat started
				//binding.getlocation.visibility = View.GONE // Asumsi get location hanya saat stopped
			} else {
				binding.startButton.visibility = View.VISIBLE
				binding.stopButton.visibility = View.GONE
				lifecycleScope.launch { viewModel.triggerShowToastEvent(getString(R.string.location_set)) } // Emit Toast via event
				//binding.addfavorite.visibility = View.GONE
				//binding.getlocation.visibility = View.VISIBLE
			}
		}
	}

	// --------- Events/Dialogs ---------
	abstract fun addFavoriteDialog() // TODO: Implement this using createAddFavoriteDialog from DialogHelper
	// showFavoriteListDialog() and showAboutDialog() implemented above using DialogHelper
	// showUpdateAvailableDialog() and dismissUpdateAvailableDialog() implemented above (stubs)
	// showPartialSearchResultsDialog() implemented above (stub)
	// showToken(token: String?) implemented below

	// Mengganti nama method dari showGojekToken menjadi showToken
	open fun showToken(token: String?) { // <<< NAMA BARU
		// Optional: update TextView or dialog with token
		// Kamu bisa update TextView di layout atau panggil showTokenDialog di sini
		// Contoh update TextView (jika ada di layout BaseMapActivity atau turunan)
		// binding.textViewToken?.text = "Token: ${token ?: "N/A"}" // Gunakan ID universal
		Timber.tag(TAG).d("Token: ${token ?: "N/A"}")
	}
	open fun onDownloadProgress(progress: Int) { /* Optional: update progress UI */ }
	open fun onDownloadFinished() { /* Optional: handle download finished */ }

	// --------- Location/Permission ---------
	@SuppressLint("MissingPermission") // Anotasi ini diperlukan karena memanggil method locationHelper yang butuh izin
	protected open fun getLastLocation() {
		Relog.i(TAG, "Base getLastLocation() called. Requesting Location updates via helper.")
		// Memanggil method requestLocationUpdates dari locationHelper yang sudah di-inject di BaseMapActivity
		locationHelper.requestLocationUpdates(this) // 'this' adalah Activity yang mengimplementasikan LocationListener
		// Hasil update lokasi real akan diterima di callback onLocationResult/onLocationError
	}

	// Method ini sekarang diasumsikan dipanggil SETELAH izin lokasi ditolak
	//abstract fun handleLocationError()

	// TODO: Tambahkan method untuk memicu permintaan izin lokasi, misal dipanggil dari tombol "Get Location"
	protected fun requestLocationPermission() {
		Timber.tag(TAG).d("Requesting location permissions...")
		// Memanggil requestLocationPermissions dari PermissionHelper dan meneruskan 'this' sebagai listener
		permissionHelper.requestLocationPermissions(this) // 'this' adalah BaseMapActivity yang mengimplementasikan PermissionResultListener
	}

	// Implementasi dari BaseMapActivity: Menangani kesalahan terkait lokasi (SAMA PERSIS, pakai Snackbar & locationHelper)
	protected fun handleLocationError() {
		Relog.i(TAG, "Handling Location Error.")
		Snackbar.make(binding.root, "Location services are disabled.", Snackbar.LENGTH_LONG)
			.setAction("Enable") {
				permissionHelper.openLocationSettings() // Panggil method di ILocationHelper
			}
			.show()
	}
	// --------- Notifications ---------
	//abstract fun showStartNotification(address: String)
	//abstract fun cancelNotification()
}
