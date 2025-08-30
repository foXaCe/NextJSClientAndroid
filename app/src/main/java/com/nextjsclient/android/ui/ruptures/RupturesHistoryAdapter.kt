package com.nextjsclient.android.ui.ruptures

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextjsclient.android.R
import com.nextjsclient.android.data.models.RuptureHistory
import com.nextjsclient.android.data.models.RuptureProduct
import com.nextjsclient.android.databinding.ItemRuptureHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class RupturesHistoryAdapter : ListAdapter<RuptureHistory, RupturesHistoryAdapter.ViewHolder>(RuptureDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRuptureHistoryBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(private val binding: ItemRuptureHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(rupture: RuptureHistory) {
            // Date et heure
            binding.dateText.text = "${rupture.date} à ${rupture.time}"
            binding.weekText.text = "Semaine ${rupture.week}/${rupture.year}"
            
            // Statistiques globales
            binding.totalMissingText.text = "${rupture.totalMissing}"
            binding.ruptureCountText.text = "${rupture.ruptureCount}"
            
            // Trouver le produit spécifique dans cette rupture
            val currentProduct = rupture.products.find { it.codeProduit.isNotEmpty() }
            
            if (currentProduct != null) {
                binding.productDetailsCard.visibility = View.VISIBLE
                binding.stockDisponibleText.text = "${currentProduct.stockDisponible}"
                binding.productMissingText.text = "${currentProduct.totalMissing}"
                
                // Approvisionneur
                val approv = currentProduct.approvisionneur
                binding.approvisionneurText.text = if (approv.nom.isNotEmpty()) {
                    "${approv.prenom} ${approv.nom}"
                } else {
                    "Non défini"
                }
                
                // Liste des SCA affectées
                if (currentProduct.scasAffected.isNotEmpty()) {
                    binding.scasAffectedRecyclerView.visibility = View.VISIBLE
                    binding.noScasText.visibility = View.GONE
                    
                    val scaAdapter = ScasAffectedAdapter()
                    binding.scasAffectedRecyclerView.apply {
                        layoutManager = LinearLayoutManager(context)
                        adapter = scaAdapter
                    }
                    scaAdapter.submitList(currentProduct.scasAffected)
                } else {
                    binding.scasAffectedRecyclerView.visibility = View.GONE
                    binding.noScasText.visibility = View.VISIBLE
                }
            } else {
                binding.productDetailsCard.visibility = View.GONE
            }
            
            // Gérer l'expansion/contraction
            var isExpanded = false
            binding.expandButton.setOnClickListener {
                isExpanded = !isExpanded
                binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
                binding.expandButton.setImageResource(
                    if (isExpanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down
                )
            }
        }
    }
}

class RuptureDiffCallback : DiffUtil.ItemCallback<RuptureHistory>() {
    override fun areItemsTheSame(oldItem: RuptureHistory, newItem: RuptureHistory): Boolean {
        return oldItem.timestamp == newItem.timestamp
    }
    
    override fun areContentsTheSame(oldItem: RuptureHistory, newItem: RuptureHistory): Boolean {
        return oldItem == newItem
    }
}