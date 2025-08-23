package com.nextjsclient.android.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.nextjsclient.android.R
import java.text.SimpleDateFormat
import java.util.*

data class WeekItem(
    val year: Int,
    val week: Int,
    val isCurrentWeek: Boolean = false,
    val isSelected: Boolean = false,
    val hasData: Boolean = false,
    val isLoading: Boolean = false
) {
    fun getDateRange(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.WEEK_OF_YEAR, week)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        val startDate = calendar.time
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDate = calendar.time
        
        val dateFormat = SimpleDateFormat("d MMM", Locale.FRANCE)
        val startStr = dateFormat.format(startDate)
        val endStr = dateFormat.format(endDate)
        
        return "$startStr - $endStr"
    }
}

class WeekGridAdapter(
    private val onWeekSelected: (WeekItem) -> Unit
) : ListAdapter<WeekItem, WeekGridAdapter.WeekViewHolder>(WeekDiffCallback()) {

    private var selectedWeek: WeekItem? = null

    class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val weekNumber: TextView = itemView.findViewById(R.id.weekNumber)
        val weekRange: TextView = itemView.findViewById(R.id.weekRange)
        val weekBadge: MaterialCardView = itemView.findViewById(R.id.weekBadge)
        val currentWeekIndicator: View = itemView.findViewById(R.id.currentWeekIndicator)
        val dataAvailableIndicator: android.widget.ImageView = itemView.findViewById(R.id.dataAvailableIndicator)
        val loadingIndicator: android.widget.ProgressBar = itemView.findViewById(R.id.loadingIndicator)
        val unavailableMessage: TextView = itemView.findViewById(R.id.unavailableMessage)
        val cardView: MaterialCardView = itemView as MaterialCardView
        
        // Tracker l'état "retourné" localement 
        var isFlipped: Boolean = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_week_grid, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val weekItem = getItem(position)
        
        holder.weekNumber.text = weekItem.week.toString()
        holder.weekRange.text = weekItem.getDateRange()
        
        // Indicateurs visuels et gestion de l'affichage "pas disponible"
        if (holder.isFlipped) {
            // État "pas disponible" : masquer TOUS les éléments normaux y compris le loader
            holder.weekBadge.isVisible = false
            holder.weekRange.isVisible = false
            holder.dataAvailableIndicator.isVisible = false
            holder.currentWeekIndicator.isVisible = false
            holder.loadingIndicator.isVisible = false  // ✅ Masquer le loader sur carte retournée
            holder.unavailableMessage.isVisible = true
        } else {
            // État normal : afficher les éléments normaux
            holder.weekBadge.isVisible = true
            holder.weekRange.isVisible = true
            holder.currentWeekIndicator.isVisible = weekItem.isCurrentWeek
            holder.dataAvailableIndicator.isVisible = weekItem.hasData && !weekItem.isLoading
            holder.loadingIndicator.isVisible = weekItem.isLoading  // Loader normal seulement si pas retournée
            holder.unavailableMessage.isVisible = false
        }
        
        // Style selon l'état
        val context = holder.itemView.context
        when {
            weekItem.isSelected -> {
                // Semaine sélectionnée
                holder.cardView.setCardBackgroundColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorPrimaryContainer)
                )
                holder.cardView.strokeColor = context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary)
                holder.cardView.strokeWidth = 2.dpToPx(context)
                holder.weekBadge.setCardBackgroundColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary)
                )
                holder.weekNumber.setTextColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary)
                )
                holder.weekRange.setTextColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)
                )
            }
            weekItem.isCurrentWeek -> {
                // Semaine courante mais non sélectionnée
                holder.cardView.setCardBackgroundColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer)
                )
                holder.cardView.strokeColor = context.getColorFromAttr(com.google.android.material.R.attr.colorSecondary)
                holder.cardView.strokeWidth = 1.dpToPx(context)
                holder.weekBadge.setCardBackgroundColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorSecondary)
                )
                holder.weekNumber.setTextColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnSecondary)
                )
                holder.weekRange.setTextColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
            }
            !weekItem.hasData -> {
                // Semaine sans données - CLIQUABLE pour vérification rapide
                holder.cardView.setCardBackgroundColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorSurface)
                )
                holder.cardView.strokeColor = context.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
                holder.cardView.strokeWidth = 1.dpToPx(context)
                holder.cardView.alpha = 0.7f // Moins grisé car cliquable
                holder.cardView.isClickable = true // Cliquable pour vérification
                holder.cardView.isFocusable = true
                holder.weekBadge.setCardBackgroundColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant)
                )
                holder.weekBadge.alpha = 0.8f
                holder.weekNumber.setTextColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                holder.weekNumber.alpha = 0.8f
                holder.weekRange.setTextColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                holder.weekRange.alpha = 0.8f
            }
            else -> {
                // Semaine normale - disponible et cliquable
                holder.cardView.setCardBackgroundColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorSurface)
                )
                holder.cardView.strokeColor = context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
                holder.cardView.strokeWidth = 1.dpToPx(context)
                holder.cardView.alpha = 1f
                holder.cardView.isClickable = true // Cliquable
                holder.cardView.isFocusable = true
                holder.weekBadge.setCardBackgroundColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer)
                )
                holder.weekBadge.alpha = 1f
                holder.weekNumber.setTextColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
                holder.weekNumber.alpha = 1f
                holder.weekRange.setTextColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                holder.weekRange.alpha = 1f
            }
        }
        
        holder.itemView.setOnClickListener {
            android.util.Log.d("WeekGridAdapter", "🖱️ Clic sur semaine ${weekItem.week}/${weekItem.year}, hasData: ${weekItem.hasData}")
            
            if (weekItem.hasData) {
                // Semaine disponible : sélection normale
                onWeekSelected(weekItem)
            } else {
                // Semaine non disponible : animation de retournement
                if (!holder.isFlipped) {
                    animateCardFlip(holder, weekItem, position)
                }
            }
        }
    }

    fun updateSelection(selectedWeek: WeekItem) {
        this.selectedWeek = selectedWeek
        notifyDataSetChanged()
    }

    private fun android.content.Context.getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun Int.dpToPx(context: android.content.Context): Int = 
        (this * context.resources.displayMetrics.density).toInt()
    
    private fun animateCardFlip(holder: WeekViewHolder, weekItem: WeekItem, position: Int) {
        // Empêcher les clics multiples pendant l'animation
        holder.itemView.isEnabled = false
        
        // Animation de rotation 3D
        holder.cardView.animate()
            .rotationY(90f)
            .setDuration(150)
            .withEndAction {
                // Masquer les éléments normaux y compris le loader
                holder.weekBadge.isVisible = false
                holder.weekRange.isVisible = false
                holder.dataAvailableIndicator.isVisible = false
                holder.currentWeekIndicator.isVisible = false
                holder.loadingIndicator.isVisible = false  // ✅ Arrêter le loader pendant l'animation
                
                // Afficher le message "Pas disponible"
                holder.unavailableMessage.isVisible = true
                
                // Tourner dans l'autre sens pour révéler la face arrière
                holder.cardView.rotationY = -90f
                holder.cardView.animate()
                    .rotationY(0f)
                    .setDuration(150)
                    .withEndAction {
                        // Réactiver les clics après l'animation et marquer comme retourné
                        holder.itemView.isEnabled = true
                        holder.isFlipped = true
                    }
                    .start()
                
                // NE PAS mettre à jour la liste pour éviter le refresh pendant l'animation
                // L'état sera géré visuellement uniquement
            }
            .start()
    }
}

class WeekDiffCallback : DiffUtil.ItemCallback<WeekItem>() {
    override fun areItemsTheSame(oldItem: WeekItem, newItem: WeekItem): Boolean {
        return oldItem.year == newItem.year && oldItem.week == newItem.week
    }

    override fun areContentsTheSame(oldItem: WeekItem, newItem: WeekItem): Boolean {
        return oldItem == newItem
    }
}