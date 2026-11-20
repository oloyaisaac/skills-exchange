package com.example.util

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * R02-P2-06: keep sensitive surfaces out of screenshots, screen recordings and the recents
 * thumbnail.
 *
 * Applied while an identity/PIN surface is on screen (doorstep PIN, NIN capture, guarantor review)
 * and cleared when it leaves composition, so the rest of the app is unaffected.
 */
@Composable
fun SecureScreen() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
