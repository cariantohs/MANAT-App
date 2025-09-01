package com.survei.manat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.survei.manat.data.SurveySession
import java.text.SimpleDateFormat
import java.util.*

class RekapitulasiFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var dataListener: ValueEventListener? = null

    private lateinit var rekapContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var scrollView: ScrollView
    private val sampleViews = mutableMapOf<String, View>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_rekapitulasi, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        rekapContainer = view.findViewById(R.id.rekap_container)
        progressBar = view.findViewById(R.id.progress_bar)
        tvStatus = view.findViewById(R.id.tv_status)
        scrollView = view.findViewById(R.id.scroll_view_rekap)

        val currentUser = auth.currentUser
        Log.d("RekapitulasiFragment", "Fragment dibuat. Mencoba memuat data untuk UID: ${currentUser?.uid}")

        if (currentUser == null) {
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("surveys").child(currentUser.uid)

        scrollView.visibility = View.GONE
        tvStatus.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        setupInitialLayout()
        fetchDataOnce()
    }

    private fun fetchDataOnce() {
        database.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            updateUI(snapshot)
            attachRealtimeListener()
        }.addOnFailureListener { error ->
            if (!isAdded) return@addOnFailureListener
            progressBar.visibility = View.GONE
            scrollView.visibility = View.GONE
            tvStatus.text = "Gagal memuat data. Periksa koneksi internet Anda."
            tvStatus.visibility = View.VISIBLE
        }
    }
    
    private fun attachRealtimeListener() {
        if (dataListener != null) {
            database.removeEventListener(dataListener!!)
        }
        dataListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                updateUI(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("RekapitulasiFragment", "Listener realtime dibatalkan.", error.toException())
            }
        }
        database.addValueEventListener(dataListener!!)
    }
    
    private fun updateUI(snapshot: DataSnapshot) {
        progressBar.visibility = View.GONE

        if (!snapshot.hasChildren()) {
            tvStatus.text = "Belum ada data yang tersimpan."
            tvStatus.visibility = View.VISIBLE
            scrollView.visibility = View.GONE
            return
        }

        tvStatus.visibility = View.GONE
        scrollView.visibility = View.VISIBLE

        for (i in 1..10) {
            val sampleId = "SAMPEL_$i"
            val sampleView = sampleViews[sampleId] ?: continue
            
            val tvMStart = sampleView.findViewById<TextView>(R.id.tv_m_start)
            val tvMEnd = sampleView.findViewById<TextView>(R.id.tv_m_end)
            val tvKpStart = sampleView.findViewById<TextView>(R.id.tv_kp_start)
            val tvKpEnd = sampleView.findViewById<TextView>(R.id.tv_kp_end)
            val tvIntiStart = sampleView.findViewById<TextView>(R.id.tv_inti_start)
            val tvIntiEnd = sampleView.findViewById<TextView>(R.id.tv_inti_end)
            val tvKoordinat = sampleView.findViewById<TextView>(R.id.tv_koordinat)

            if (snapshot.hasChild(sampleId)) {
                // Ambil data dari kunjungan terakhir
                val lastKunjunganKey = snapshot.child(sampleId).children.lastOrNull()?.key
                if (lastKunjunganKey != null) {
                    val session = snapshot.child(sampleId).child(lastKunjunganKey).getValue(SurveySession::class.java)
                    if (session != null) {
                        val mStart = formatTimestamp(session.startTimeM)
                        val mEnd = formatTimestamp(session.endTimeM)
                        val kpStart = formatTimestamp(session.startTimeKp)
                        val kpEnd = formatTimestamp(session.endTimeKp)
                        val intiStart = formatTimestamp(session.startTimeInti)
                        val intiEnd = formatTimestamp(session.endTimeInti)
                        val koordinat = formatKoordinat(session.locationStartM?.latitude, session.locationStartM?.longitude)

                        tvMStart.text = "Mulai VSEN25.M: $mStart"
                        tvMEnd.text = "Selesai VSEN25.M: $mEnd"
                        tvKpStart.text = "Mulai VSEN25.KP: $kpStart"
                        tvKpEnd.text = "Selesai VSEN25.KP: $kpEnd"
                        tvIntiStart.text = "Mulai VSERUTI25.INTI: $intiStart"
                        tvIntiEnd.text = "Selesai VSERUTI25.INTI: $intiEnd"
                        tvKoordinat.text = "Koordinat: $koordinat"
                        
                        val rekapLengkap = "Sampel: $i\nMulai M: $mStart\nSelesai M: $mEnd\nMulai KP: $kpStart\nSelesai KP: $kpEnd\nMulai Inti: $intiStart\nSelesai Inti: $intiEnd\nKoordinat: $koordinat"
                        setupCopyListener(sampleView, rekapLengkap)
                    }
                }
            } else {
                tvMStart.text = "Mulai VSEN25.M: -"
                tvMEnd.text = "Selesai VSEN25.M: -"
                tvKpStart.text = "Mulai VSEN25.KP: -"
                tvKpEnd.text = "Selesai VSEN25.KP: -"
                tvIntiStart.text = "Mulai VSERUTI25.INTI: -"
                tvIntiEnd.text = "Selesai VSERUTI25.INTI: -"
                tvKoordinat.text = "Koordinat: -"
            }
        }
    }

    private fun setupInitialLayout() {
        val inflater = LayoutInflater.from(context)
        rekapContainer.removeAllViews()
        sampleViews.clear()
        
        val titleView = inflater.inflate(R.layout.item_rekapitulasi_title, rekapContainer, false)
        rekapContainer.addView(titleView)

        for (i in 1..10) {
            val sampleView = inflater.inflate(R.layout.item_rekapitulasi, rekapContainer, false)
            val sampleTitle = sampleView.findViewById<TextView>(R.id.tv_sample_title)
            sampleTitle.text = "No Urut Sampel: $i"
            val sampleId = "SAMPEL_$i"
            sampleViews[sampleId] = sampleView
            rekapContainer.addView(sampleView)
        }
    }
    
    private fun setupCopyListener(view: View, fullText: String) {
        view.setOnLongClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Data Rekapitulasi Sampel", fullText)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Data sampel disalin ke clipboard!", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null || timestamp == 0L) return "-"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale("id", "ID"))
        return sdf.format(Date(timestamp))
    }
    
    private fun formatKoordinat(lat: Double?, lon: Double?): String {
        if (lat == null || lon == null) return "-"
        return "${toDMS(lat)}, ${toDMS(lon)}"
    }
    
    private fun toDMS(coordinate: Double): String {
        val absolute = Math.abs(coordinate)
        val degrees = Math.floor(absolute).toInt()
        val minutesNotTruncated = (absolute - degrees) * 60
        val minutes = Math.floor(minutesNotTruncated).toInt()
        val seconds = Math.floor((minutesNotTruncated - minutes) * 60)
        return "$degrees°$minutes'$seconds\""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (dataListener != null) {
            database.removeEventListener(dataListener!!)
        }
    }
}