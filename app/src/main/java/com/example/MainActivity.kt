package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import com.example.data.remote.FirebaseService
import com.example.ui.screens.MainScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.SignalBlue
import com.example.ui.theme.SkillsExchangeTheme
import com.example.ui.viewmodel.SkillsExchangeViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // C2-1: Firebase Phone Auth needs a live Activity for its Play-Integrity/reCAPTCHA
    // fallback. Registering here is what allows real SMS code verification to run instead of
    // the previous placeholder that accepted any six-digit code.
    FirebaseService.attachPhoneAuthActivity(this)
    enableEdgeToEdge()
    setContent {
      val viewModel: SkillsExchangeViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()
      val isSessionRestored by viewModel.isSessionRestored.collectAsStateWithLifecycle()

      SkillsExchangeTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
          if (!isSessionRestored) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator(color = SignalBlue)
            }
          } else if (isOnboardingCompleted) {
            MainScreen(viewModel = viewModel)
          } else {
            OnboardingScreen(
              viewModel = viewModel,
              onOnboardingComplete = {
                // State change automatically renders MainScreen
              }
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    // C2-1: never keep a destroyed Activity referenced by the phone-auth holder.
    FirebaseService.detachPhoneAuthActivity(this)
    super.onDestroy()
  }
}

