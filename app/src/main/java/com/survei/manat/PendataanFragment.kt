package com.survei.manat

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.survei.manat.data.LocationData
import com.survei.manat.data.SurveySession
import com.survei.manat.data.UserProfile
import java.text.SimpleDateFormat
import java.util.*

class PendataanFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val surveyDataMap = mutableMapOf<String, Any?>()

    // Variabel UI
    private lateinit var tvWilayahInfo: TextView
    private lateinit var spinnerKunjungan: Spinner
    private lateinit var spinnerSampleId: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStartM: Button
    private lateinit var btnEndM: Button
    private lateinit var btnStartKp: Button
    private lateinit var btnEndKp: Button
    private lateinit var btnStartInti: Button
    private lateinit var btnEndInti: Button
    private lateinit var btnSave: Button
    private lateinit var tvMStart: TextView
    private lateinit var tvMEnd: TextView
    private lateinit var tvKpStart: TextView
    private lateinit var tvKpEnd: TextView
    private lateinit var tvIntiStart: TextView
    private lateinit var tvIntiEnd: TextView

    // Variabel state
    private val allSampleNumbers = (1..10).map { it.toString() }
    private var completedSamplesForCurrentVisit = setOf<String>()
    private var isSpinnerSetupComplete = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pendataan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        bindViews(view)
        loadPetugasInfo()
        setupKunjunganSpinner()
    }

    private fun bindViews(view: View) {
        tvWilayahInfo = view.findViewById(R.id.tv_wilayah_info)
        spinnerKunjungan = view.findViewById(R.id.spinner_kunjungan)
        spinnerSampleId = view.findViewById(R.id.spinner_sample_id)
        progressBar = view.findViewById(R.id.progress_bar_pendataan)
        btnStartM = view.findViewById(R.id.btn_start_m)
        btnEndM = view.findViewById(R.id.btn_end_m)
        btnStartKp = view.findViewById(R.id.btn_start_kp)
        btnEndKp = view.findViewById(R.id.btn_end_kp)
        btnStartInti = view.findViewById(R.id.btn_start_inti)
        btnEndInti = view.findViewById(R.id.btn_end_inti)
        btnSave = view.findViewById(R.id.btn_save)
        tvMStart = view.findViewById(R.id.tv_m_start)
        tvMEnd = view.findViewById(R.id.tv_m_end)
        tvKpStart = view.findViewById(R.id.tv_kp_start)
        tvKpEnd = view.findViewById(R.id.tv_kp_end)
        tvIntiStart = view.findViewById(R.id.tv_inti_start)
        tvIntiEnd = view.findViewById(R.id.tv_inti_end)
    }
    
    private fun loadPetugasInfo() {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("users").child(uid).get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            val userProfile = snapshot.getValue(UserProfile::class.java)
            userProfile?.let {
                val wilayahText = "Wilayah: ${it.kecamatan}, ${it.desa}, ${it.dusun} (NKS: ${it.nks})"
                tvWilayahInfo.text = wilayahText
            }
        }
    }
    
    private fun setupKunjunganSpinner() {
        val kunjunganNumbers = (1..5).map { "Kunjungan Ke-$it" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, kunjunganNumbers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerKunjungan.adapter = adapter

        spinnerKunjungan.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadCompletedSamplesAndUpdateSampleSpinner()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }
    
    private fun loadCompletedSamplesAndUpdateSampleSpinner() {
        val kunjungan = "kunjungan_${spinnerKunjungan.selectedItemPosition + 1}"
        val uid = auth.currentUser?.uid ?: return
        val surveysRef = database.getReference("surveys").child(uid)

        progressBar.visibility = View.VISIBLE
        surveysRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            val completed = mutableSetOf<String>()
            for (sampleSnapshot in snapshot.children) {
                if (sampleSnapshot.child(kunjungan).exists()) {
                    val session = sampleSnapshot.child(kunjungan).getValue(SurveySession::class.java)
                    if (session?.endTimeInti != null) {
                         completed.add(sampleSnapshot.key ?: "")
                    }
                }
            }
            completedSamplesForCurrentVisit = completed
            
            val availableSamples = allSampleNumbers.filter { !"SAMPEL_$it" in completedSamplesForCurrentVisit }
            val sampleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableSamples)
            sampleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerSampleId.adapter = sampleAdapter
            
            if (availableSamples.isEmpty()) {
                Toast.makeText(context, "Semua sampel untuk kunjungan ini sudah selesai.", Toast.LENGTH_LONG).show()
                resetUIForNewSample()
            } else {
                 // Picu pemuatan data untuk item pertama yang terpilih secara otomatis
                loadDataForCurrentSelection()
            }
            
            setupSampleSpinnerListener()
            progressBar.visibility = View.GONE
        }
    }

    private fun setupSampleSpinnerListener() {
         spinnerSampleId.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if(isSpinnerSetupComplete) { // Hanya jalankan jika setup awal selesai
                    loadDataForCurrentSelection()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {
                resetUIForNewSample()
            }
        }
        isSpinnerSetupComplete = true
    }
    
    private fun loadDataForCurrentSelection() {
        if (spinnerSampleId.selectedItem == null) {
            resetUIForNewSample()
            return
        }
        val kunjungan = "kunjungan_${spinnerKunjungan.selectedItemPosition + 1}"
        val sampel = "SAMPEL_${spinnerSampleId.selectedItem.toString()}"
        val uid = auth.currentUser?.uid ?: return
        
        progressBar.visibility = View.VISIBLE
        database.getReference("surveys").child(uid).child(sampel).child(kunjungan).get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                if (snapshot.exists()) {
                    val session = snapshot.getValue(SurveySession::class.java)
                    surveyDataMap.clear()
                    session?.let { restoreUiState(it) }
                } else {
                    resetUIForNewSample()
                }
                progressBar.visibility = View.GONE
            }.addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Gagal memuat data sampel.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun restoreUiState(session: SurveySession) {
        resetUIForNewSample()
        var lastStepKey = ""
        
        fun processTimestamp(timestamp: Long?, key: String, textView: TextView, baseText: String) {
            timestamp?.let {
                textView.text = formatTextView(baseText, it)
                surveyDataMap[key] = it
                lastStepKey = key
            }
        }
        
        processTimestamp(session.startTimeM, "startTimeM", tvMStart, "Mulai VSEN25.M")
        processTimestamp(session.endTimeM, "endTimeM", tvMEnd, "Selesai VSEN25.M")
        processTimestamp(session.startTimeKp, "startTimeKp", tvKpStart, "Mulai VSEN25.KP")
        processTimestamp(session.endTimeKp, "endTimeKp", tvKpEnd, "Selesai VSEN25.KP")
        processTimestamp(session.startTimeInti, "startTimeInti", tvIntiStart, "Mulai VSERUTI25.INTI")
        processTimestamp(session.endTimeInti, "endTimeInti", tvIntiEnd, "Selesai VSERUTI25.INTI")
        
        updateButtonVisibility(lastStepKey)
    }

    private fun saveSurveyDataToFirebase() {
        if (spinnerSampleId.selectedItem == null) {
             Toast.makeText(context, "Tidak ada sampel yang dipilih.", Toast.LENGTH_SHORT).show()
             return
        }
        
        val kunjunganInt = spinnerKunjungan.selectedItemPosition + 1
        val kunjungan = "kunjungan_$kunjunganInt"
        val sampel = "SAMPEL_${spinnerSampleId.selectedItem.toString()}"
        val uid = auth.currentUser?.uid ?: return

        // Logika save parsial untuk Kunjungan ke-1
        if (kunjunganInt == 1 && surveyDataMap["endTimeInti"] == null) {
            val lastTimestamp = surveyDataMap["endTimeInti"] ?: surveyDataMap["startTimeInti"] ?: surveyDataMap["endTimeKp"] ?: surveyDataMap["startTimeKp"] ?: surveyDataMap["endTimeM"] ?: surveyDataMap["startTimeM"]
            if (lastTimestamp != null) {
                surveyDataMap["endTimeInti"] = lastTimestamp
            }
        } else if (kunjunganInt > 1 && surveyDataMap["endTimeInti"] == null) {
            Toast.makeText(context, "Untuk kunjungan lanjutan, semua kuesioner harus diselesaikan.", Toast.LENGTH_LONG).show()
            return
        }
        
        val finalSurveyData = SurveySession(
            sampleId = sampel,
            startTimeM = surveyDataMap["startTimeM"] as? Long,
            locationStartM = surveyDataMap["locationStartM"] as? LocationData,
            endTimeM = surveyDataMap["endTimeM"] as? Long,
            locationEndM = surveyDataMap["locationEndM"] as? LocationData,
            startTimeKp = surveyDataMap["startTimeKp"] as? Long,
            locationStartKp = surveyDataMap["locationStartKp"] as? LocationData,
            endTimeKp = surveyDataMap["endTimeKp"] as? Long,
            locationEndKp = surveyDataMap["locationEndKp"] as? LocationData,
            startTimeInti = surveyDataMap["startTimeInti"] as? Long,
            locationStartInti = surveyDataMap["locationStartInti"] as? LocationData,
            endTimeInti = surveyDataMap["endTimeInti"] as? Long,
            locationEndInti = surveyDataMap["locationEndInti"] as? LocationData
        )

        progressBar.visibility = View.VISIBLE
        database.getReference("surveys").child(uid).child(sampel).child(kunjungan)
            .setValue(finalSurveyData)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Sukses! Data untuk $sampel ($kunjungan) telah disimpan.", Toast.LENGTH_LONG).show()
                loadCompletedSamplesAndUpdateSampleSpinner()
            }.addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Gagal menyimpan data.", Toast.SHORT).show()
            }
    }

    @SuppressLint("MissingPermission")
    private fun captureTimestampAndLocation(timeKey: String, locationKey: String, targetTextView: TextView) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location == null) {
                Toast.makeText(context, "Gagal mendapatkan lokasi. Pastikan GPS aktif.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            if (location.isFromMockProvider) {
                Toast.makeText(context, "Aplikasi Fake GPS terdeteksi! Nonaktifkan untuk melanjutkan.", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            val currentTime = System.currentTimeMillis()
            val locationData = LocationData(location.latitude, location.longitude)
            surveyDataMap[timeKey] = currentTime
            surveyDataMap[locationKey] = locationData

            val baseText = targetTextView.text.toString().substringBefore(':')
            targetTextView.text = formatTextView(baseText, currentTime)
            
            val dmsLocation = "${toDMS(location.latitude)}, ${toDMS(location.longitude)}"
            Log.d("PendataanFragment", "Lokasi diambil: $dmsLocation")

            updateButtonVisibility(timeKey)
        }
    }

    private fun updateButtonVisibility(lastKey: String) {
        val allButtons = listOf(btnStartM, btnEndM, btnStartKp, btnEndKp, btnStartInti, btnEndInti, btnSave)
        allButtons.forEach { it.visibility = View.GONE }

        when (lastKey) {
            "startTimeM" -> btnEndM.visibility = View.VISIBLE
            "endTimeM" -> btnStartKp.visibility = View.VISIBLE
            "startTimeKp" -> btnEndKp.visibility = View.VISIBLE
            "endTimeKp" -> btnStartInti.visibility = View.VISIBLE
            "startTimeInti" -> btnEndInti.visibility = View.VISIBLE
            "endTimeInti" -> btnSave.visibility = View.VISIBLE
            else -> btnStartM.visibility = View.VISIBLE // Default state
        }
    }
    
    private fun resetUIForNewSample() {
        surveyDataMap.clear()

        tvMStart.text = "Mulai VSEN25.M: -"
        tvMEnd.text = "Selesai VSEN25.M: -"
        tvKpStart.text = "Mulai VSEN25.KP: -"
        tvKpEnd.text = "Selesai VSEN25.KP: -"
        tvIntiStart.text = "Mulai VSERUTI25.INTI: -"
        tvIntiEnd.text = "Selesai VSERUTI25.INTI: -"

        updateButtonVisibility("") // Reset to initial state
        spinnerKunjungan.isEnabled = true
        spinnerSampleId.isEnabled = true
    }

    private fun formatTextView(baseText: String, timestamp: Long): String {
        return "$baseText: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))}"
    }

    private fun toDMS(coordinate: Double): String {
        val absolute = Math.abs(coordinate)
        val degrees = Math.floor(absolute).toInt()
        val minutesNotTruncated = (absolute - degrees) * 60
        val minutes = Math.floor(minutesNotTruncated).toInt()
        val seconds = Math.floor((minutesNotTruncated - minutes) * 60)
        return "$degrees°$minutes'$seconds\""
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Izin lokasi diberikan. Silakan coba lagi.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Izin lokasi ditolak. Aplikasi tidak dapat mengambil koordinat.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}