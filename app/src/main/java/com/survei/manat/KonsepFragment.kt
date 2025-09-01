package com.survei.manat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SearchView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment

class KonsepFragment : Fragment() {

    private lateinit var spinnerRincian: Spinner
    private lateinit var tvMateriContent: TextView
    private lateinit var searchView: SearchView

    // Ini adalah data contoh. Nantinya, data ini bisa diambil dari database.
    private val daftarTopik = mapOf(
        "Pilih Topik..." to "Silakan pilih rincian pertanyaan dari daftar di atas.",
        "R101. Nama Kepala Keluarga" to "Kepala keluarga adalah salah satu anggota keluarga yang bertanggung jawab atas pemenuhan kebutuhan keluarga.",
        "R105. Status Tempat Tinggal" to "Status kepemilikan tempat tinggal adalah status penguasaan bangunan tempat tinggal yang ditempati pada saat pencacahan.",
        "R201. Pendidikan Tertinggi" to "Pendidikan tertinggi yang ditamatkan adalah jenjang pendidikan formal terakhir yang diikuti dan berhasil lulus/mendapatkan ijazah."
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_konsep, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerRincian = view.findViewById(R.id.spinner_rincian)
        tvMateriContent = view.findViewById(R.id.tv_materi_content)
        searchView = view.findViewById(R.id.search_view)
        
        setupSpinner()
        setupSearchView()
    }

    private fun setupSpinner() {
        val topikTitles = daftarTopik.keys.toList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, topikTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRincian.adapter = adapter

        spinnerRincian.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedTitle = topikTitles[position]
                tvMateriContent.text = daftarTopik[selectedTitle]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    performSearch(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })
    }

    private fun performSearch(query: String) {
        val searchResults = daftarTopik.filter { (judul, isi) ->
            judul.contains(query, ignoreCase = true) || isi.contains(query, ignoreCase = true)
        }

        if (searchResults.isNotEmpty()) {
            val resultText = StringBuilder()
            searchResults.forEach { (judul, isi) ->
                resultText.append("--- $judul ---\n")
                resultText.append("$isi\n\n")
            }
            tvMateriContent.text = resultText.toString()
        } else {
            tvMateriContent.text = "Materi untuk '$query' tidak ditemukan."
        }
    }
}