package com.survei.manat.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val title: String
) : Parcelable