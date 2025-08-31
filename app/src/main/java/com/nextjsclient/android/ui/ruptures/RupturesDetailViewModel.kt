package com.nextjsclient.android.ui.ruptures

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextjsclient.android.data.models.RuptureHistory
import com.nextjsclient.android.data.models.RuptureSummary
import com.nextjsclient.android.data.repository.FirebaseRepository
import kotlinx.coroutines.launch

class RupturesDetailViewModel : ViewModel() {
    
    private val repository = FirebaseRepository()
    
    private val _ruptureHistory = MutableLiveData<List<RuptureHistory>>()
    val ruptureHistory: LiveData<List<RuptureHistory>> = _ruptureHistory
    
    private val _ruptureSummary = MutableLiveData<RuptureSummary>()
    val ruptureSummary: LiveData<RuptureSummary> = _ruptureSummary
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    fun loadRuptureHistory(codeProduit: String, supplier: String) {
        android.util.Log.d("RupturesViewModel", "=== DÉBUT loadRuptureHistory ===")
        android.util.Log.d("RupturesViewModel", "Chargement pour codeProduit: '$codeProduit', supplier: '$supplier'")
        
        viewModelScope.launch {
            _errorMessage.value = null
            
            try {
                android.util.Log.d("RupturesViewModel", "Appel repository.getRuptureHistoryForProduct...")
                val history = repository.getRuptureHistoryForProduct(codeProduit, supplier)
                android.util.Log.d("RupturesViewModel", "Réponse repository: ${history.size} éléments")
                
                _ruptureHistory.value = history
                android.util.Log.d("RupturesViewModel", "LiveData mis à jour")
                
                // Charger également le résumé
                android.util.Log.d("RupturesViewModel", "Chargement du résumé...")
                val summary = repository.getRuptureSummaryForProduct(codeProduit, supplier)
                _ruptureSummary.value = summary
                android.util.Log.d("RupturesViewModel", "Résumé chargé: $summary")
                
            } catch (e: Exception) {
                android.util.Log.e("RupturesViewModel", "Erreur dans loadRuptureHistory: ${e.message}", e)
                _errorMessage.value = e.message ?: "Unknown error"
            } finally {
                android.util.Log.d("RupturesViewModel", "=== FIN loadRuptureHistory ===")
            }
        }
    }
}