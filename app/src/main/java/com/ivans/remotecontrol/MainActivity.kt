package com.ivans.remotecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ivans.remotecontrol.network.ApiClient
import com.ivans.remotecontrol.ui.alarms.AlarmsFragment
import com.ivans.remotecontrol.ui.home.HomeFragment
import com.ivans.remotecontrol.ui.settings.SettingsFragment
import com.ivans.remotecontrol.utils.PreferencesManager
import com.ivans.remotecontrol.dialogs.SecurityDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView
    private var securityDialog: SecurityDialog? = null
    private var isShowingSecurityDialog = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Auto-load saved server URL
        val preferencesManager = PreferencesManager(this)
        val savedUrl = preferencesManager.getServerUrl()
        ApiClient.updateServerUrl(savedUrl)
        ApiClient.setPreferencesManager(preferencesManager) // Add this line

        // Rest of your existing code...
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setupTranslucentNavigation()
        setupViewPager()
        setupBottomNavigation()
        setupSystemBarPaddingSafe()
        createNotificationChannel()

        // Check if we need authentication on startup
        checkInitialAuthentication()
    }

    private fun setupTranslucentNavigation() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun setupViewPager() {
        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = ViewPagerAdapter(this)
        viewPager.isUserInputEnabled = true
    }

    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    viewPager.currentItem = 0
                    true
                }
                R.id.nav_alarms -> {
                    viewPager.currentItem = 1
                    true
                }
                R.id.nav_settings -> {
                    viewPager.currentItem = 2
                    true
                }
                else -> false
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> bottomNavigation.selectedItemId = R.id.nav_home
                    1 -> bottomNavigation.selectedItemId = R.id.nav_alarms
                    2 -> bottomNavigation.selectedItemId = R.id.nav_settings
                }
            }
        })
    }

    private fun setupSystemBarPaddingSafe() {
        // Apply to the entire ViewPager container
        ViewCompat.setOnApplyWindowInsetsListener(viewPager) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        // Also apply to bottom navigation
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screenshots",
                "Screenshots",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Screenshot notifications"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ================================
    // AUTHENTICATION METHODS (PUBLIC)
    // ================================

    private fun checkInitialAuthentication() {
        lifecycleScope.launch {
            try {
                val authResult = ApiClient.checkAuthStatus()
                when (authResult) {
                    ApiClient.AuthResult.LOCKED,
                    ApiClient.AuthResult.TEMPORARILY_LOCKED -> {
                        showSecurityDialog {
                            // Authentication successful, continue with app
                        }
                    }
                    ApiClient.AuthResult.UNLOCKED -> {
                        // Already authenticated, continue normally
                    }
                    ApiClient.AuthResult.ERROR -> {
                        // Connection issues, let the app handle it normally
                    }
                    else -> {
                        // Other states, continue normally
                    }
                }
            } catch (e: Exception) {
                // If we can't check auth status, let the app continue
                // The fragments will handle auth as needed
            }
        }
    }

    fun showSecurityDialog(onUnlocked: () -> Unit) {
        if (isShowingSecurityDialog) {
            Log.d("MainActivity", "Security dialog already showing, ignoring request")
            return
        }

        // Dismiss any existing dialog first
        securityDialog?.dismissAllowingStateLoss()
        securityDialog = null

        isShowingSecurityDialog = true
        Log.d("MainActivity", "Showing new security dialog")

        SecurityDialog.show(supportFragmentManager) { sessionToken ->
            isShowingSecurityDialog = false
            Log.d("MainActivity", "Security dialog callback received")

            // Store session token and proceed
            if (sessionToken.isNotEmpty()) {
                onUnlocked()
            } else {
                onUnlocked()
            }
        }
    }

    // Add this method to reset the flag if needed
    override fun onResume() {
        super.onResume()
        // Reset flag if no dialog is actually showing
        if (supportFragmentManager.findFragmentByTag("SecurityDialog") == null) {
            isShowingSecurityDialog = false
        }
    }

    fun checkAuthenticationAndProceed(action: () -> Unit) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getAuthStatus()
                if (response.isSuccessful) {
                    val authStatus = response.body()
                    if (authStatus?.locked == true) {
                        showSecurityDialog {
                            action()
                        }
                    } else {
                        action()
                    }
                } else {
                    // Assume locked if we can't check status
                    showSecurityDialog {
                        action()
                    }
                }
            } catch (e: Exception) {
                // On network error, show security dialog
                showSecurityDialog {
                    action()
                }
            }
        }
    }

    // Utility methods for external components
    fun showScreenshotDialog(screenshotId: String) {
        if (!isFinishing && !isDestroyed) {
            findViewById<View>(android.R.id.content).post {
                val currentFragment = getCurrentFragment()
                if (currentFragment is HomeFragment) {
                    com.ivans.remotecontrol.dialogs.ScreenshotDialog.show(
                        supportFragmentManager,
                        screenshotId
                    )
                }
            }
        }
    }

    fun navigateToTab(tabIndex: Int) {
        if (tabIndex in 0..2) {
            viewPager.currentItem = tabIndex
        }
    }

    fun getCurrentFragment(): Fragment? {
        return supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
    }

    // Handle API calls that require authentication
    fun handleApiCall(action: () -> Unit) {
        checkAuthenticationAndProceed(action)
    }

    // Check if we have a valid session
    fun hasValidSession(): Boolean {
        return ApiClient.hasValidSession()
    }

    // Clear session (for logout functionality)
    fun clearSession() {
        ApiClient.clearSession()
    }

    private class ViewPagerAdapter(fragmentActivity: FragmentActivity) :
        FragmentStateAdapter(fragmentActivity) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment()
                1 -> AlarmsFragment()
                2 -> SettingsFragment()
                else -> HomeFragment()
            }
        }
    }
}