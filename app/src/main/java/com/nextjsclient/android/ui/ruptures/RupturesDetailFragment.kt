package com.nextjsclient.android.ui.ruptures

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
        
        setupRecyclerView()
        observeViewModel()
        
        // Récupérer les arguments
        val codeProduit = arguments?.getString("codeProduit") ?: ""
        val supplier = arguments?.getString("supplier") ?: ""
        val productName = arguments?.getString("productName") ?: ""
        
        // Mettre à jour le titre
        binding.productNameText.text = productName
        
        // Charger les données
        if (codeProduit.isNotEmpty() && supplier.isNotEmpty()) {
            viewModel.loadRuptureHistory(codeProduit, supplier)
        }
        
        // Bouton de retour
        binding.backButton.setOnClickListener {
            requireActivity().finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = RupturesHistoryAdapter()
        binding.rupturesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RupturesDetailFragment.adapter
        }
    }
    
    private fun observeViewModel() {
        viewModel.ruptureHistory.observe(viewLifecycleOwner) { history ->
            adapter.submitList(history)
            
            // Gérer l'affichage vide
            if (history.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.rupturesRecyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.rupturesRecyclerView.visibility = View.VISIBLE
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                // Afficher l'erreur avec un Snackbar
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
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