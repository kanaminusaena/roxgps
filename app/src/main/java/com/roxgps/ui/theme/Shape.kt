// ui/theme/Shape.kt
package com.roxgps.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    // Bentuk untuk komponen kecil (misal: tombol kecil, chip)
    small = RoundedCornerShape(4.dp),
    // Bentuk untuk komponen sedang (misal: kartu, dialog)
    medium = RoundedCornerShape(8.dp),
    // Bentuk untuk komponen besar (misal: bottom sheet, sheet)
    large = RoundedCornerShape(12.dp),

    // Anda juga bisa mendefinisikan bentuk kustom lainnya,
    // misal CutCornerShape atau bentuk dengan sudut yang berbeda-beda
    // extraSmall = RoundedCornerShape(2.dp),
    // extraLarge = RoundedCornerShape(16.dp)
)