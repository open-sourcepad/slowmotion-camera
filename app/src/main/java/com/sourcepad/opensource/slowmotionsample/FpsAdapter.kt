package com.sourcepad.opensource.slowmotionsample

import android.util.Range
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FpsAdapter : RecyclerView.Adapter<FpsAdapter.FpsVH>() {

    var items: List<Range<Int>>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var onClicked: ((Range<Int>) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FpsVH {
        return FpsVH(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_fps,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return items?.size ?: 0
    }

    override fun onBindViewHolder(holder: FpsVH, position: Int) {

        val item = items?.get(position)

        item?.run {
            (holder.itemView as TextView).text = "${this.lower} FPS"
        }

        holder.itemView.setOnClickListener {
            if (item != null) {
                onClicked?.invoke(item)
            }
        }
    }


    class FpsVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }
}