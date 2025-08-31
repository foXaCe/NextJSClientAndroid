package com.nextjsclient.android.ui.ruptures

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextjsclient.android.R
import com.nextjsclient.android.databinding.FragmentRupturesDetailBinding
import kotlinx.coroutines.launch

class RupturesDetailFragment : Fragment() {
    
    private var _binding: FragmentRupturesDetailBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: RupturesDetailViewModel by viewModels()
    private lateinit var adapter: RupturesHistoryAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRupturesDetailBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        android.util.Log.d("RupturesFragment", "=== DÉBUT onViewCreated ===")
        
        setupRecyclerView()
        observeViewModel()
        
        // Récupérer les arguments
        val codeProduit = arguments?.getString("codeProduit") ?: ""
        val supplier = arguments?.getString("supplier") ?: ""
        val productName = arguments?.getString("productName") ?: ""
        
        android.util.Log.d("RupturesFragment", "Arguments reçus:")
        android.util.Log.d("RupturesFragment", "  - codeProduit: '$codeProduit'")
        android.util.Log.d("RupturesFragment", "  - supplier: '$supplier'")
        android.util.Log.d("RupturesFragment", "  - productName: '$productName'")
        
        // Mettre à jour le titre
        binding.productNameText.text = productName
        
        // Charger les données
        if (codeProduit.isNotEmpty() && supplier.isNotEmpty()) {
            android.util.Log.d("RupturesFragment", "Lancement du chargement des données...")
            viewModel.loadRuptureHistory(codeProduit, supplier)
        } else {
            android.util.Log.w("RupturesFragment", "Arguments manquants, pas de chargement")
        }
        
        // Bouton de retour
        binding.backButton.setOnClickListener {
            requireActivity().finish()
        }
        
        android.util.Log.d("RupturesFragment", "=== FIN onViewCreated ===")
    }
    
    private fun setupRecyclerView() {
        adapter = RupturesHistoryAdapter()
        binding.rupturesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RupturesDetailFragment.adapter
        }
    }
    
    private fun observeViewModel() {
        // Observer les données de rupture
        viewModel.ruptureHistory.observe(viewLifecycleOwner) { history ->
            android.util.Log.d("RupturesFragment", "Observer ruptureHistory: ${history.size} éléments reçus")
            adapter.submitList(history)
            
            // Gérer l'affichage vide
            if (history.isEmpty()) {
                android.util.Log.d("RupturesFragment", "Liste vide - affichage du message vide")
                binding.emptyView.visibility = View.VISIBLE
                binding.rupturesRecyclerView.visibility = View.GONE
            } else {
                android.util.Log.d("RupturesFragment", "Liste avec données - affichage du RecyclerView")
                binding.emptyView.visibility = View.GONE
                binding.rupturesRecyclerView.visibility = View.VISIBLE
            }
        }
        
        // Observer le résumé des ruptures
        viewModel.ruptureSummary.observe(viewLifecycleOwner) { summary ->
            android.util.Log.d("RupturesFragment", "Observer ruptureSummary: $summary")
            
            // N'afficher la carte que s'il y a des ruptures
            if (summary.totalRuptures > 0) {
                binding.summaryCard.root.visibility = View.VISIBLE
                // Mettre à jour les vues de la carte de résumé
                binding.summaryCard.totalRupturesText.text = summary.totalRuptures.toString()
                binding.summaryCard.totalCommandedText.text = summary.totalCommanded.toString()
                binding.summaryCard.totalMissingText.text = summary.totalMissing.toString()
                binding.summaryCard.deliveryRateText.text = String.format("%.1f%%", summary.deliveryRate)
            } else {
                binding.summaryCard.root.visibility = View.GONE
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                // Formater le message d'erreur avec les ressources string
                val errorMessage = getString(R.string.error_loading_rupture_history, it)
                // Afficher l'erreur avec un Snackbar
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, errorMessage, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance(codeProduit: String, supplier: String, productName: String): RupturesDetailFragment {
            return RupturesDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("codeProduit", codeProduit)
                    putString("supplier", supplier)
                    putString("productName", productName)
                }
            }
        }
    }
}