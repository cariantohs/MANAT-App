package com.survei.manat

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.survei.manat.data.MapPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapActivity : AppCompatActivity() {
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))

        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val mapController = mapView.controller
        mapController.setZoom(12.0)

        val mapPoints = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("map_points", MapPoint::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<MapPoint>("map_points")
        }

        if (!mapPoints.isNullOrEmpty()) {
            val centerPoint = GeoPoint(mapPoints[0].latitude, mapPoints[0].longitude)
            mapController.setCenter(centerPoint)

            for (point in mapPoints) {
                val marker = Marker(mapView)
                marker.position = GeoPoint(point.latitude, point.longitude)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = point.title
                mapView.overlays.add(marker)
            }
        } else {
            val defaultCenter = GeoPoint(2.62, 98.78)
            mapController.setCenter(defaultCenter)
        }

        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
