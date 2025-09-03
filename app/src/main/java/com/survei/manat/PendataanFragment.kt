package com.survei.manat

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.survei.manat.data.LocationData
import com.survei.manat.data.SurveySession
import com.survei.manat.data.UserProfile
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
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
    private lateinit var btnTakePhoto: Button
    private lateinit var ivPhotoPreview: ImageView

    // Variabel state
    private val allSampleNumbers = (1..10).map { it.toString() }
    private var fullyCompletedSamples = setOf<String>()
    private var isSpinnerSetupComplete = false
    private var currentPhotoUri: Uri? = null
    private var watermarkedPhotoFile: File? = null
    private var userProfile: UserProfile? = null

    // Launcher untuk permission location
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, lanjutkan capture timestamp dan location
            captureTimestampAfterPermissionGranted()
        } else {
            Toast.makeText(context, "Izin lokasi ditolak.", Toast.LENGTH_SHORT).show()
        }
    }

    // Variabel untuk menyimpan data sementara saat meminta permission
    private var pendingTimeKey: String? = null
    private var pendingLocationKey: String? = null
    private var pendingTextView: TextView? = null

    // Launcher untuk menangani hasil dari kamera
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                watermarkAndDisplayImage(uri)
            }
        }
    }

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
        setupButtonListeners()
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
        btnTakePhoto = view.findViewById(R.id.btn_take_photo)
        ivPhotoPreview = view.findViewById(R.id.iv_photo_preview)
    }

    private fun setupButtonListeners() {
        btnStartM.setOnClickListener { prepareCaptureTimestampAndLocation("startTimeM", "locationStartM", tvMStart) }
        btnEndM.setOnClickListener { prepareCaptureTimestampAndLocation("endTimeM", "locationEndM", tvMEnd) }
        btnStartKp.setOnClickListener { prepareCaptureTimestampAndLocation("startTimeKp", "locationStartKp", tvKpStart) }
        btnEndKp.setOnClickListener { prepareCaptureTimestampAndLocation("endTimeKp", "locationEndKp", tvKpEnd) }
        btnStartInti.setOnClickListener { prepareCaptureTimestampAndLocation("startTimeInti", "locationStartInti", tvIntiStart) }
        btnEndInti.setOnClickListener { prepareCaptureTimestampAndLocation("endTimeInti", "locationEndInti", tvIntiEnd) }
        btnSave.setOnClickListener { saveSurveyDataToFirebase() }

        btnTakePhoto.setOnClickListener {
            try {
                val photoFile = File.createTempFile("PHOTO_", ".jpg", requireContext().externalCacheDir)
                currentPhotoUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile)
                takePictureLauncher.launch(currentPhotoUri)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyiapkan kamera.", Toast.LENGTH_SHORT).show()
                Log.e("PendataanFragment", "Error creating temp file for camera", e)
            }
        }
    }

    private fun prepareCaptureTimestampAndLocation(timeKey: String, locationKey: String, targetTextView: TextView) {
        pendingTimeKey = timeKey
        pendingLocationKey = locationKey
        pendingTextView = targetTextView
        
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            captureTimestampAndLocation(timeKey, locationKey, targetTextView)
        }
    }

    private fun captureTimestampAfterPermissionGranted() {
        pendingTimeKey?.let { timeKey ->
            pendingLocationKey?.let { locationKey ->
                pendingTextView?.let { textView ->
                    captureTimestampAndLocation(timeKey, locationKey, textView)
                }
            }
        }
        
        // Reset pending values
        pendingTimeKey = null
        pendingLocationKey = null
        pendingTextView = null
    }

    @SuppressLint("MissingPermission")
    private fun captureTimestampAndLocation(timeKey: String, locationKey: String, targetTextView: TextView) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location == null) {
                Toast.makeText(context, "Gagal mendapatkan lokasi. Pastikan GPS aktif.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && location.isMock) {
                Toast.makeText(context, "Aplikasi Fake GPS terdeteksi! Nonaktifkan untuk melanjutkan.", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            val currentTime = System.currentTimeMillis()
            val locationData = LocationData(location.latitude, location.longitude)
            surveyDataMap[timeKey] = currentTime
            if (timeKey == "startTimeM") {
                surveyDataMap[locationKey] = locationData
            }

            val baseText = targetTextView.text.toString().substringBefore(':')
            targetTextView.text = formatTextView(baseText, currentTime)

            updateButtonVisibility(timeKey)
        }
    }

    private fun loadPetugasInfo() {
        val uid = auth.currentUser?.uid?: return
        database.getReference("users").child(uid).get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            userProfile = snapshot.getValue(UserProfile::class.java)
            userProfile?.let {
                val wilayahText = "Wilayah: ${it.kecamatan}, ${it.desa}, ${it.dusun} (NKS: ${it.nks})"
                tvWilayahInfo.text = wilayahText
            }
        }.addOnFailureListener {
            if (isAdded) {
                Toast.makeText(context, "Gagal memuat informasi petugas.", Toast.LENGTH_SHORT).show()
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
                isSpinnerSetupComplete = false
                loadFullyCompletedSamplesAndUpdateSampleSpinner()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun loadFullyCompletedSamplesAndUpdateSampleSpinner() {
        val uid = auth.currentUser?.uid?: return
        val surveysRef = database.getReference("surveys").child(uid)

        progressBar.visibility = View.VISIBLE
        surveysRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            
            val completed = mutableSetOf<String>()
            val dataCorruptionDetected = mutableSetOf<String>()
            
            for (sampleSnapshot in snapshot.children) {
                var sampleCompleted = false
                for (kunjunganSnapshot in sampleSnapshot.children) {
                    try {
                        // Check if the data is a Map (object) before converting
                        if (kunjunganSnapshot.value is Map<*, *>) {
                            val session = kunjunganSnapshot.getValue(SurveySession::class.java)
                            if (session?.endTimeInti != null) {
                                sampleCompleted = true
                                break
                            }
                        } else {
                            // Log data type mismatch but don't break the app
                            Log.w("PendataanFragment", "Data type mismatch for ${sampleSnapshot.key}/${kunjunganSnapshot.key}: ${kunjunganSnapshot.value?.javaClass?.simpleName}")
                            dataCorruptionDetected.add("${sampleSnapshot.key}/${kunjunganSnapshot.key}")
                        }
                    } catch (e: Exception) {
                        Log.e("PendataanFragment", "Error processing ${sampleSnapshot.key}/${kunjunganSnapshot.key}: ${e.message}")
                        dataCorruptionDetected.add("${sampleSnapshot.key}/${kunjunganSnapshot.key}")
                    }
                }
                if (sampleCompleted) {
                    completed.add(sampleSnapshot.key ?: "")
                }
            }
            
            fullyCompletedSamples = completed

            // Show warning if data corruption is detected
            if (dataCorruptionDetected.isNotEmpty() && isAdded) {
                Toast.makeText(context, "Peringatan: Data tidak valid ditemukan di ${dataCorruptionDetected.size} lokasi", Toast.LENGTH_LONG).show()
                Log.w("PendataanFragment", "Data corruption detected at: $dataCorruptionDetected")
            }

            val availableSamples = allSampleNumbers.filter {!fullyCompletedSamples.contains("SAMPEL_$it") }
            val sampleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableSamples)
            sampleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerSampleId.adapter = sampleAdapter

            if (availableSamples.isEmpty()) {
                Toast.makeText(context, "Semua sampel sudah selesai.", Toast.LENGTH_LONG).show()
                resetUIForNewSample()
            }

            setupSampleSpinnerListener()
            progressBar.visibility = View.GONE
        }.addOnFailureListener {
            if (!isAdded) return@addOnFailureListener
            progressBar.visibility = View.GONE
            Toast.makeText(context, "Gagal memuat data histori sampel.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSampleSpinnerListener() {
        spinnerSampleId.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (isSpinnerSetupComplete) {
                    loadDataForCurrentSelection()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {
                resetUIForNewSample()
            }
        }

        if (spinnerSampleId.adapter.count > 0) {
            loadDataForCurrentSelection()
        } else {
            resetUIForNewSample()
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
                    try {
                        // Check if the data is a Map (object) before converting
                        if (snapshot.value is Map<*, *>) {
                            val session = snapshot.getValue(SurveySession::class.java)
                            surveyDataMap.clear()
                            session?.let { restoreUiState(it) }
                        } else {
                            // Handle data type mismatch
                            Log.w("PendataanFragment", "Data type mismatch for $sampel/$kunjungan: ${snapshot.value?.javaClass?.simpleName}")
                            Toast.makeText(context, "Data tidak valid untuk sampel ini", Toast.LENGTH_SHORT).show()
                            resetUIForNewSample()
                        }
                    } catch (e: Exception) {
                        Log.e("PendataanFragment", "Error converting data for $sampel/$kunjungan: ${e.message}")
                        Toast.makeText(context, "Gagal memuat data sampel", Toast.LENGTH_SHORT).show()
                        resetUIForNewSample()
                    }
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

        // Restore location data
        session.locationStartM?.let { surveyDataMap["locationStartM"] = it }
        session.locationEndM?.let { surveyDataMap["locationEndM"] = it }
        session.locationStartKp?.let { surveyDataMap["locationStartKp"] = it }
        session.locationEndKp?.let { surveyDataMap["locationEndKp"] = it }
        session.locationStartInti?.let { surveyDataMap["locationStartInti"] = it }
        session.locationEndInti?.let { surveyDataMap["locationEndInti"] = it }

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

        if (surveyDataMap.isEmpty()) {
            Toast.makeText(context, "Tidak ada data waktu untuk disimpan.", Toast.LENGTH_SHORT).show()
            return
        }

        // Perbaikan: Condition 'surveyDataMap == null' is always 'false'
        if (kunjunganInt > 1 && surveyDataMap.isEmpty()) {
            Toast.makeText(context, "Untuk kunjungan lanjutan, semua kuesioner harus diselesaikan.", Toast.LENGTH_LONG).show()
            return
        }

        // Create a map with proper data types for Firebase
        val surveyData = mutableMapOf<String, Any?>()
        
        // Add timestamps
        surveyData["sampleId"] = sampel
        surveyData["startTimeM"] = surveyDataMap["startTimeM"] as? Long
        surveyData["endTimeM"] = surveyDataMap["endTimeM"] as? Long
        surveyData["startTimeKp"] = surveyDataMap["startTimeKp"] as? Long
        surveyData["endTimeKp"] = surveyDataMap["endTimeKp"] as? Long
        surveyData["startTimeInti"] = surveyDataMap["startTimeInti"] as? Long
        surveyData["endTimeInti"] = surveyDataMap["endTimeInti"] as? Long
        
        // Add location data
        (surveyDataMap["locationStartM"] as? LocationData)?.let {
            surveyData["locationStartM"] = mapOf("latitude" to it.latitude, "longitude" to it.longitude)
        }
        (surveyDataMap["locationEndM"] as? LocationData)?.let {
            surveyData["locationEndM"] = mapOf("latitude" to it.latitude, "longitude" to it.longitude)
        }
        (surveyDataMap["locationStartKp"] as? LocationData)?.let {
            surveyData["locationStartKp"] = mapOf("latitude" to it.latitude, "longitude" to it.longitude)
        }
        (surveyDataMap["locationEndKp"] as? LocationData)?.let {
            surveyData["locationEndKp"] = mapOf("latitude" to it.latitude, "longitude" to it.longitude)
        }
        (surveyDataMap["locationStartInti"] as? LocationData)?.let {
            surveyData["locationStartInti"] = mapOf("latitude" to it.latitude, "longitude" to it.longitude)
        }
        (surveyDataMap["locationEndInti"] as? LocationData)?.let {
            surveyData["locationEndInti"] = mapOf("latitude" to it.latitude, "longitude" to it.longitude)
        }

        progressBar.visibility = View.VISIBLE
        database.getReference("surveys").child(uid).child(sampel).child(kunjungan)
            .setValue(surveyData)
            .addOnSuccessListener {
                // Data waktu berhasil disimpan, sekarang tangani unggahan foto
                handlePhotoUpload()
            }.addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Gagal menyimpan data waktu: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handlePhotoUpload() {
        // Cek apakah ada foto yang akan diunggah
        if (watermarkedPhotoFile == null || !watermarkedPhotoFile!!.exists()) {
            progressBar.visibility = View.GONE
            Toast.makeText(context, "Sukses! Data waktu disimpan (tanpa foto).", Toast.LENGTH_LONG).show()
            loadFullyCompletedSamplesAndUpdateSampleSpinner() // Muat ulang data sampel
            resetUIForNewSample() // Reset form
            return
        }

        // Buat nama file yang unik dan deskriptif
        val kunjungan = spinnerKunjungan.selectedItemPosition + 1
        val sampel = spinnerSampleId.selectedItem.toString()
        val nks = userProfile?.nks ?: "00000"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "FOTO_${nks}_K${kunjungan}_S${sampel}_$timestamp.jpg"

        Toast.makeText(context, "Data waktu disimpan. Mengunggah foto...", Toast.LENGTH_SHORT).show()

        // Gunakan lifecycleScope untuk memanggil suspend function dari Fragment
        viewLifecycleOwner.lifecycleScope.launch {
            val result = DriveUploader.uploadPhoto(requireContext(), watermarkedPhotoFile!!, fileName)

            // Pastikan fragment masih terpasang sebelum memperbarui UI
            if (!isAdded) return@launch

            progressBar.visibility = View.GONE
            when (result) {
                is UploadResult.Success -> {
                    // Foto berhasil diunggah
                    Toast.makeText(requireContext(), "Sukses! Data dan Foto berhasil disimpan!", Toast.LENGTH_LONG).show()
                    Log.d("PendataanFragment", "File ID di Google Drive: ${result.fileId}")

                    // (Opsional) Simpan ID file Drive ke Firebase
                    val uid = auth.currentUser?.uid
                    val kunjunganPath = "kunjungan_$kunjungan"
                    val sampelPath = "SAMPEL_$sampel"
                    if (uid != null) {
                        database.getReference("surveys").child(uid).child(sampelPath).child(kunjunganPath)
                            .child("photoDriveId").setValue(result.fileId)
                    }

                    // Hapus file temporer setelah berhasil diunggah
                    watermarkedPhotoFile?.delete()
                    loadFullyCompletedSamplesAndUpdateSampleSpinner()
                    resetUIForNewSample()
                }
                is UploadResult.Error -> {
                    // Foto GAGAL diunggah, tampilkan pesan galat yang detail
                    Toast.makeText(requireContext(), "Data waktu disimpan, tapi foto GAGAL diunggah: ${result.message}", Toast.LENGTH_LONG).show()
                    // Periksa Logcat dengan filter "DriveUploader" untuk detail teknis
                    loadFullyCompletedSamplesAndUpdateSampleSpinner()
                    resetUIForNewSample()
                }
            }
        }
    }

    private fun watermarkAndDisplayImage(sourceUri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(sourceUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint()

            paint.color = Color.WHITE
            paint.textSize = 36f
            paint.isAntiAlias = true
            paint.setShadowLayer(5f, 5f, 5f, Color.BLACK)

            val desa = userProfile?.desa ?: ""
            val watermarkText1 = "Pendataan Susenas September 2025 Desa $desa Kabupaten Samosir"
            val timeStamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            
            // Perbaikan: Unnecessary safe call on a non-null receiver
            val locationDMS = surveyDataMap.let {
                val loc = it["locationStartM"] as? LocationData
                if (loc?.latitude != null && loc.longitude != null) {
                    "${toDMS(loc.latitude)}, ${toDMS(loc.longitude)}"
                } else {
                    "Lokasi tidak tersedia"
                }
            }

            canvas.drawText(watermarkText1, 50f, mutableBitmap.height - 150f, paint)
            canvas.drawText(locationDMS, 50f, mutableBitmap.height - 100f, paint)
            canvas.drawText(timeStamp, 50f, mutableBitmap.height - 50f, paint)

            ivPhotoPreview.setImageBitmap(mutableBitmap)
            ivPhotoPreview.visibility = View.VISIBLE

            // Simpan bitmap ber-watermark ke file temporer
            watermarkedPhotoFile = File.createTempFile("WATERMARKED_", ".jpg", requireContext().cacheDir)
            val fos = FileOutputStream(watermarkedPhotoFile)
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            fos.flush()
            fos.close()

        } catch (e: Exception) {
            Log.e("PendataanFragment", "Error watermarking image", e)
            Toast.makeText(context, "Gagal memproses gambar.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateButtonVisibility(lastKey: String) {
        val allButtons = listOf(btnStartM, btnEndM, btnStartKp, btnEndKp, btnStartInti, btnEndInti, btnSave, btnTakePhoto)
        allButtons.forEach { it.visibility = View.GONE }
        ivPhotoPreview.visibility = View.GONE

        when (lastKey) {
            "startTimeM" -> btnEndM.visibility = View.VISIBLE
            "endTimeM" -> btnStartKp.visibility = View.VISIBLE
            "startTimeKp" -> btnEndKp.visibility = View.VISIBLE
            "endTimeKp" -> btnStartInti.visibility = View.VISIBLE
            "startTimeInti" -> btnEndInti.visibility = View.VISIBLE
            "endTimeInti" -> {
                btnTakePhoto.visibility = View.VISIBLE
                btnSave.visibility = View.VISIBLE
            }
            else -> btnStartM.visibility = View.VISIBLE
        }

        if (lastKey.isNotEmpty()) {
            btnSave.visibility = View.VISIBLE
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
        ivPhotoPreview.visibility = View.GONE
        ivPhotoPreview.setImageDrawable(null)
        currentPhotoUri = null
        watermarkedPhotoFile?.delete() // Hapus file temporer saat reset
        watermarkedPhotoFile = null
        updateButtonVisibility("")
        spinnerSampleId.isEnabled = true
        spinnerKunjungan.isEnabled = true
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
}