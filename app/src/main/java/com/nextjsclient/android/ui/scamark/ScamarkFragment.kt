package com.nextjsclient.android.ui.scamark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.nextjsclient.android.R
import com.nextjsclient.android.databinding.FragmentScamarkBinding

class ScamarkFragment : Fragment() {
    
    private var _binding: FragmentScamarkBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ScamarkViewModel by activityViewModels()
    private lateinit var productsAdapter: ScamarkProductAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScamarkBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        android.util.Log.d("ScamarkFragment", "🚀 onViewCreated DÉBUT")
        val startTime = System.currentTimeMillis()
        
        // Read supplier parameter from arguments
        val supplierFromArgs = arguments?.getString("supplier")
        android.util.Log.d("ScamarkFragment", "📦 Arguments reçus: supplier = '$supplierFromArgs'")
        android.util.Log.d("ScamarkFragment", "📊 ViewModel supplier actuel: '${viewModel.selectedSupplier.value}'")
        
        if (supplierFromArgs != null) {
            android.util.Log.d("ScamarkFragment", "🔄 Chargement normal pour $supplierFromArgs avec ViewModel isolé")
            
            // IMPORTANT: Toujours forcer le rechargement même si c'est le même fournisseur
            // car on peut venir de l'aperçu avec des données mixtes
            if (viewModel.selectedSupplier.value == supplierFromArgs) {
                android.util.Log.d("ScamarkFragment", "🔄 FORCER rechargement pour $supplierFromArgs (même supplier mais venant d'aperçu)")
                // Nettoyer seulement les données mixtes sans affecter previousWeekProducts
                viewModel.forceReloadSupplierData(supplierFromArgs)
            } else {
                viewModel.selectSupplier(supplierFromArgs)
            }
            
            // Vérifier s'il y a un filtre à appliquer
            val filterFromArgs = arguments?.getString("filter")
            if (filterFromArgs != null) {
                android.util.Log.d("ScamarkFragment", "🎯 Application du filtre depuis les arguments: $filterFromArgs")
                viewModel.setProductFilter(filterFromArgs)
            }
        } else {
            android.util.Log.w("ScamarkFragment", "⚠️ Pas de supplier dans les arguments! Utilisation du supplier par défaut")
            viewModel.selectSupplier("all")
        }
        
        setupRecyclerView()
        setupWeekSpinner()
        observeViewModel()
        setupSwipeRefresh()
        setupFab()
        
        val endTime = System.currentTimeMillis()
        android.util.Log.d("ScamarkFragment", "✅ onViewCreated TERMINÉ - Durée: ${endTime - startTime}ms")
    }
    
    private fun setupRecyclerView() {
        productsAdapter = ScamarkProductAdapter(
            onItemClick = { product ->
                // Handle product click - open detail view
                showProductDetail(product)
            },
            onEditClick = { product ->
                // Handle edit click
                showEditDialog(product)
            },
            onDeleteClick = { product ->
                // TODO: Implémenter la suppression si nécessaire
            },
            getProductStatus = { productName ->
                viewModel.getProductStatus(productName)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = productsAdapter
            // Pas d'animation du sélecteur lors du scroll - il reste toujours visible
        }
    }
    
    private fun setupWeekSpinner() {
        // Setup modern week selector
        setupModernWeekSelector()
    }
    
    private fun setupModernWeekSelector() {
        // Get week selector views
        val currentWeekDisplay = binding.root.findViewById<TextView>(R.id.currentWeekDisplay)
        val previousWeekButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.previousWeekButton)
        val nextWeekButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.nextWeekButton)
        val currentWeekButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.currentWeekButton)
        val weekListButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.weekListButton)
        
        // Observe current week changes
        viewModel.selectedWeek.observe(viewLifecycleOwner) { week ->
            viewModel.selectedYear.observe(viewLifecycleOwner) { year ->
                if (week != null && year != null) {
                    val weekStr = week.toString().padStart(2, '0')
                    val dates = viewModel.formatWeekDatesOnly(year, week)
                    currentWeekDisplay.text = "S$weekStr - $dates"
                }
            }
        }
        
        // Setup button listeners
        previousWeekButton.setOnClickListener { navigateToPreviousWeek() }
        nextWeekButton.setOnClickListener { navigateToNextWeek() }
        currentWeekButton.setOnClickListener { navigateToCurrentWeek() }
        weekListButton.setOnClickListener { showWeekListDialog() }
    }
    
    private fun navigateToPreviousWeek() {
        val currentWeek = viewModel.selectedWeek.value ?: return
        val currentYear = viewModel.selectedYear.value ?: return
        
        if (currentWeek > 1) {
            viewModel.selectWeek(currentYear, currentWeek - 1)
        } else {
            viewModel.selectWeek(currentYear - 1, 52) // Go to last week of previous year
        }
    }
    
    private fun navigateToNextWeek() {
        val currentWeek = viewModel.selectedWeek.value ?: return
        val currentYear = viewModel.selectedYear.value ?: return
        
        if (currentWeek < 52) {
            viewModel.selectWeek(currentYear, currentWeek + 1)
        } else {
            viewModel.selectWeek(currentYear + 1, 1) // Go to first week of next year
        }
    }
    
    private fun navigateToCurrentWeek() {
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        viewModel.selectWeek(currentYear, currentWeek)
    }
    
    private fun getCurrentISOWeek(): Int {
        val calendar = java.util.Calendar.getInstance()
        val date = calendar.time
        
        calendar.time = date
        val dayOfWeek = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -dayOfWeek + 3)
        val firstThursday = calendar.timeInMillis
        
        calendar.set(java.util.Calendar.MONTH, 0)
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        if (calendar.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.THURSDAY) {
            val daysToAdd = (4 - calendar.get(java.util.Calendar.DAY_OF_WEEK) + 7) % 7
            calendar.add(java.util.Calendar.DAY_OF_YEAR, daysToAdd)
        }
        
        return 1 + ((firstThursday - calendar.timeInMillis) / (7 * 24 * 60 * 60 * 1000)).toInt()
    }
    
    private fun showWeekListDialog() {
        val availableWeeks = viewModel.availableWeeks.value ?: return
        val currentWeek = viewModel.selectedWeek.value ?: 0
        val currentYear = viewModel.selectedYear.value ?: 0
        
        // Créer un BottomSheetDialog Material 3
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_week_selector, null)
        bottomSheetDialog.setContentView(view)
        
        // Configurer le RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.weeksRecyclerView)
        val loadMoreButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.loadMoreWeeksButton)
        
        val adapter = WeekSelectorAdapter(availableWeeks.map { 
            com.nextjsclient.android.data.models.WeekInfo(it.year, it.week, it.supplier) 
        }, currentWeek, currentYear) { selectedWeek ->
            viewModel.selectWeek(selectedWeek.year, selectedWeek.week)
            bottomSheetDialog.dismiss()
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        // Configurer le bouton "Charger plus"
        setupLoadMoreButton(loadMoreButton, adapter, bottomSheetDialog)
        
        bottomSheetDialog.show()
    }
    
    private fun setupLoadMoreButton(
        loadMoreButton: com.google.android.material.button.MaterialButton,
        adapter: WeekSelectorAdapter,
        bottomSheetDialog: com.google.android.material.bottomsheet.BottomSheetDialog
    ) {
        // Observer les états du ViewModel
        viewModel.canLoadMoreWeeks.observe(viewLifecycleOwner) { canLoadMore ->
            loadMoreButton.visibility = if (canLoadMore) View.VISIBLE else View.GONE
        }
        
        viewModel.isLoadingMoreWeeks.observe(viewLifecycleOwner) { isLoading ->
            loadMoreButton.isEnabled = !isLoading
            loadMoreButton.text = if (isLoading) getString(R.string.loading) else getString(R.string.load_more)
        }
        
        viewModel.availableWeeks.observe(viewLifecycleOwner) { weeks ->
            // Mettre à jour l'adaptateur avec les nouvelles semaines
            val weekInfos = weeks.map { 
                com.nextjsclient.android.data.models.WeekInfo(it.year, it.week, it.supplier) 
            }
            adapter.updateWeeks(weekInfos)
        }
        
        // Action du bouton
        loadMoreButton.setOnClickListener {
            android.util.Log.d("ScamarkFragment", "🔘 Bouton 'Charger plus' cliqué")
            viewModel.loadMoreWeeks()
        }
        
        // Nettoyer les observers quand le dialog se ferme
        bottomSheetDialog.setOnDismissListener {
            viewModel.canLoadMoreWeeks.removeObservers(viewLifecycleOwner)
            viewModel.isLoadingMoreWeeks.removeObservers(viewLifecycleOwner)
            viewModel.availableWeeks.removeObservers(viewLifecycleOwner)
        }
    }
    
    private fun observeViewModel() {
        viewModel.products.observe(viewLifecycleOwner) { products ->
            productsAdapter.submitList(products)
            updateEmptyViewState(products.isEmpty(), viewModel.isLoading.value ?: false)
        }
        
        // Observer le filtre pour mettre à jour l'adaptateur et l'interface
        viewModel.productFilter.observe(viewLifecycleOwner) { filter ->
            // Mettre à jour les flags selon le filtre
            productsAdapter.isShowingSortants = (filter == "sortants")
            productsAdapter.isShowingEntrants = (filter == "entrants")
            // Forcer la mise à jour de la liste pour appliquer les couleurs
            productsAdapter.notifyDataSetChanged()
            
            // Masquer/afficher le sélecteur de semaine selon le filtre
            updateWeekSelectorVisibility(filter)
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Utiliser seulement le SwipeRefreshLayout pour le chargement
            binding.swipeRefresh.isRefreshing = isLoading
            // Le ProgressBar reste masqué pendant le chargement normal
            binding.progressBar.visibility = View.GONE
            // Mettre à jour l'état de la vue vide
            updateEmptyViewState(viewModel.products.value?.isEmpty() ?: true, isLoading)
        }
        
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        
        // Week display is now handled in setupModernWeekSelector()
    }
    
    private fun updateEmptyViewState(isEmpty: Boolean, isLoading: Boolean) {
        if (!isEmpty) {
            // Il y a des produits, masquer la vue vide
            binding.emptyView.visibility = View.GONE
        } else if (isLoading) {
            // Chargement en cours
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.findViewById<TextView>(R.id.emptyText)?.apply {
                text = "Produits en chargement..."
                visibility = View.VISIBLE
            }
            binding.emptyView.findViewById<TextView>(R.id.emptySubtext)?.apply {
                text = getString(R.string.please_wait)
                visibility = View.VISIBLE
            }
            binding.emptyView.findViewById<android.widget.ProgressBar>(R.id.emptyProgressBar)?.visibility = View.VISIBLE
            binding.emptyView.findViewById<android.widget.ImageView>(R.id.emptyIcon)?.visibility = View.GONE
        } else {
            // Pas de chargement et liste vide = afficher seulement l'image Anecoop
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.findViewById<TextView>(R.id.emptyText)?.visibility = View.GONE
            binding.emptyView.findViewById<TextView>(R.id.emptySubtext)?.visibility = View.GONE
            binding.emptyView.findViewById<android.widget.ProgressBar>(R.id.emptyProgressBar)?.visibility = View.GONE
            binding.emptyView.findViewById<android.widget.ImageView>(R.id.emptyIcon)?.visibility = View.VISIBLE
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            // Configurer les couleurs du loader
            setColorSchemeResources(
                R.color.md_theme_light_primary,
                R.color.md_theme_light_secondary,
                R.color.md_theme_light_tertiary
            )
            
            // Augmenter la distance de déclenchement pour éviter les activations accidentelles
            setDistanceToTriggerSync(300)
            
            // Ne permettre le refresh que si on est en haut de la liste
            setOnChildScrollUpCallback { _, _ ->
                val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
                val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
                firstVisiblePosition > 0
            }
            
            setOnRefreshListener {
                viewModel.refresh(activity)
            }
        }
    }
    
    private fun setupFab() {
        binding.fab.setOnClickListener {
            // Show add product dialog
            showAddProductDialog()
        }
    }
    
    private fun showProductDetail(product: com.nextjsclient.android.data.models.ScamarkProduct) {
        // Navigate to product detail screen with selected week/year
        val selectedWeek = viewModel.selectedWeek.value ?: getCurrentISOWeek()
        val selectedYear = viewModel.selectedYear.value ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        
        // Marquer comme navigation interne pour éviter la re-authentification biométrique
        (activity as? com.nextjsclient.android.MainActivity)?.markInternalNavigation()
        
        val intent = com.nextjsclient.android.ProductDetailActivity.createIntent(
            requireContext(), 
            product, 
            selectedYear, 
            selectedWeek
        )
        startActivity(intent)
    }
    
    private fun showEditDialog(product: com.nextjsclient.android.data.models.ScamarkProduct) {
        // Show edit dialog
        // You can implement an edit dialog fragment
    }
    
    private fun showAddProductDialog() {
        // Show add product dialog
        // You can implement an add dialog fragment
    }
    
    /**
     * Switch to a specific supplier (called from MainActivity)
     */
    fun switchSupplier(supplier: String) {
        // Store in arguments for future reference
        if (arguments == null) {
            arguments = Bundle()
        }
        arguments?.putString("supplier", supplier)
        
        viewModel.selectSupplier(supplier)
        // Appliquer la couleur du fournisseur au sélecteur de semaine
        applySupplierThemeToWeekSelector(supplier)
    }
    
    private fun applySupplierThemeToWeekSelector(supplier: String) {
        // Vérifier que le binding existe (le fragment est créé)
        if (_binding == null) return
        
        val weekSelector = binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.weekSelector) ?: return
        val context = requireContext()
        
        when (supplier.lowercase()) {
            "anecoop" -> {
                // Bleu pour Anecoop
                weekSelector.strokeColor = androidx.core.content.ContextCompat.getColor(context, R.color.anecoop_primary)
                weekSelector.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.anecoop_surface_variant))
            }
            "solagora" -> {
                // Vert pour Solagora
                weekSelector.strokeColor = androidx.core.content.ContextCompat.getColor(context, R.color.solagora_primary)
                weekSelector.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.solagora_surface_variant))
            }
            else -> {
                // Couleur par défaut
                weekSelector.strokeColor = androidx.core.content.ContextCompat.getColor(context, R.color.md_theme_light_primary)
                weekSelector.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.md_theme_light_surfaceContainerHigh))
            }
        }
    }
    
    /**
     * Met à jour la visibilité du sélecteur de semaine selon le filtre actif
     */
    private fun updateWeekSelectorVisibility(filter: String) {
        val weekSelector = binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.weekSelector)
        val filterMessage = binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.filterMessage)
        
        if (filter != "all") {
            // Masquer le sélecteur de semaine et afficher le message
            weekSelector?.visibility = View.GONE
            filterMessage?.visibility = View.VISIBLE
            
            // Mettre à jour le texte du message selon le filtre
            val messageText = filterMessage?.findViewById<android.widget.TextView>(R.id.filterMessageText)
            when (filter) {
                "entrants" -> messageText?.text = getString(R.string.filter_incoming_message)
                "sortants" -> messageText?.text = getString(R.string.filter_outgoing_message)
                "promo" -> messageText?.text = getString(R.string.filter_promo_message)
                else -> messageText?.text = getString(R.string.filter_active_message)
            }
        } else {
            // Afficher le sélecteur de semaine et masquer le message
            weekSelector?.visibility = View.VISIBLE
            filterMessage?.visibility = View.GONE
        }
    }
    
    /**
     * Perform search (called from MainActivity)
     */
    fun performSearch(query: String?) {
        viewModel.searchProducts(query)
    }
    
    
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}