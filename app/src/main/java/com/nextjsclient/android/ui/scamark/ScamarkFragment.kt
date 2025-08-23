package com.nextjsclient.android.ui.scamark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
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
    private lateinit var suggestionsAdapter: SearchSuggestionsAdapter
    
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
        setupSearchSuggestions()
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
            onDeleteClick = { _ ->
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
        // Get Material3 week selector
        val weekSelector = binding.root.findViewById<com.nextjsclient.android.ui.components.Material3WeekSelector>(R.id.weekSelector)
        
        // Setup week selector with ViewModel
        weekSelector?.let { selector ->
            // Set initial week from ViewModel
            viewModel.selectedWeek.value?.let { week ->
                viewModel.selectedYear.value?.let { year ->
                    selector.setWeek(week, year)
                }
            }
            
            // Set listener for week changes
            selector.setOnWeekChangeListener { week, year ->
                viewModel.selectWeek(year, week)
            }
            
            // Set listener for week list dialog
            selector.setOnWeekListRequestedListener {
                showWeekListDialog()
            }
            
            // Set listener for week history (when clicking on "Semaine XX")
            selector.setOnWeekHistoryRequestedListener {
                showWeekHistoryDialog()
            }
        }
        
        // Observe week changes from ViewModel to update selector
        viewModel.selectedWeek.observe(viewLifecycleOwner) { week ->
            val year = viewModel.selectedYear.value
            if (week != null && year != null) {
                weekSelector?.setWeek(week, year)
            }
        }
        
        viewModel.selectedYear.observe(viewLifecycleOwner) { year ->
            val week = viewModel.selectedWeek.value
            if (week != null && year != null) {
                weekSelector?.setWeek(week, year)
            }
        }
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
        val currentWeek = calendar.get(java.util.Calendar.WEEK_OF_YEAR)
        viewModel.selectWeek(currentYear, currentWeek)
    }
    
    private fun showWeekListDialog() {
        showMaterial3WeekPicker()
    }
    
    private fun showWeekHistoryDialog() {
        showMaterial3WeekPicker()
    }
    
    private fun showMaterial3WeekPicker() {
        val dialog = com.nextjsclient.android.ui.components.WeekPickerDialog(
            requireContext(),
            viewModel,
            viewLifecycleOwner
        ) { selectedWeek, selectedYear ->
            android.util.Log.d("ScamarkFragment", "✅ Semaine sélectionnée: $selectedWeek de l'année $selectedYear")
            viewModel.selectWeek(selectedYear, selectedWeek)
        }
        dialog.show()
    }
    
    private fun showOldWeekHistoryDialog() {
        // Obtenir les semaines disponibles depuis le ViewModel
        val availableWeeks = viewModel.availableWeeks.value ?: emptyList()
        
        if (availableWeeks.isEmpty()) {
            android.util.Log.d("ScamarkFragment", "🔍 Aucune semaine disponible")
            return
        }
        
        // Créer un dialogue avec la liste des semaines
        val weekNames = availableWeeks.map { week ->
            "Semaine ${week.week} - ${week.year}"
        }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Choisir une semaine")
            .setItems(weekNames) { _, which ->
                val selectedWeek = availableWeeks[which]
                android.util.Log.d("ScamarkFragment", "🔍 Semaine sélectionnée: ${selectedWeek.week}/${selectedWeek.year}")
                viewModel.selectWeek(selectedWeek.year, selectedWeek.week)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun observeViewModel() {
        viewModel.products.observe(viewLifecycleOwner) { products ->
            productsAdapter.submitList(products)
        }
        
        // Observer les suggestions de recherche
        viewModel.searchSuggestions.observe(viewLifecycleOwner) { suggestions ->
            android.util.Log.d("ScamarkFragment", "🔍 Observer suggestions triggered: ${suggestions.size} suggestions reçues")
            suggestionsAdapter.submitList(suggestions)
            
            // Afficher/masquer la carte des suggestions
            val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchBar)
            val suggestionsCard = searchContainer?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.suggestionsCard)
            
            android.util.Log.d("ScamarkFragment", "🔍 searchContainer: ${searchContainer != null}, suggestionsCard: ${suggestionsCard != null}")
            
            if (suggestions.isNotEmpty()) {
                android.util.Log.d("ScamarkFragment", "🔍 Affichage des suggestions")
                suggestionsCard?.visibility = View.VISIBLE
            } else {
                android.util.Log.d("ScamarkFragment", "🔍 Masquage des suggestions")
                suggestionsCard?.visibility = View.GONE
            }
        }
    }
    
    private fun setupSwipeRefresh() {
        val swipeRefresh = binding.root.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        
        // Observer le chargement UNE SEULE FOIS au setup
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            android.util.Log.d("ScamarkFragment", "🔄 Observer isLoading triggered: $isLoading")
            android.util.Log.d("ScamarkFragment", "🔄 SwipeRefresh current state: ${swipeRefresh?.isRefreshing}")
            
            // Gérer le loader Material 3
            val loadingOverlay = binding.root.findViewById<View>(R.id.loadingOverlay)
            if (isLoading) {
                loadingOverlay?.visibility = View.VISIBLE
                loadingOverlay?.animate()?.alpha(1f)?.setDuration(200)?.start()
            } else {
                loadingOverlay?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction {
                    loadingOverlay.visibility = View.GONE
                }?.start()
            }
            
            if (!isLoading) {
                android.util.Log.d("ScamarkFragment", "🔄 Setting swipeRefresh.isRefreshing = false")
                swipeRefresh?.isRefreshing = false
            } else {
                android.util.Log.d("ScamarkFragment", "🔄 Loading is true, swipeRefresh should be spinning")
            }
        }
        
        swipeRefresh?.setOnRefreshListener {
            android.util.Log.d("ScamarkFragment", "🔄 Rafraîchissement déclenché")
            android.util.Log.d("ScamarkFragment", "🔄 SwipeRefresh state BEFORE setting: ${swipeRefresh.isRefreshing}")
            
            // Activer le loader immédiatement
            swipeRefresh.isRefreshing = true
            android.util.Log.d("ScamarkFragment", "🔄 SwipeRefresh state AFTER setting: ${swipeRefresh.isRefreshing}")
            
            // Fermer la barre de recherche si elle est ouverte
            val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchBar)
            if (searchContainer?.visibility == View.VISIBLE) {
                android.util.Log.d("ScamarkFragment", "🔄 Fermeture de la barre de recherche")
                val weekSelector = binding.weekSelector
                val searchBarCard = searchContainer.findViewById<com.google.android.material.card.MaterialCardView>(R.id.searchBarCard)
                val searchInput = searchBarCard?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)
                closeSearchMode(weekSelector, searchContainer, searchInput)
            }
            
            // Utiliser refresh() qui force le rechargement même si c'est le même fournisseur
            android.util.Log.d("ScamarkFragment", "🔄 Calling viewModel.refresh()")
            viewModel.refresh()
        }
        
        // Configurer les couleurs Material 3
        swipeRefresh?.setColorSchemeResources(
            com.google.android.material.R.color.m3_sys_color_dynamic_light_primary,
            com.google.android.material.R.color.m3_sys_color_dynamic_light_secondary,
            com.google.android.material.R.color.m3_sys_color_dynamic_light_tertiary
        )
    }
    
    private fun setupSearchSuggestions() {
        // Initialiser l'adapter des suggestions
        suggestionsAdapter = SearchSuggestionsAdapter { suggestion ->
            // Quand on clique sur une suggestion
            viewModel.applySuggestion(suggestion)
            
            // Fermer le clavier de manière plus robuste
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchContainer)
            val searchInput = searchContainer?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)
            
            // Retirer le focus et fermer le clavier
            searchInput?.clearFocus()
            imm?.hideSoftInputFromWindow(searchInput?.windowToken, android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS)
            
            // Mettre à jour le texte de recherche avec une petite animation
            searchInput?.setText(suggestion.text)
            searchInput?.setSelection(suggestion.text.length) // Positionner le curseur à la fin
        }
        
        // Configurer la RecyclerView des suggestions
        val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchBar)
        val suggestionsList = searchContainer?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestionsList)
        suggestionsList?.apply {
            adapter = suggestionsAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        }
    }

    private fun setupFab() {
        // TODO: Implémenter le FAB si nécessaire
    }
    
    private fun showProductDetail(product: com.nextjsclient.android.data.models.ScamarkProduct) {
        val intent = android.content.Intent(requireContext(), com.nextjsclient.android.ProductDetailActivity::class.java).apply {
            putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_PRODUCT_NAME, product.productName)
            putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_SUPPLIER, viewModel.selectedSupplier.value)
            putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_PRICE_RETENU, product.prixRetenu)
            putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_PRICE_OFFERT, product.prixOffert)
            putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_IS_PROMO, product.isPromo)
            
            // Utiliser les infos de articleInfo si disponible
            product.articleInfo?.let { article ->
                putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_PRODUCT_CODE, article.codeProduit)
                putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_EAN, article.ean ?: article.gencode)
                putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_CATEGORY, article.categorie ?: article.category)
                putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_BRAND, article.marque)
                putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_ORIGIN, article.origine)
            }
            
            // Infos des clients depuis les décisions
            val clientsNames = product.decisions.map { it.nomClient }
            val clientsTypes = product.decisions.map { decision -> 
                decision.clientInfo?.typeCaisse ?: "standard"
            }
            val clientsTimes = product.decisions.map { decision -> 
                decision.clientInfo?.heureDepart ?: "Non défini"
            }
            
            putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_CLIENTS_COUNT, product.decisions.size)
            putStringArrayListExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_CLIENTS_NAMES, ArrayList(clientsNames))
            putStringArrayListExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_CLIENTS_TYPES, ArrayList(clientsTypes))
            putStringArrayListExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_CLIENTS_TIMES, ArrayList(clientsTimes))
            
            // Ces infos ne sont pas directement disponibles dans le modèle actuel
            putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_CONSECUTIVE_WEEKS, 0)
            putExtra(com.nextjsclient.android.ProductDetailActivity.EXTRA_TOTAL_REFERENCES, product.totalScas)
        }
        
        // Marquer comme navigation interne pour éviter la demande de biométrie au retour
        (activity as? com.nextjsclient.android.MainActivity)?.markInternalNavigation()
        startActivity(intent)
    }
    
    private fun showEditDialog(product: com.nextjsclient.android.data.models.ScamarkProduct) {
        // TODO: Afficher le dialogue d'édition
    }
    
    fun toggleSearchMode() {
        android.util.Log.d("ScamarkFragment", "🔍 Basculement du mode recherche")
        
        val weekSelector = binding.weekSelector
        
        // L'include pointe maintenant vers le nouveau layout avec suggestions (LinearLayout)
        val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchBar)
        val searchBarCard = searchContainer?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.searchBarCard)
        val searchInput = searchBarCard?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)
        val searchBackButton = searchBarCard?.findViewById<View>(R.id.searchBackButton)
        val searchClearButton = searchBarCard?.findViewById<View>(R.id.searchClearButton)
        val searchActionButton = searchBarCard?.findViewById<View>(R.id.searchActionButton)
        
        android.util.Log.d("ScamarkFragment", "🔍 searchContainer trouvé: ${searchContainer != null}, visible: ${searchContainer?.visibility}")
        
        if (searchContainer?.visibility == View.VISIBLE) {
            // Fermer la recherche
            closeSearchMode(weekSelector, searchContainer, searchInput)
        } else {
            // Ouvrir la recherche
            openSearchMode(weekSelector, searchContainer, searchInput, searchBackButton, searchClearButton, searchActionButton)
        }
    }
    
    private fun openSearchMode(
        weekSelector: View?,
        searchContainer: LinearLayout?,
        searchInput: com.google.android.material.textfield.TextInputEditText?,
        searchBackButton: View?,
        searchClearButton: View?,
        searchActionButton: View?
    ) {
        // Animation de disparition du sélecteur de semaine
        weekSelector?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                weekSelector.visibility = View.GONE
                
                // Animation d'apparition du conteneur de recherche
                searchContainer?.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    translationY = -20f
                    animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(300)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .withEndAction {
                            // Focus sur le champ de recherche et ouvrir le clavier
                            searchInput?.requestFocus()
                            val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                            imm?.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        }
                        .start()
                }
            }
            ?.start()
        
        // Setup des listeners
        searchBackButton?.setOnClickListener {
            toggleSearchMode()
        }
        
        searchClearButton?.setOnClickListener {
            searchInput?.setText("")
            searchClearButton.visibility = View.GONE
        }
        
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                android.util.Log.d("ScamarkFragment", "🔍 Texte changé: '$s'")
                searchClearButton?.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                // Recherche en temps réel pendant la frappe
                viewModel.searchProducts(s?.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        searchActionButton?.setOnClickListener {
            performSearch(searchInput?.text?.toString())
        }
        
        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.text?.toString())
                true
            } else false
        }
    }
    
    private fun closeSearchMode(
        weekSelector: View?,
        searchContainer: LinearLayout?,
        searchInput: com.google.android.material.textfield.TextInputEditText?
    ) {
        // Fermer le clavier
        val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput?.windowToken, 0)
        
        // Animation de disparition du conteneur de recherche
        searchContainer?.animate()
            ?.alpha(0f)
            ?.translationY(-20f)
            ?.setDuration(200)
            ?.withEndAction {
                searchContainer.visibility = View.GONE
                searchInput?.setText("")
                
                // Animation d'apparition du sélecteur de semaine
                weekSelector?.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start()
                }
            }
            ?.start()
            
        // Réinitialiser la recherche
        performSearch(null)
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
