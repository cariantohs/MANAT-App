package com.survei.manat.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class LocationData(
    val latitude: Double? = null,
    val longitude: Double? = null
)

@IgnoreExtraProperties
data class SurveySession(
    val sampleId: String? = null,
    val startTimeM: Long? = null,
    val locationStartM: LocationData? = null,
    val endTimeM: Long? = null,
    val locationEndM: LocationData? = null,
    val startTimeKp: Long? = null,
    val locationStartKp: LocationData? = null,
    val endTimeKp: Long? = null,
    val locationEndKp: LocationData? = null,
    val startTimeInti: Long? = null,
    val locationStartInti: LocationData? = null,
    val endTimeInti: Long? = null,
    val locationEndInti: LocationData? = null
)