package io.github.lrosa2.carcare.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.lrosa2.carcare.domain.DueStatus

private val Blue = Color(0xFF1E5A8A)

@Composable
fun CarCareTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9CCAF5))
        else -> lightColorScheme(primary = Blue)
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Fixed status colours so red/amber/green mean the same with any wallpaper colours. */
data class StatusColors(val container: Color, val content: Color, val label: String)

@Composable
fun statusColors(status: DueStatus): StatusColors {
    val dark = isSystemInDarkTheme()
    return when (status) {
        DueStatus.OVERDUE -> StatusColors(if (dark) Color(0xFF8C1D18) else Color(0xFFFFDAD6), if (dark) Color(0xFFFFDAD6) else Color(0xFF8C1D18), "Overdue")
        DueStatus.NEVER_DONE -> StatusColors(if (dark) Color(0xFF8C1D18) else Color(0xFFFFDAD6), if (dark) Color(0xFFFFDAD6) else Color(0xFF8C1D18), "Due now")
        DueStatus.DUE_SOON -> StatusColors(if (dark) Color(0xFF6B4500) else Color(0xFFFFE0B2), if (dark) Color(0xFFFFE0B2) else Color(0xFF6B4500), "Due soon")
        DueStatus.OK -> StatusColors(if (dark) Color(0xFF1B5E20) else Color(0xFFDCEFD8), if (dark) Color(0xFFDCEFD8) else Color(0xFF1B5E20), "OK")
        DueStatus.NO_SCHEDULE -> StatusColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "No schedule")
    }
}
