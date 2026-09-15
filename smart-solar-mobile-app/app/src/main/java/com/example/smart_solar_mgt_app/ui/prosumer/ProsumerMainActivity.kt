package com.example.smart_solar_mgt_app.ui.prosumer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.smart_solar_mgt_app.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class ProsumerMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prosumer_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostProsumer) as NavHostFragment
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavProsumer)
        bottomNav.setupWithNavController(navHostFragment.navController)
    }
}
