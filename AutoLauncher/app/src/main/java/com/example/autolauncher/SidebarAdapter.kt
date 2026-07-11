package com.example.autolauncher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class SidebarAdapter(
    private val items: List<SidebarItem>,
    private val onClick: (SidebarItem) -> Unit
) : RecyclerView.Adapter<SidebarAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val glyph: ImageView = view.findViewById(R.id.sidebarIconGlyph)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sidebar_icon, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.glyph.setImageResource(item.iconRes)
        holder.glyph.setColorFilter(Color.parseColor("#1FA7D8"))
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
