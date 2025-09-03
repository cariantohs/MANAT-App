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

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar_dashboard)
        setSupportActionBar(toolbar)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout_dashboard)
        val navigationView = findViewById<NavigationView>(R.id.nav_view_dashboard)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.dashboard_nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        toolbar.setupWithNavController(navController, appBarConfiguration)
        navigationView.setupWithNavController(navController)
        
        navigationView.menu.findItem(R.id.nav_logout_dashboard).setOnMenuItemClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            true
        }

        val headerView = navigationView.getHeaderView(0)
        val navUserEmail = headerView.findViewById<TextView>(R.id.nav_header_email)
        navUserEmail.text = auth.currentUser?.email
    }
}