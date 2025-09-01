package com.survei.manat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.survei.manat.adapter.RekapAdapter
import com.survei.manat.data.LocationData
import com.survei.manat.data.MapPoint
import com.survei.manat.data.RekapItem
import com.survei.manat.data.SurveySession
import com.survei.manat.data.UserProfile

class DashboardFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var spinnerPetugas: Spinner
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnShowMap: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var rekapAdapter: RekapAdapter
    
    private val petugasList = mutableListOf<Pair<String, String>>() // Pair of (UID, Nama)
    private val rekapList = mutableListOf<RekapItem>()
    private val mapPoints = mutableListOf<MapPoint>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        spinnerPetugas = view.findViewById(R.id.spinner_petugas)
        recyclerView = view.findViewById(R.id.recycler_view_rekap)
        btnShowMap = view.findViewById(R.id.btn_show_map)
        progressBar = view.findViewById(R.id.progress_bar_dashboard)
        tvStatus = view.findViewById(R.id.tv_status_dashboard)

        recyclerView.layoutManager = LinearLayoutManager(context)
        rekapAdapter = RekapAdapter(rekapList)
        recyclerView.adapter = rekapAdapter

        loadUsersBasedOnRole()

        btnShowMap.setOnClickListener {
            if (mapPoints.isNotEmpty()) {
                val intent = Intent(activity, MapActivity::class.java).apply {
                    putParcelableArrayListExtra("map_points", ArrayList(mapPoints))
                }
                startActivity(intent)
            } else {
                Toast.makeText(context, "Tidak ada data koordinat untuk ditampilkan.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUsersBasedOnRole() {
        progressBar.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Memuat daftar petugas..."
        
        val currentUser = auth.currentUser ?: return
        val usersRef = database.getReference("users")

        usersRef.child(currentUser.uid).get().addOnSuccessListener { userSnapshot ->
            val userProfile = userSnapshot.getValue(UserProfile::class.java)
            when (userProfile?.role) {
                "admin" -> {
                    usersRef.orderByChild("role").equalTo("petugas").get().addOnSuccessListener { allPetugasSnapshot ->
                        processPetugasSnapshot(allPetugasSnapshot)
                    }
                }
                "pemeriksa" -> {
                    val bawahanUids = userProfile.bawahan?.keys ?: emptySet()
                    if (bawahanUids.isEmpty()) {
                        progressBar.visibility = View.GONE
                        tvStatus.text = "Tidak ada petugas yang diawasi."
                        return@addOnSuccessListener
                    }
                    // Ambil data untuk setiap bawahan
                    usersRef.get().addOnSuccessListener { allUsersSnapshot ->
                        val filteredPetugas = allUsersSnapshot.children.filter { it.key in bawahanUids }
                        processPetugasList(filteredPetugas)
                    }
                }
                else -> {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Peran tidak dikenali."
                }
            }
        }
    }
    
    private fun processPetugasSnapshot(snapshot: DataSnapshot) {
        processPetugasList(snapshot.children.toList())
    }

    private fun processPetugasList(petugasSnapshots: List<DataSnapshot>) {
        petugasList.clear()
        petugasSnapshots.forEach { snapshot ->
            val uid = snapshot.key
            val nama = snapshot.child("nama").getValue(String::class.java)
            if (uid != null && nama != null) {
                petugasList.add(Pair(uid, nama))
            }
        }

        if (petugasList.isEmpty()) {
            progressBar.visibility = View.GONE
            tvStatus.text = "Tidak ada petugas yang ditemukan."
            return
        }

        val petugasNames = petugasList.map { it.second }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, petugasNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPetugas.adapter = adapter

        spinnerPetugas.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPetugasUid = petugasList[position].first
                loadSurveyDataForPetugas(selectedPetugasUid)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        progressBar.visibility = View.GONE
        tvStatus.visibility = View.GONE
    }
    
    private fun loadSurveyDataForPetugas(petugasUid: String) {
        progressBar.visibility = View.VISIBLE
        rekapList.clear()
        mapPoints.clear()
        rekapAdapter.notifyDataSetChanged()

        val surveyRef = database.getReference("surveys").child(petugasUid)
        surveyRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Petugas ini belum memiliki data.", Toast.LENGTH_SHORT).show()
                btnShowMap.visibility = View.GONE
                return@addOnSuccessListener
            }

            snapshot.children.forEach { sampleSnapshot ->
                val sampleId = sampleSnapshot.key ?: "Unknown Sample"
                var latestKunjungan: DataSnapshot? = null
                // Cari kunjungan terakhir
                for (i in 5 downTo 1) {
                    if (sampleSnapshot.hasChild("kunjungan_$i")) {
                        latestKunjungan = sampleSnapshot.child("kunjungan_$i")
                        break
                    }
                }

                if (latestKunjungan != null) {
                    val session = latestKunjungan.getValue(SurveySession::class.java)
                    if (session?.endTimeInti != null && session.startTimeM != null) {
                        // Data Selesai
                        val duration = (session.endTimeInti - session.startTimeM) / (1000 * 60)
                        rekapList.add(RekapItem(sampleId, "Selesai", duration, session.locationStartM))
                        session.locationStartM?.let { loc ->
                            if (loc.latitude != null && loc.longitude != null) {
                                mapPoints.add(MapPoint(loc.latitude, loc.longitude, sampleId))
                            }
                        }
                    } else {
                        // Data Belum Selesai
                        rekapList.add(RekapItem(sampleId, "Dalam Pengerjaan", null, session?.locationStartM))
                        session?.locationStartM?.let { loc ->
                             if (loc.latitude != null && loc.longitude != null) {
                                mapPoints.add(MapPoint(loc.latitude, loc.longitude, sampleId))
                            }
                        }
                    }
                }
            }
            rekapAdapter.notifyDataSetChanged()
            progressBar.visibility = View.GONE
            btnShowMap.visibility = if (mapPoints.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }
}