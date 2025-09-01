package com.survei.manat.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserProfile(
    val nama: String? = null,
    val email: String? = null,
    val role: String? = null,
    val kecamatan: String? = null,
    val desa: String? = null,
    val nks: String? = null,
    val dusun: String? = null,
    val bawahan: Map<String, Boolean>? = null
)