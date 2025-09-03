package com.survei.manat.utils

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.survei.manat.data.SurveySession

object DatabaseMigration {
    private const val TAG = "DatabaseMigration"

    /**
     * Memigrasikan data pengguna untuk memperbaiki struktur data yang tidak konsisten
     */
    fun migrateUserData(uid: String, onComplete: (Boolean) -> Unit) {
        val database = FirebaseDatabase.getInstance()
        val surveysRef = database.getReference("surveys").child(uid)

        surveysRef.get().addOnSuccessListener { snapshot ->
            var migrationNeeded = false
            val updates = mutableMapOf<String, Any>()

            for (sampleSnapshot in snapshot.children) {
                // PERBAIKAN: Simpan 'key' ke variabel lokal
                val sampleKey = sampleSnapshot.key
                if (sampleKey == null) continue // Lewati jika key null

                for (kunjunganSnapshot in sampleSnapshot.children) {
                    val kunjunganKey = kunjunganSnapshot.key
                    if (kunjunganKey == null) continue // Lewati jika key null

                    val value = kunjunganSnapshot.value
                    if (value is Long) {
                        // Ini adalah data yang corrupt - konversi ke struktur yang benar
                        migrationNeeded = true
                        Log.w(TAG, "Migrating corrupted data at $sampleKey/$kunjunganKey")

                        // Buat objek SurveySession yang benar
                        val session = SurveySession(
                            sampleId = sampleKey, // Gunakan variabel lokal
                            endTimeInti = value
                        )

                        updates["$sampleKey/$kunjunganKey"] = session.toMap()
                    } else if (value is Map<*, *>) {
                        // Periksa apakah data sudah dalam format yang benar
                        @Suppress("UNCHECKED_CAST")
                        val map = value as Map<String, Any>
                        if (!map.containsKey("sampleId")) {
                            // Tambahkan sampleId jika tidak ada
                            migrationNeeded = true
                            Log.w(TAG, "Adding sampleId to data at $sampleKey/$kunjunganKey")

                            val updatedData = map.toMutableMap()
                            updatedData["sampleId"] = sampleKey // Gunakan variabel lokal
                            updates["$sampleKey/$kunjunganKey"] = updatedData
                        }
                    }
                }
            }

            if (migrationNeeded) {
                Log.i(TAG, "Performing database migration for user: $uid")
                surveysRef.updateChildren(updates).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i(TAG, "Database migration completed successfully for user: $uid")
                        onComplete(true)
                    } else {
                        Log.e(TAG, "Database migration failed for user: $uid - ${task.exception?.message}")
                        onComplete(false)
                    }
                }
            } else {
                Log.i(TAG, "No database migration needed for user: $uid")
                onComplete(true)
            }
        }.addOnFailureListener {
            Log.e(TAG, "Failed to check database migration for user: $uid - ${it.message}")
            onComplete(false)
        }
    }
}