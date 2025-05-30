package com.roxgps.ui.compose.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roxgps.R

@Composable
fun DrawerContent(
    selected: DrawerMenuItem?,
    onMenuClick: (DrawerMenuItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = stringResource(R.string.content_desc),
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 10.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
            )
        }
        Divider()
        // Menu Items (isi dari nav_drawer_menu atau main_menu)
        DrawerMenuItem.values().forEach { item ->
            NavigationDrawerItem(
                label = { Text(stringResource(item.titleRes)) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null
                    )
                },
                selected = selected == item,
                onClick = { onMenuClick(item) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

enum class DrawerMenuItem(val titleRes: Int, val icon: ImageVector) {
    Favorite(R.string.nav_fav_title, Icons.Filled.Favorite),
    About(R.string.nav_about_title, Icons.Filled.Info),
    Update(R.string.nav_update_title, Icons.Outlined.Update),
    ClearLog(R.string.nav_clear_log_title, Icons.Outlined.Clear),
    ViewLogFolder(R.string.nav_view_log_folder_title, Icons.Outlined.Description), // Ganti dengan ikon folder log jika ada
    Token(R.string.nav_token_title, Icons.Filled.Token),
    Exit(R.string.nav_exit_title, Icons.Filled.ExitToApp)
}