package com.survei.manat.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.survei.manat.R
import com.survei.manat.data.RekapItem

class RekapAdapter(private val rekapList: List<RekapItem>) : RecyclerView.Adapter<RekapAdapter.RekapViewHolder>() {

    class RekapViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sampleId: TextView = itemView.findViewById(R.id.tv_item_sample_id)
        val status: TextView = itemView.findViewById(R.id.tv_item_status)
        val duration: TextView = itemView.findViewById(R.id.tv_item_duration)
        val warningIcon: ImageView = itemView.findViewById(R.id.iv_warning)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RekapViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dashboard_rekap, parent, false)
        return RekapViewHolder(view)
    }

    override fun onBindViewHolder(holder: RekapViewHolder, position: Int) {
        val item = rekapList[position]
        holder.sampleId.text = "Sampel ID: ${item.sampleId}"
        holder.status.text = "Status: ${item.status}"

        if (item.durationInMinutes != null) {
            holder.duration.text = "Durasi: ${item.durationInMinutes} Menit"
            if (item.durationInMinutes < 90) {
                holder.warningIcon.visibility = View.VISIBLE
                holder.duration.setTextColor(Color.RED)
            } else {
                holder.warningIcon.visibility = View.GONE
                holder.duration.setTextColor(Color.BLACK)
            }
        } else {
            holder.duration.text = "Durasi: -"
            holder.warningIcon.visibility = View.GONE
            holder.duration.setTextColor(Color.BLACK)
        }
    }

    override fun getItemCount() = rekapList.size
}