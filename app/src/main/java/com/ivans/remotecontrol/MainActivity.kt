package com.ivans.remotecontrol

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView
    private var securityDialog: SecurityDialog? = null
    private var isShowingSecurityDialog = false

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Auto-load saved server URL
        val preferencesManager = PreferencesManager(this)
        val savedUrl = preferencesManager.getServerUrl()
        ApiClient.updateServerUrl(savedUrl)
        ApiClient.setPreferencesManager(preferencesManager)

        // Setup UI
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setupTranslucentNavigation()
        setupViewPager()
        setupBottomNavigation()
        setupSystemBarPaddingSafe()
        createNotificationChannel()

        // Request notification permission for Android 13+
        requestNotificationPermission()

        // Check if we need authentication on startup
        checkInitialAuthentication()
    }

    private fun performVibration() {
        val preferencesManager = PreferencesManager(this)
        if (preferencesManager.getVibrationEnabled()) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(
                    100,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            NOTIFICATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "Notification permission granted")
                } else {
                    Log.d("MainActivity", "Notification permission denied")
                }
            }
        }
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
        ViewCompat.setOnApplyWindowInsetsListener(viewPager) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

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
                NotificationManager.IMPORTANCE_HIGH // Changed to HIGH to ensure they show
            ).apply {
                description = "Screenshot notifications"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ================================
    // AUTHENTICATION METHODS
    // ================================

    private fun checkInitialAuthentication() {
        lifecycleScope.launch {
            try {
                val authResult = ApiClient.checkAuthStatus()
                when (authResult) {
                    ApiClient.AuthResult.LOCKED,
                    ApiClient.AuthResult.TEMPORARILY_LOCKED -> {
                        showSecurityDialog {
                            // Authentication successful
                        }
                    }
                    ApiClient.AuthResult.UNLOCKED -> {
                        // Already authenticated
                    }
                    ApiClient.AuthResult.ERROR -> {
                        // Connection issues
                    }
                    else -> {
                        // Other states
                    }
                }
            } catch (e: Exception) {
                // Let the app continue
            }
        }
    }

    fun showSecurityDialog(onUnlocked: () -> Unit) {
        if (isShowingSecurityDialog) {
            Log.d("MainActivity", "Security dialog already showing")
            return
        }

        securityDialog?.dismissAllowingStateLoss()
        securityDialog = null

        isShowingSecurityDialog = true
        Log.d("MainActivity", "Showing security dialog")

        SecurityDialog.show(supportFragmentManager) { sessionToken ->
            isShowingSecurityDialog = false
            Log.d("MainActivity", "Security dialog completed")

            if (sessionToken.isNotEmpty()) {
                onUnlocked()
            } else {
                onUnlocked()
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
                        showSecurityDialog { action() }
                    } else {
                        action()
                    }
                } else {
                    showSecurityDialog { action() }
                }
            } catch (e: Exception) {
                showSecurityDialog { action() }
            }
        }
    }

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

    fun handleApiCall(action: () -> Unit) {
        checkAuthenticationAndProceed(action)
    }

    fun hasValidSession(): Boolean {
        return ApiClient.hasValidSession()
    }

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