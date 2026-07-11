package com.example.autolauncher

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppGridAdapter(
    private val items: List<AppItem>,
    private val onClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppGridAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val iconBg: View = view.findViewById(R.id.iconBg)
        val iconGlyph: ImageView = view.findViewById(R.id.iconGlyph)
        val label: TextView = view.findViewById(R.id.appLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_icon, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.label.text = item.name
        holder.iconGlyph.setImageResource(item.iconRes)

        val bgColor = Color.parseColor(item.colorHex)
        holder.iconBg.background.setColorFilter(bgColor, PorterDuff.Mode.SRC_IN)

        if (item.onWhiteBg) {
            holder.iconGlyph.setColorFilter(Color.BLACK)
        } else {
            holder.iconGlyph.setColorFilter(Color.WHITE)
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
