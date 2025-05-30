package com.roxgps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.roxgps.ui.compose.common.DrawerContent
import com.roxgps.ui.compose.common.DrawerMenuItem
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    state: MapScreenState,
    onSearch: (String) -> Unit,
    onNavMenuClick: () -> Unit,
    onAboutClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onGetLocationClick: () -> Unit,
    onAddFavoriteClick: () -> Unit,
    onDrawerItemSelected: (DrawerMenu) -> Unit,
    drawerContent: @Composable ColumnScope.() -> Unit
) {
    var selectedMenuItem by remember { mutableStateOf<DrawerMenuItem?>(null) }
    val navController = rememberNavController()
    var showAboutDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var checkForUpdates by remember { mutableStateOf(false) }
    var showClearLogDialog by remember { mutableStateOf(false) }
    var showLogFolderDialog by remember { mutableStateOf(false) }
    ModalNavigationDrawer(
        drawerContent = {
            DrawerContent(
                selected = selectedMenuItem,
                onMenuClick = { item ->
                    when(item) {
                        DrawerMenuItem.Favorite -> {
                            // Navigasi ke halaman Favorit
                            navController.navigate("favorite")
                        }
                        DrawerMenuItem.About -> {
                            // Tampilkan dialog About
                            showAboutDialog = true
                        }
                        DrawerMenuItem.Update -> {
                            // Jalankan logika update aplikasi
                            checkForUpdates()
                        }
                        DrawerMenuItem.ClearLog -> {
                            // Bersihkan log aplikasi
                            clearLog()
                        }
                        DrawerMenuItem.ViewLogFolder -> {
                            // Buka folder log
                            openLogFolder()
                        }
                        DrawerMenuItem.Token -> {
                            // Tampilkan token
                            showTokenDialog = true
                        }
                        DrawerMenuItem.Exit -> {
                            // Keluar aplikasi
                            exitApp()
                        }
                    }
                }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            // Kontainer utama (setara RelativeLayout di XML)
            Column(Modifier.fillMaxSize()) {
                // Toolbar
                TopAppBar(
                    title = { Text("RoxGPS") },
                    navigationIcon = {
                        IconButton(onClick = onNavMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onAboutClick) {
                            Icon(Icons.Default.Info, contentDescription = "About")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .zIndex(1f)
                )
                // Search Bar (setara include_search)
                SearchBarCompose(
                    value = state.searchQuery,
                    onValueChange = state.onSearchQueryChange,
                    onSearch = onSearch,
                    isLoading = state.isSearching,
                    modifier = Modifier
                        .padding(start = 18.dp, end = 18.dp, top = 48.dp, bottom = 8.dp)
                        .zIndex(1f)
                )
                // Map Container (setara include map_container)
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Ganti dengan MapView Compose sesuai kebutuhan Anda
                    MapViewComposable(
                        latitude = state.latitude,
                        longitude = state.longitude,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Tombol-tombol vertikal kanan bawah
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 32.dp, bottom = 96.dp)
                    ) {
                        // Start/Stop Button
                        if (state.isStarted) {
                            ActionIconButton(
                                icon = Icons.Default.Stop,
                                contentDescription = "Stop",
                                onClick = onStopClick
                            )
                        } else {
                            ActionIconButton(
                                icon = Icons.Default.PlayArrow,
                                contentDescription = "Start",
                                onClick = onStartClick
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        // Add Favorite
                        ActionIconButton(
                            icon = Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            onClick = onAddFavoriteClick
                        )
                        Spacer(Modifier.height(16.dp))
                        // Get Location
                        ActionIconButton(
                            icon = Icons.Default.MyLocation,
                            contentDescription = "Get Location",
                            onClick = onGetLocationClick
                        )
                    }
                }
            }
        }
    }
}

// Tombol bulat dengan ikon, setara MaterialButton XML
@Composable
fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .alpha(0.95f)
            .shadow(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp)
        )
    }
}

// Placeholder MapView, ganti dengan Map Compose sesuai kebutuhan.
@Composable
fun MapViewComposable(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .background(Color.LightGray)
    ) {
        Text(
            text = "MapView Placeholder: ($latitude, $longitude)",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// SearchBar (setara include_search)
@Composable
fun SearchBarCompose(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Cari alamat...") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { onSearch(value) }) {
                Icon(Icons.Default.MyLocation, contentDescription = "Cari")
            }
        }
    }
}

// Enum untuk menu drawer
enum class DrawerMenu {
    FAVORITE, ABOUT, CLEAR_LOG, VIEW_LOG_FOLDER, TOKEN, EXIT
}

// State holder untuk seluruh MapScreen
data class MapScreenState(
    val searchQuery: String = "",
    val onSearchQueryChange: (String) -> Unit = {},
    val isSearching: Boolean = false,
    val isStarted: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)