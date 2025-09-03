package com.survei.manat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Cek jika user sudah login, langsung arahkan ke SplashActivity
        if (auth.currentUser != null) {
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }

        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan Password tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Tampilkan progress indicator (jika ada)
            // progressBar.visibility = View.VISIBLE

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // progressBar.visibility = View.GONE
                    
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Login Berhasil!", Toast.LENGTH_SHORT).show()
                        // Arahkan ke SplashActivity untuk pengecekan peran
                        startActivity(Intent(this, SplashActivity::class.java))
                        finish()
                    } else {
                        // Penanganan error yang lebih spesifik
                        val errorMessage = when (task.exception) {
                            is FirebaseAuthInvalidUserException -> "Akun tidak ditemukan."
                            is FirebaseAuthInvalidCredentialsException -> "Email atau password salah."
                            else -> "Login Gagal: ${task.exception?.message}"
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    override fun onBackPressed() {
        // Mencegah user kembali ke activity sebelumnya jika sudah di login screen
        moveTaskToBack(true)
    }
}