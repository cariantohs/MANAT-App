package com.survei.manat.data

data class RekapItem(
    val sampleId: String,
    val status: String,
    val durationInMinutes: Long?,
    val coordinate: LocationData?
)