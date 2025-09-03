package com.survei.manat

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.survei.manat.utils.DatabaseMigration

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
                Log.d("SplashActivity", "AuthStateListener: User is null, navigating to LoginActivity.")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } else {
                // Jika ada user yang login, jalankan migrasi database terlebih dahulu
                Log.d("SplashActivity", "AuthStateListener: User is signed in with UID: ${currentUser.uid}")
                runDatabaseMigration(currentUser.uid)
            }
        }, 2000) // Durasi splash screen 2 detik
    }

    /**
     * Menjalankan migrasi database sebelum pengecekan peran pengguna
     */
    private fun runDatabaseMigration(userId: String) {
        DatabaseMigration.migrateUserData(userId) { success ->
            if (success) {
                Log.i("SplashActivity", "Database migration completed successfully")
                checkUserRoleAndNavigate()
            } else {
                Log.e("SplashActivity", "Database migration failed")
                // Meskipun migrasi gagal, tetap lanjutkan dengan pengecekan peran
                Toast.makeText(this, "Peringatan: Terjadi masalah dengan data", Toast.LENGTH_SHORT).show()
                checkUserRoleAndNavigate()
            }
        }
    }

    /**
     * Memeriksa peran pengguna dan mengarahkan ke activity yang sesuai
     */
    private fun checkUserRoleAndNavigate() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)
            .get().addOnSuccessListener { snapshot ->
                if (!isFinishing) {
                    if (snapshot.exists()) {
                        val role = snapshot.child("role").getValue(String::class.java)
                        navigateBasedOnRole(role, currentUser.uid)
                    } else {
                        // Jika user ada di Auth tapi tidak ada di Database, logout paksa
                        auth.signOut()
                        Toast.makeText(this, "Data pengguna tidak ditemukan.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }
            }.addOnFailureListener { exception ->
                if (!isFinishing) {
                    // Jika gagal mengambil data dari database, logout paksa
                    Log.e("SplashActivity", "Error fetching user data: ${exception.message}")
                    auth.signOut()
                    Toast.makeText(this, "Gagal memverifikasi pengguna.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
    }

    /**
     * Mengarahkan pengguna ke activity yang sesuai berdasarkan perannya
     */
    private fun navigateBasedOnRole(role: String?, userId: String) {
        when (role) {
            "petugas" -> {
                // Jika perannya petugas, arahkan ke MainActivity
                Log.d("SplashActivity", "Navigating to MainActivity for user: $userId")
                startActivity(Intent(this, MainActivity::class.java))
            }
            "pemeriksa", "admin" -> {
                // Jika perannya pemeriksa atau admin, arahkan ke DashboardActivity
                Log.d("SplashActivity", "Navigating to DashboardActivity for user: $userId")
                startActivity(Intent(this, DashboardActivity::class.java))
            }
            else -> {
                // Jika peran tidak dikenali, logout dan kembali ke Login
                Log.w("SplashActivity", "Unknown role '$role' for user: $userId")
                auth.signOut()
                Toast.makeText(this, "Peran pengguna tidak valid.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
        finish() // Tutup SplashActivity agar tidak bisa kembali
    }

    override fun onResume() {
        super.onResume()
        // Pastikan activity tidak di-destroy sebelum waktunya
        if (isFinishing) {
            return
        }
    }

    override fun onPause() {
        super.onPause()
        // Hapus callback untuk mencegah memory leaks
        Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
    }
}