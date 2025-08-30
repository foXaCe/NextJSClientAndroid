package com.nextjsclient.android.ui.ruptures

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextjsclient.android.data.models.RuptureHistory
import com.nextjsclient.android.data.repository.FirebaseRepository
import kotlinx.coroutines.launch

class RupturesDetailViewModel : ViewModel() {
    
    private val repository = FirebaseRepository()
    
    private val _ruptureHistory = MutableLiveData<List<RuptureHistory>>()
    val ruptureHistory: LiveData<List<RuptureHistory>> = _ruptureHistory
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    fun loadRuptureHistory(codeProduit: String, supplier: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val history = repository.getRuptureHistoryForProduct(codeProduit, supplier)
                _ruptureHistory.value = history
            } catch (e: Exception) {
                _errorMessage.value = "Erreur lors du chargement des données: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}