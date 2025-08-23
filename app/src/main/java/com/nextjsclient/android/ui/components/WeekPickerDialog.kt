package com.nextjsclient.android.ui.components

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.nextjsclient.android.R
import com.nextjsclient.android.ui.scamark.ScamarkViewModel
import java.text.SimpleDateFormat
import java.util.*

class WeekPickerDialog(
    context: Context,
    private val viewModel: ScamarkViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onWeekSelected: (Int, Int) -> Unit
) : Dialog(context) {

    private lateinit var closeButton: MaterialButton
    private lateinit var previousYearButton: MaterialButton
    private lateinit var nextYearButton: MaterialButton
    private lateinit var currentYearDisplay: TextView
    private lateinit var weeksRecyclerView: RecyclerView

    private lateinit var weekGridAdapter: WeekGridAdapter
    private var currentDisplayYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var isInitialLoadComplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val view = LayoutInflater.from(context).inflate(R.layout.week_picker_material3, null)
        setContentView(view)
        
        // Configuration de la fenêtre
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
        
        initViews(view)
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        
        // Initialiser l'affichage et charger automatiquement toutes les semaines disponibles
        updateYearDisplay()
        loadAllAvailableWeeks()
        loadWeeksForCurrentYear()
    }

    private fun initViews(view: android.view.View) {
        closeButton = view.findViewById(R.id.closeButton)
        previousYearButton = view.findViewById(R.id.previousYearButton)
        nextYearButton = view.findViewById(R.id.nextYearButton)
        currentYearDisplay = view.findViewById(R.id.currentYearDisplay)
        weeksRecyclerView = view.findViewById(R.id.weeksRecyclerView)
    }

    private fun setupRecyclerView() {
        weekGridAdapter = WeekGridAdapter { weekItem ->
            if (weekItem.hasData) {
                // Semaine disponible : sélection normale
                android.util.Log.d("WeekPickerDialog", "✅ Sélection semaine ${weekItem.week}/${weekItem.year}")
                onWeekSelected(weekItem.week, weekItem.year)
                dismiss()
            } else {
                // Semaine non-disponible : l'animation se fait dans l'adapter
                android.util.Log.d("WeekPickerDialog", "❌ Semaine ${weekItem.week}/${weekItem.year} pas disponible")
            }
        }
        
        weeksRecyclerView.apply {
            adapter = weekGridAdapter
            layoutManager = GridLayoutManager(context, 4) // 4 colonnes
        }
    }

    private fun setupListeners() {
        closeButton.setOnClickListener { dismiss() }
        
        previousYearButton.setOnClickListener {
            currentDisplayYear--
            updateYearDisplay()
            loadWeeksForCurrentYear()
        }
        
        nextYearButton.setOnClickListener {
            currentDisplayYear++
            updateYearDisplay()
            loadWeeksForCurrentYear()
        }
        
    }

    private fun observeViewModel() {
        // Observer les semaines disponibles
        viewModel.availableWeeks.observe(lifecycleOwner) { weeks ->
            android.util.Log.d("WeekPickerDialog", "📅 Semaines disponibles mises à jour: ${weeks.size} total")
            loadWeeksForCurrentYear()
        }
    }


    private fun updateYearDisplay() {
        currentYearDisplay.text = currentDisplayYear.toString()
        
        // Désactiver les boutons si nécessaire
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        nextYearButton.isEnabled = currentDisplayYear < currentYear // Pas d'années futures
        previousYearButton.isEnabled = currentDisplayYear > currentYear - 5 // 5 ans en arrière max
    }

    private fun loadAllAvailableWeeks() {
        android.util.Log.d("WeekPickerDialog", "🔄 Chargement automatique d'un batch supplémentaire de semaines")
        // Charger un seul batch supplémentaire pour avoir plus de semaines disponibles
        if (viewModel.canLoadMoreWeeks.value == true) {
            viewModel.loadMoreWeeks()
        }
    }
    
    private fun loadMoreWeeksRecursively() {
        // Empêcher les appels récursifs infinis si le chargement initial est terminé
        if (isInitialLoadComplete) {
            android.util.Log.d("WeekPickerDialog", "🛑 Chargement récursif empêché - initial load terminé")
            return
        }
        
        // Vérifier s'il peut y avoir plus de semaines
        val canLoadMore = viewModel.canLoadMoreWeeks.value ?: true
        if (canLoadMore) {
            android.util.Log.d("WeekPickerDialog", "🔄 Chargement automatique d'un batch de semaines")
            viewModel.loadMoreWeeks()
            
            // Observer une seule fois pour déclencher le prochain chargement
            viewModel.isLoadingMoreWeeks.observe(lifecycleOwner, object : androidx.lifecycle.Observer<Boolean> {
                override fun onChanged(isLoading: Boolean) {
                    if (!isLoading) {
                        // Retirer cet observer et continuer le chargement récursif
                        viewModel.isLoadingMoreWeeks.removeObserver(this)
                        // Programmer le prochain chargement avec un délai pour éviter la surcharge
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // DOUBLE-CHECK: Vérifier à nouveau canLoadMoreWeeks avant l'appel récursif
                            val canStillLoadMore = viewModel.canLoadMoreWeeks.value ?: false
                            android.util.Log.d("WeekPickerDialog", "🔄 DOUBLE_CHECK avant récursion: canLoadMore=$canStillLoadMore")
                            if (canStillLoadMore && !isInitialLoadComplete) {
                                loadMoreWeeksRecursively()
                            } else {
                                android.util.Log.w("WeekPickerDialog", "🛑 RECURSION_STOPPED - canLoadMore=$canStillLoadMore, isInitialComplete=$isInitialLoadComplete")
                                isInitialLoadComplete = true
                                android.util.Log.d("WeekPickerDialog", "✅ Chargement initial terminé")
                            }
                        }, 100)
                    }
                }
            })
        } else {
            android.util.Log.d("WeekPickerDialog", "✅ Chargement automatique terminé - toutes les semaines chargées")
            isInitialLoadComplete = true
            android.util.Log.d("WeekPickerDialog", "✅ Chargement initial terminé")
        }
    }

    private fun loadWeeksForCurrentYear() {
        android.util.Log.d("WeekPickerDialog", "📅 loadWeeksForCurrentYear pour année $currentDisplayYear")
        val weekItems = viewModel.generateWeekGridItems(currentDisplayYear)
        android.util.Log.d("WeekPickerDialog", "📅 Reçu ${weekItems.size} weekItems (${weekItems.count { it.hasData }} avec données)")
        weekGridAdapter.submitList(weekItems)
    }
}