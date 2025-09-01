package com.survei.manat

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SplashActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        // Tampilkan splash screen selama 2 detik, lalu jalankan pengecekan
        Handler(Looper.getMainLooper()).postDelayed({
            if (currentUser == null) {
                // Jika tidak ada user yang login, arahkan ke halaman Login
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } else {
                // Jika ada user yang login, cek perannya di Realtime Database
                FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)
                    .get().addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val role = snapshot.child("role").getValue(String::class.java)
                            when (role) {
                                "petugas" -> {
                                    // Jika perannya petugas, arahkan ke MainActivity
                                    startActivity(Intent(this, MainActivity::class.java))
                                }
                                "pemeriksa", "admin" -> {
                                    // Jika perannya pemeriksa atau admin, arahkan ke DashboardActivity
                                    startActivity(Intent(this, DashboardActivity::class.java))
                                }
                                else -> {
                                    // Jika peran tidak dikenali, logout dan kembali ke Login
                                    auth.signOut()
                                    Toast.makeText(this, "Peran pengguna tidak valid.", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, LoginActivity::class.java))
                                }
                            }
                        } else {
                            // Jika user ada di Auth tapi tidak ada di Database, logout paksa
                            auth.signOut()
                            Toast.makeText(this, "Data pengguna tidak ditemukan.", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, LoginActivity::class.java))
                        }
                        finish() // Tutup SplashActivity agar tidak bisa kembali
                    }.addOnFailureListener {
                        // Jika gagal mengambil data dari database, logout paksa
                        auth.signOut()
                        Toast.makeText(this, "Gagal memverifikasi pengguna.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
            }
        }, 2000) // Durasi splash screen 2 detik
    }
}