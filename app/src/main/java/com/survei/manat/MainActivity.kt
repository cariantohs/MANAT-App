package com.survei.manat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var authStateListener: FirebaseAuth.AuthStateListener
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navigationView: NavigationView
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        // Setup OnBackPressedCallback untuk menangani back press dengan cara modern
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (supportFragmentManager.backStackEntryCount > 0) {
                    // Jika ada fragment di back stack, pop back stack
                    supportFragmentManager.popBackStack()
                } else {
                    // Jika tidak ada fragment di back stack, lanjutkan back press default
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Listener ini akan aktif setiap kali status login berubah (login/logout)
        // Ini adalah penjaga keamanan utama aplikasi
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                // Jika tidak ada user yang login, paksa kembali ke halaman Login
                Log.d("MainActivity", "AuthStateListener: User is null, navigating to LoginActivity.")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } else {
                Log.d("MainActivity", "AuthStateListener: User is signed in with UID: ${user.uid}")
            }
        }

        // Hanya setup UI jika ada user yang login saat ini
        if (auth.currentUser != null) {
            setupUI()
            if (savedInstanceState == null) {
                // Saat aplikasi pertama kali dibuka, otomatis tampilkan halaman Pendataan
                navController.navigate(R.id.pendataanFragment)
                navigationView.setCheckedItem(R.id.pendataanFragment)
            }
        }
    }

    private fun setupUI() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        // Temukan NavController dari NavHostFragment
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Tentukan halaman level atas agar tombol hamburger muncul
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.pendataanFragment, R.id.rekapitulasiFragment),
            drawerLayout
        )

        // Hubungkan Toolbar dengan NavController agar judul otomatis berubah
        toolbar.setupWithNavController(navController, appBarConfiguration)

        // Hubungkan bilah menu samping dengan NavController agar navigasi otomatis
        navigationView.setupWithNavController(navController)
        
        // Atur listener logout secara terpisah karena tidak ada di nav_graph
        navigationView.menu.findItem(R.id.nav_logout).setOnMenuItemClickListener {
            auth.signOut()
            // AuthStateListener akan otomatis mendeteksi logout dan pindah ke LoginActivity
            true
        }

        // Tampilkan email user di header
        val headerView = navigationView.getHeaderView(0)
        val navUserEmail = headerView.findViewById<TextView>(R.id.nav_header_email)
        navUserEmail.text = auth.currentUser?.email
    }

    override fun onStart() {
        super.onStart()
        // Mulai mendengarkan status login saat activity terlihat
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        // Berhenti mendengarkan saat activity tidak lagi terlihat
        auth.removeAuthStateListener(authStateListener)
    }

    // Fungsi ini WAJIB ada agar tombol hamburger (garis tiga) bisa berfungsi
    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

    // Hapus metode onBackPressed() yang deprecated
    // Fungsi untuk handle tombol "back" di ponsel sekarang ditangani oleh OnBackPressedCallback
}