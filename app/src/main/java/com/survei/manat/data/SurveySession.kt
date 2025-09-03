package com.survei.manat.data

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
    val locationEndInti: LocationData? = null,
    val photoDriveId: String? = null
) {
    /**
     * Mengonversi objek SurveySession ke Map untuk Firebase
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "sampleId" to sampleId,
            "startTimeM" to startTimeM,
            "locationStartM" to locationStartM?.toMap(),
            "endTimeM" to endTimeM,
            "locationEndM" to locationEndM?.toMap(),
            "startTimeKp" to startTimeKp,
            "locationStartKp" to locationStartKp?.toMap(),
            "endTimeKp" to endTimeKp,
            "locationEndKp" to locationEndKp?.toMap(),
            "startTimeInti" to startTimeInti,
            "locationStartInti" to locationStartInti?.toMap(),
            "endTimeInti" to endTimeInti,
            "locationEndInti" to locationEndInti?.toMap(),
            "photoDriveId" to photoDriveId
        )
    }
    
    /**
     * Memeriksa apakah semua kuesioner telah diselesaikan
     */
    fun isFullyCompleted(): Boolean {
        return endTimeInti != null
    }
}

data class LocationData(
    val latitude: Double,
    val longitude: Double
) {
    /**
     * Mengonversi objek LocationData ke Map untuk Firebase
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "latitude" to latitude,
            "longitude" to longitude
        )
    }
}