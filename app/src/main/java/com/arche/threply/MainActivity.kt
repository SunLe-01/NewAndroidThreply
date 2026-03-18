package com.arche.threply

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arche.threply.data.PrefsManager
import com.arche.threply.ui.home.HomeScreen
import com.arche.threply.ui.onboarding.OnboardingScreen
import com.arche.threply.ui.paywall.PaywallScreen
import com.arche.threply.ui.theme.ThreplyTheme
import com.arche.threply.ui.theme.threplyPalette

/**
 * Single Activity entry point for Threply Android.
 * Equivalent to iOS threplyApp -> ContentView.
 *
 * Routes between OnboardingScreen and HomeScreen based on onboarding completion state,
 * mirroring iOS ContentView's hasCompletedOnboarding logic.
 */
class MainActivity : AppCompatActivity() {

    private val _deepLinkPaywall = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(PrefsManager.getThemePreference(this).nightMode)
        installSplashScreen().setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)

        setContent {
            ThreplyTheme {
                ThreplyRoot(showPaywallFromDeepLink = _deepLinkPaywall)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "threply" && uri.host == "paywall") {
            _deepLinkPaywall.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreplyRoot(showPaywallFromDeepLink: MutableState<Boolean>) {
    val context = LocalContext.current
    val palette = threplyPalette()
    var hasCompletedOnboarding by remember {
        mutableStateOf(PrefsManager.hasCompletedOnboarding(context))
    }

    AnimatedContent(
        targetState = hasCompletedOnboarding,
        transitionSpec = {
            fadeIn() + scaleIn(initialScale = 0.96f) togetherWith
                    fadeOut() + scaleOut(targetScale = 0.96f)
        },
        modifier = Modifier.fillMaxSize(),
        label = "rootTransition"
    ) { onboardingDone ->
        if (onboardingDone) {
            HomeScreen(
                onShowOnboarding = {
                    PrefsManager.setCompletedOnboarding(context, false)
                    hasCompletedOnboarding = false
                },
                onNavigateToPaywall = {
                    showPaywallFromDeepLink.value = true
                }
            )
        } else {
            OnboardingScreen(
                onFinish = {
                    PrefsManager.setCompletedOnboarding(context, true)
                    hasCompletedOnboarding = true
                }
            )
        }
    }

    if (showPaywallFromDeepLink.value) {
        ModalBottomSheet(
            onDismissRequest = { showPaywallFromDeepLink.value = false },
            containerColor = palette.bottomSheetSurface,
        ) {
            PaywallScreen(onDismiss = { showPaywallFromDeepLink.value = false })
        }
    }
}
