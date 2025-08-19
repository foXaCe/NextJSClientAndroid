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
        private val weekNumberText: TextView = itemView.findViewById(R.id.weekNumberText)
        private val weekDatesText: TextView = itemView.findViewById(R.id.weekDatesText)
        private val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)

        fun bind(weekInfo: WeekInfo, isSelected: Boolean) {
            val weekStr = weekInfo.week.toString().padStart(2, '0')
            weekNumberText.text = "Semaine $weekStr"
            
            // Format des dates de la semaine
            weekDatesText.text = formatWeekDates(weekInfo.year, weekInfo.week)
            
            // Indicateur de sélection
            selectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // Click listener
            itemView.setOnClickListener {
                onWeekSelected(weekInfo)
            }
        }
        
        private fun formatWeekDates(year: Int, week: Int): String {
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.YEAR, year)
            calendar.set(java.util.Calendar.WEEK_OF_YEAR, week)
            calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            
            val startDate = calendar.time
            calendar.add(java.util.Calendar.DAY_OF_WEEK, 6)
            val endDate = calendar.time
            
            val dateFormat = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
            return "${dateFormat.format(startDate)} au ${dateFormat.format(endDate)}/$year"
        }
    }
}