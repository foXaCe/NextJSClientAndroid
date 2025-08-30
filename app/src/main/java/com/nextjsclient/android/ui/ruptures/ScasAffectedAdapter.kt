package com.nextjsclient.android.ui.ruptures

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextjsclient.android.data.models.ScaAffected
import com.nextjsclient.android.databinding.ItemScaAffectedBinding

class ScasAffectedAdapter : ListAdapter<ScaAffected, ScasAffectedAdapter.ViewHolder>(ScaDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScaAffectedBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(private val binding: ItemScaAffectedBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(sca: ScaAffected) {
            binding.clientNameText.text = sca.clientName
            binding.quantityCommandedText.text = sca.quantityCommanded.toString()
            binding.quantityAvailableText.text = sca.quantityAvailable.toString()
            binding.quantityMissingText.text = sca.quantityMissing.toString()
            
            // Calcul du pourcentage de satisfaction
            val satisfactionPercent = if (sca.quantityCommanded > 0) {
                (sca.quantityAvailable * 100) / sca.quantityCommanded
            } else {
                100
            }
            
            binding.satisfactionPercentText.text = "${satisfactionPercent}%"
            
            // Couleur selon le niveau de satisfaction
            val color = when {
                satisfactionPercent >= 90 -> android.R.color.holo_green_dark
                satisfactionPercent >= 70 -> android.R.color.holo_orange_dark
                else -> android.R.color.holo_red_dark
            }
            
            binding.satisfactionPercentText.setTextColor(
                androidx.core.content.ContextCompat.getColor(itemView.context, color)
            )
        }
    }
}

class ScaDiffCallback : DiffUtil.ItemCallback<ScaAffected>() {
    override fun areItemsTheSame(oldItem: ScaAffected, newItem: ScaAffected): Boolean {
        return oldItem.codeClient == newItem.codeClient
    }
    
    override fun areContentsTheSame(oldItem: ScaAffected, newItem: ScaAffected): Boolean {
        return oldItem == newItem
    }
}