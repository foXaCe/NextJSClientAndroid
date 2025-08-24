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
                onWeekSelected(weekItem.week, weekItem.year)
                dismiss()
            } else {
                // Semaine non-disponible : l'animation se fait dans l'adapter
            }
        }
        
        weeksRecyclerView.apply {
            adapter = weekGridAdapter
            layoutManager = GridLayoutManager(context, 3) // 3 colonnes
        }
    }

    private fun setupListeners() {
        closeButton.setOnClickListener { dismiss() }
        
        previousYearButton.setOnClickListener {
            currentDisplayYear--
            updateYearDisplay()
            loadWeeksForSpecificYear(currentDisplayYear)
        }
        
        nextYearButton.setOnClickListener {
            currentDisplayYear++
            updateYearDisplay()
            loadWeeksForSpecificYear(currentDisplayYear)
        }
        
    }

    private fun observeViewModel() {
        // Observer les semaines disponibles
        viewModel.availableWeeks.observe(lifecycleOwner) { _ ->
            updateYearDisplay() // Mettre à jour les boutons basé sur les nouvelles données
            loadWeeksForCurrentYear()
        }
    }
    private fun updateYearDisplay() {
        currentYearDisplay.text = currentDisplayYear.toString()
        
        // Désactiver les boutons basé sur les données disponibles
        val availableWeeks = viewModel.availableWeeks.value ?: emptyList()
        val availableYears = availableWeeks.map { it.year }.distinct().sorted()
        
        
        nextYearButton.isEnabled = if (availableYears.isNotEmpty()) {
            val canGoNext = currentDisplayYear < availableYears.maxOrNull()!!
            canGoNext
        } else {
            false
        }
        
        previousYearButton.isEnabled = if (availableYears.isNotEmpty()) {
            // Permettre de naviguer vers les années précédentes même si pas encore chargées
            val canGoPrevious = currentDisplayYear > (availableYears.minOrNull()!! - 2) // Permettre 2 ans de plus
            canGoPrevious
        } else {
            // Si aucune donnée, permettre quand même la navigation vers les 2 dernières années
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val canGoPrevious = currentDisplayYear > currentYear - 2
            canGoPrevious
        }
    }

    private fun loadAllAvailableWeeks() {
        // Forcer le chargement de toutes les semaines de tous les fournisseurs
        // pour que le sélecteur affiche toutes les semaines possibles
        viewModel.loadAvailableWeeks("all")
        
        // Charger aussi un batch supplémentaire pour avoir plus de semaines disponibles
        if (viewModel.canLoadMoreWeeks.value == true) {
            viewModel.loadMoreWeeks()
        }
    }
    
    private fun loadMoreWeeksRecursively() {
        // Empêcher les appels récursifs infinis si le chargement initial est terminé
        if (isInitialLoadComplete) {
            return
        }
        
        // Vérifier s'il peut y avoir plus de semaines
        val canLoadMore = viewModel.canLoadMoreWeeks.value ?: true
        if (canLoadMore) {
            viewModel.loadMoreWeeks()
            
            // Observer une seule fois pour déclencher le prochain chargement
            viewModel.isLoadingMoreWeeks.observe(lifecycleOwner, object : androidx.lifecycle.Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    val isLoading = value
                    if (!isLoading) {
                        // Retirer cet observer et continuer le chargement récursif
                        viewModel.isLoadingMoreWeeks.removeObserver(this)
                        // Programmer le prochain chargement avec un délai pour éviter la surcharge
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // DOUBLE-CHECK: Vérifier à nouveau canLoadMoreWeeks avant l'appel récursif
                            val canStillLoadMore = viewModel.canLoadMoreWeeks.value ?: false
                            if (canStillLoadMore && !isInitialLoadComplete) {
                                loadMoreWeeksRecursively()
                            } else {
                                isInitialLoadComplete = true
                            }
                        }, 100)
                    }
                }
            })
        } else {
            isInitialLoadComplete = true
        }
    }

    private fun loadWeeksForCurrentYear() {
        val weekItems = viewModel.generateWeekGridItems(currentDisplayYear)
        weekGridAdapter.submitList(weekItems)
    }
    
    private fun loadWeeksForSpecificYear(year: Int) {
        viewModel.loadAvailableWeeksForYear("all", year)
    }
}