package com.nextjsclient.android.ui.scamark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nextjsclient.android.R
import com.nextjsclient.android.data.models.WeekInfo

class WeekSelectorAdapter(
    private var weeks: List<WeekInfo>,
    private val currentWeek: Int,
    private val currentYear: Int,
    private val onWeekSelected: (WeekInfo) -> Unit
) : RecyclerView.Adapter<WeekSelectorAdapter.WeekViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_week_selector, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val week = weeks[position]
        holder.bind(week, week.week == currentWeek && week.year == currentYear)
    }

    override fun getItemCount(): Int = weeks.size
    
    /**
     * Met à jour la liste des semaines
     */
    fun updateWeeks(newWeeks: List<WeekInfo>) {
        weeks = newWeeks
        notifyDataSetChanged()
    }

    inner class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val weekTitle: TextView = itemView.findViewById(R.id.weekTitle)
        private val weekYear: TextView = itemView.findViewById(R.id.weekYear)
        private val selectedIndicator: View = itemView.findViewById(R.id.selectedIndicator)

        fun bind(weekInfo: WeekInfo, isSelected: Boolean) {
            val weekStr = weekInfo.week.toString().padStart(2, '0')
            weekTitle.text = "Semaine $weekStr"
            
            // Afficher l'année
            weekYear.text = weekInfo.year.toString()
            
            // Indicateur de sélection
            selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // Click listener
            itemView.setOnClickListener {
                onWeekSelected(weekInfo)
            }
        }
    }
}