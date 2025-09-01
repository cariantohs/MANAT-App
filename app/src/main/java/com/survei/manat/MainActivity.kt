package com.survei.manat

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)

        // Temukan NavController dari NavHostFragment
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Tentukan halaman level atas agar tombol hamburger muncul
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.pendataanFragment, R.id.rekapitulasiFragment, R.id.konsepFragment),
            drawerLayout
        )

        // Hubungkan Toolbar dengan NavController agar judul otomatis berubah
        toolbar.setupWithNavController(navController, appBarConfiguration)

        // Hubungkan bilah menu samping dengan NavController agar navigasi otomatis
        navigationView.setupWithNavController(navController)
        
        // Atur listener logout secara terpisah
        navigationView.menu.findItem(R.id.nav_logout).setOnMenuItemClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            true
        }

        // Tampilkan email user di header
        val headerView = navigationView.getHeaderView(0)
        val navUserEmail = headerView.findViewById<TextView>(R.id.nav_header_email)
        navUserEmail.text = auth.currentUser?.email
    }

    // Fungsi ini WAJIB ada agar tombol hamburger (garis tiga) bisa berfungsi
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    // Fungsi untuk handle tombol "back" di ponsel
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}