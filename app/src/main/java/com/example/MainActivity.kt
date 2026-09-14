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
import com.example.ui.screens.MainScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.SignalBlue
import com.example.ui.theme.SkillsExchangeTheme
import com.example.ui.viewmodel.SkillsExchangeViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
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
}

