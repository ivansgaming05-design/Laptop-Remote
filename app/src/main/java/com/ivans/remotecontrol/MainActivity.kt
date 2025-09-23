package com.ivans.remotecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Auto-load saved server URL
        val preferencesManager = PreferencesManager(this)
        val savedUrl = preferencesManager.getServerUrl()
        ApiClient.updateServerUrl(savedUrl)

        // Rest of your existing code...
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setupTranslucentNavigation()
        setupViewPager()
        setupBottomNavigation()
        setupSystemBarPaddingSafe()
        createNotificationChannel()
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