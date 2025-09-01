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
import com.nextjsclient.android.MainActivity
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
        
        // Read supplier parameter from arguments
        val supplierFromArgs = arguments?.getString("supplier")
        
        if (supplierFromArgs != null) {
            // Vérifier si on vient de l'aperçu avec une semaine spécifique
            val mainActivity = (activity as? MainActivity)
            val navigationWeekInfo = mainActivity?.getSelectedWeekForNavigation()
            
            if (navigationWeekInfo != null) {
                // Navigation depuis l'aperçu avec une semaine spécifique
                val (year, week) = navigationWeekInfo
                viewModel.selectSupplier(supplierFromArgs)
                viewModel.selectWeek(year, week)
                
                // Nettoyer les informations de navigation après utilisation
                mainActivity.clearPreloadedCache()
            } else {
                // Navigation normale - d'abord vérifier s'il y a des données préchargées
                val hasDataFromOverview = mainActivity?.hasPreloadedDataFor(supplierFromArgs) == true
                
                if (hasDataFromOverview && mainActivity != null) {
                    // Charger d'abord les données préchargées
                    mainActivity.loadPreloadedDataToViewModel(supplierFromArgs, viewModel)
                } else {
                    // Pas de données préchargées, navigation normale
                    // Déterminer si on doit réinitialiser le filtre
                    val filterFromArgs = arguments?.getString("filter")
                    val shouldResetFilter = filterFromArgs == null // Réinitialiser seulement si aucun filtre n'est passé
                    
                    if (viewModel.selectedSupplier.value == supplierFromArgs) {
                        viewModel.forceReloadSupplierData(supplierFromArgs, shouldResetFilter)
                    } else {
                        viewModel.selectSupplier(supplierFromArgs, shouldResetFilter)
                    }
                    
                    // Appliquer le filtre s'il y en a un
                    if (filterFromArgs != null) {
                        viewModel.setProductFilter(filterFromArgs)
                    }
                }
            }
        } else {
            viewModel.selectSupplier("all", true) // Réinitialiser le filtre quand on navigue vers "all"
        }
        
        setupRecyclerView()
        setupWeekSpinner()
        setupSearchSuggestions()
        observeViewModel()
        setupSwipeRefresh()
        setupFab()
        setupExpressiveLoader()
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
                viewModel.selectWeek(year, week, "arrows")
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
            viewModel.selectWeek(selectedYear, selectedWeek, "picker")
        }
        dialog.show()
    }
    
    private fun showOldWeekHistoryDialog() {
        // Obtenir les semaines disponibles depuis le ViewModel
        val availableWeeks = viewModel.availableWeeks.value ?: emptyList()
        
        if (availableWeeks.isEmpty()) {
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
                viewModel.selectWeek(selectedWeek.year, selectedWeek.week)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private var loaderShownTime = 0L
    private val MIN_LOADER_DURATION = 250L // Durée minimale d'affichage du loader en ms
    private var loaderAnimator: android.animation.ValueAnimator? = null
    
    private fun updateSwipeRefreshForSupplier(supplier: String?) {
        val swipeRefresh = binding.root.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        
        // Récupérer la couleur de surface adaptative depuis le thème
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        val adaptiveSurfaceColor = typedValue.data
        
        swipeRefresh?.apply {
            // Couleurs adaptées au fournisseur
            when (supplier?.lowercase()) {
                "anecoop" -> {
                    setColorSchemeResources(
                        R.color.anecoop_primary,
                        R.color.anecoop_secondary,
                        R.color.md_theme_light_tertiary
                    )
                    setProgressBackgroundColorSchemeColor(adaptiveSurfaceColor)
                }
                "solagora" -> {
                    setColorSchemeResources(
                        R.color.solagora_primary,
                        R.color.solagora_secondary,
                        R.color.md_theme_light_tertiary
                    )
                    setProgressBackgroundColorSchemeColor(adaptiveSurfaceColor)
                }
                else -> {
                    // Couleurs par défaut
                    setColorSchemeResources(
                        R.color.md_theme_light_primary,
                        R.color.md_theme_light_secondary,
                        R.color.md_theme_light_tertiary
                    )
                    setProgressBackgroundColorSchemeColor(adaptiveSurfaceColor)
                }
            }
        }
    }

    private fun setupExpressiveLoader() {
        val loadingOverlay = binding.loadingOverlay
        val loaderBackground = loadingOverlay.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.loaderBackground)
        val loaderAccent = loadingOverlay.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.loaderAccent)
        
        // Animation de pulse pour le background
        loaderBackground?.let { loader ->
            val pulseAnimation = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.loader_pulse)
            loader.startAnimation(pulseAnimation)
        }
        
        // Animation inverse pour l'accent
        loaderAccent?.let { loader ->
            val reverseAnimation = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.loader_rotate_reverse)
            loader.startAnimation(reverseAnimation)
        }
    }

    private fun observeViewModel() {
        viewModel.products.observe(viewLifecycleOwner) { products ->
            // Animation fluide pour l'apparition des produits
            animateProductsUpdate(products)
            
            // Gérer l'affichage de la vue vide
            updateEmptyView(products)
        }
        
        
        // Observer les changements de fournisseur pour activer les animations
        viewModel.selectedSupplier.observe(viewLifecycleOwner) { supplier ->
            // Activer les animations d'entrée pour les nouveaux éléments seulement
            productsAdapter.enableEntranceAnimations()
            
            // Met à jour les couleurs du SwipeRefresh selon le fournisseur
            updateSwipeRefreshForSupplier(supplier)
            
            // Mettre à jour les couleurs du FAB si il est configuré
            if (fabSetupDone) {
                val scrollToTopFab = binding.root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.scrollToTopFab)
                scrollToTopFab?.let { updateFabColors(it) }
            }
        }
        
        // Observer le state de loading pour réinitialiser l'adapter
        viewModel.isLoadingWeekChange.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                productsAdapter.submitList(emptyList())
                productsAdapter.notifyDataSetChanged()
            }
        }
        
        // Observer les changements de filtre pour mettre à jour les couleurs de l'adapter
        viewModel.productFilter.observe(viewLifecycleOwner) { filter ->
            productsAdapter.isShowingEntrants = (filter == "entrants")
            productsAdapter.isShowingSortants = (filter == "sortants")
            productsAdapter.notifyDataSetChanged() // Force la mise à jour des couleurs
        }
        
        // Observer les changements de semaine pour afficher un loader complet
        viewModel.isLoadingWeekChange.observe(viewLifecycleOwner) { isLoadingWeekChange ->
            // Ne pas afficher le loader pour les changements de semaine (utiliser seulement l'indicateur du sélecteur)
            val weekSelector = binding.root.findViewById<com.nextjsclient.android.ui.components.Material3WeekSelector>(R.id.weekSelector)
            if (isLoadingWeekChange) {
                // Indicateur subtil sur le sélecteur de semaine seulement
                weekSelector?.animate()
                    ?.alpha(0.7f)
                    ?.setDuration(150)
                    ?.start()
                weekSelector?.isEnabled = false
            } else {
                weekSelector?.animate()
                    ?.alpha(1f)
                    ?.setDuration(150)
                    ?.start()
                weekSelector?.isEnabled = true
            }
        }
        
        // Observer les suggestions de recherche
        viewModel.searchSuggestions.observe(viewLifecycleOwner) { suggestions ->
            suggestionsAdapter.submitList(suggestions)
            
            // Afficher/masquer la carte des suggestions
            val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchBar)
            val suggestionsCard = searchContainer?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.suggestionsCard)
            
            if (suggestions.isNotEmpty()) {
                suggestionsCard?.visibility = View.VISIBLE
            } else {
                suggestionsCard?.visibility = View.GONE
            }
        }
    }
    
    private fun setupSwipeRefresh() {
        val swipeRefresh = binding.root.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        
        // Observer le chargement
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val loadingOverlay = binding.root.findViewById<View>(R.id.loadingOverlay)
            
            if (isLoading) {
                loadingOverlay?.visibility = View.VISIBLE
            } else {
                loadingOverlay?.visibility = View.GONE
                swipeRefresh?.isRefreshing = false
            }
        }
        
        swipeRefresh?.setOnRefreshListener {
            // Activer le loader immédiatement
            swipeRefresh.isRefreshing = true
            
            // Fermer la barre de recherche si elle est ouverte
            val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchBar)
            if (searchContainer?.visibility == View.VISIBLE) {
                val weekSelector = binding.weekSelector
                val searchBarCard = searchContainer.findViewById<com.google.android.material.card.MaterialCardView>(R.id.searchBarCard)
                val searchInput = searchBarCard?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)
                closeSearchMode(weekSelector, searchContainer, searchInput)
            }
            
            // Nettoyer le cache de MainActivity pour éviter la persistance des filtres
            (activity as? MainActivity)?.clearPreloadedCache()
            
            // Utiliser refresh() qui force le rechargement même si c'est le même fournisseur
            viewModel.refresh()
        }
        
        // Configuration Material 3 expressif pour le pull-to-refresh
        swipeRefresh?.apply {
            setColorSchemeResources(
                R.color.md_theme_light_primary,
                R.color.md_theme_light_secondary, 
                R.color.md_theme_light_tertiary,
                R.color.md_theme_light_primary
            )
            
            // Améliorer les paramètres visuels avec couleur adaptative
            val typedValue = android.util.TypedValue()
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            setProgressBackgroundColorSchemeColor(typedValue.data)
            setSlingshotDistance(100) // Distance plus courte pour les pages fournisseurs
            setProgressViewOffset(false, -10, 70) // Position ajustée
            setSize(androidx.swiperefreshlayout.widget.SwipeRefreshLayout.DEFAULT) // Taille normale
        }
    }
    
    private fun setupSearchSuggestions() {
        // Initialiser l'adapter des suggestions
        suggestionsAdapter = SearchSuggestionsAdapter(
            onSuggestionClick = { suggestion ->
                // Quand on clique sur une suggestion
                viewModel.applySuggestion(suggestion)
                
                // Obtenir les références aux vues
                val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchBar)
                val searchInput = searchContainer?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)
                val suggestionsCard = searchContainer?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.suggestionsCard)
                
                // Mettre à jour le texte de recherche avec seulement les 2 premiers mots
                val limitedText = suggestion.text.split(" ").take(2).joinToString(" ")
                searchInput?.setText(limitedText)
                searchInput?.setSelection(limitedText.length)
                
                // Masquer les suggestions
                suggestionsCard?.visibility = View.GONE
                
                // Fermer le clavier avec plusieurs méthodes pour assurer la fermeture
                searchInput?.let { input ->
                    // Retirer le focus d'abord
                    input.clearFocus()
                    
                    // Fermer le clavier avec un délai pour être sûr
                    view?.post {
                        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                        
                        // Essayer plusieurs approches pour fermer le clavier
                        imm?.hideSoftInputFromWindow(input.windowToken, android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS)
                        imm?.hideSoftInputFromWindow(input.windowToken, 0)
                        
                        // Force avec la vue principale si nécessaire
                        activity?.currentFocus?.let { focusedView ->
                            imm?.hideSoftInputFromWindow(focusedView.windowToken, 0)
                        }
                    }
                }
            },
            getCurrentSupplier = { viewModel.selectedSupplier.value }
        )
        
        // Configurer la RecyclerView des suggestions
        val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchBar)
        val suggestionsList = searchContainer?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.suggestionsList)
        suggestionsList?.apply {
            adapter = suggestionsAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        }
    }

    private var fabSetupDone = false
    
    private fun setupFab() {
        if (fabSetupDone) {
            android.util.Log.d("FAB", "setupFab() already done, skipping")
            return
        }
        
        android.util.Log.d("FAB", "=== setupFab() called for first time ===")
        val scrollToTopFab = binding.root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.scrollToTopFab)
        val nestedScrollView = binding.root.findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollView)
        
        if (scrollToTopFab == null) {
            android.util.Log.e("FAB", "ERROR: scrollToTopFab is null!")
            return
        }
        
        // Masquer par défaut - apparaîtra avec le scroll
        scrollToTopFab.visibility = View.GONE
        scrollToTopFab.elevation = 16f
        scrollToTopFab.isClickable = true
        scrollToTopFab.isFocusable = true
        android.util.Log.d("FAB", "FAB setup - hidden by default")
        
        // Appliquer les couleurs selon le fournisseur
        updateFabColors(scrollToTopFab)
        
        // Click listener
        scrollToTopFab.setOnClickListener {
            nestedScrollView?.smoothScrollTo(0, 0)
            android.util.Log.d("FAB", "FAB clicked - scrolling to top")
        }
        
        // Logique de scroll avec logs détaillés pour debug
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                val firstVisibleItem = layoutManager?.findFirstVisibleItemPosition() ?: 0
                val itemCount = recyclerView.adapter?.itemCount ?: 0
                
                android.util.Log.d("FAB", "SCROLL EVENT: dy=$dy, firstVisible=$firstVisibleItem, itemCount=$itemCount, fabVisible=${scrollToTopFab.visibility == View.VISIBLE}")
                
                if (dy < -10 && firstVisibleItem > 3 && scrollToTopFab.visibility == View.GONE) {
                    android.util.Log.d("FAB", "Showing FAB - scrolling UP")
                    scrollToTopFab.visibility = View.VISIBLE
                    scrollToTopFab.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                } else if ((dy > 10 || firstVisibleItem <= 2) && scrollToTopFab.visibility == View.VISIBLE) {
                    android.util.Log.d("FAB", "Hiding FAB")
                    scrollToTopFab.animate().scaleX(0f).scaleY(0f).setDuration(150).withEndAction {
                        scrollToTopFab.visibility = View.GONE
                        scrollToTopFab.scaleX = 1f
                        scrollToTopFab.scaleY = 1f
                    }.start()
                }
            }
            
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val stateText = when(newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> "IDLE"
                    RecyclerView.SCROLL_STATE_DRAGGING -> "DRAGGING"
                    RecyclerView.SCROLL_STATE_SETTLING -> "SETTLING"
                    else -> "UNKNOWN"
                }
                android.util.Log.d("FAB", "SCROLL STATE: $stateText")
            }
        })
        
        // Utiliser le NestedScrollView pour la logique du FAB (c'est lui qui scroll vraiment)
        nestedScrollView?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            android.util.Log.d("FAB", "NESTED SCROLL: scrollY=$scrollY, dy=$dy, fabVisible=${scrollToTopFab.visibility == View.VISIBLE}")
            
            // Logique FAB basée sur le NestedScrollView avec animations Material 3
            if (dy < -30 && scrollY > 500 && scrollToTopFab.visibility == View.GONE) {
                android.util.Log.d("FAB", "Showing FAB - scrolling UP in NestedScrollView")
                
                // Animation d'apparition expressive
                scrollToTopFab.visibility = View.VISIBLE
                scrollToTopFab.scaleX = 0.3f
                scrollToTopFab.scaleY = 0.3f
                scrollToTopFab.alpha = 0f
                
                scrollToTopFab.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                    .start()
                    
            } else if ((dy > 30 || scrollY <= 100) && scrollToTopFab.visibility == View.VISIBLE) {
                android.util.Log.d("FAB", "Hiding FAB - scrolling DOWN or at top")
                
                // Animation de disparition fluide
                scrollToTopFab.animate()
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .alpha(0f)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.AccelerateInterpolator(1.5f))
                    .withEndAction {
                        scrollToTopFab.visibility = View.GONE
                        scrollToTopFab.scaleX = 1f
                        scrollToTopFab.scaleY = 1f
                        scrollToTopFab.alpha = 1f
                    }
                    .start()
            }
        }
        
        fabSetupDone = true
        android.util.Log.d("FAB", "=== setupFab() complete ===")
    }
    
    private fun updateFabColors(fab: com.google.android.material.floatingactionbutton.FloatingActionButton) {
        val supplier = viewModel.selectedSupplier.value
        android.util.Log.d("FAB", "Updating FAB colors for supplier: $supplier")
        
        val blackColor = androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black)
        
        when (supplier?.lowercase()) {
            "anecoop" -> {
                val anecoopGreen = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.anecoop_primary)
                fab.backgroundTintList = android.content.res.ColorStateList.valueOf(blackColor)
                fab.imageTintList = android.content.res.ColorStateList.valueOf(anecoopGreen)
                android.util.Log.d("FAB", "Set Anecoop colors - black background, green arrow")
            }
            "solagora" -> {
                val solagoraOrange = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.solagora_primary)
                fab.backgroundTintList = android.content.res.ColorStateList.valueOf(blackColor)
                fab.imageTintList = android.content.res.ColorStateList.valueOf(solagoraOrange)
                android.util.Log.d("FAB", "Set Solagora colors - black background, orange arrow")
            }
            else -> {
                // Couleurs par défaut
                val primaryColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
                val onPrimaryColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_theme_light_onPrimary)
                fab.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                fab.imageTintList = android.content.res.ColorStateList.valueOf(onPrimaryColor)
                android.util.Log.d("FAB", "Set default colors")
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("FAB", "=== onResume() - checking FAB state ===")
        val scrollToTopFab = binding.root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.scrollToTopFab)
        android.util.Log.d("FAB", "FAB in onResume - found: ${scrollToTopFab != null}, visibility: ${scrollToTopFab?.visibility}")
    }
    
    private fun showFab(fab: com.google.android.material.floatingactionbutton.FloatingActionButton) {
        fab.visibility = View.VISIBLE
        fab.scaleX = 0f
        fab.scaleY = 0f
        fab.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()
    }
    
    private fun hideFab(fab: com.google.android.material.floatingactionbutton.FloatingActionButton) {
        fab.animate()
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(150)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                fab.visibility = View.GONE
            }
            .start()
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
    
    @Suppress("UNUSED_PARAMETER")
    private fun showEditDialog(product: com.nextjsclient.android.data.models.ScamarkProduct) {
        // TODO: Afficher le dialogue d'édition
    }
    
    fun toggleSearchMode() {
        
        val weekSelector = binding.weekSelector
        
        // L'include pointe maintenant vers le nouveau layout avec suggestions (LinearLayout)
        val searchContainer = binding.root.findViewById<LinearLayout>(R.id.searchBar)
        val searchBarCard = searchContainer?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.searchBarCard)
        val searchInput = searchBarCard?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)
        val searchClearButton = searchBarCard?.findViewById<View>(R.id.searchClearButton)
        
        
        if (searchContainer?.visibility == View.VISIBLE) {
            // Fermer la recherche
            closeSearchMode(weekSelector, searchContainer, searchInput)
        } else {
            // Ouvrir la recherche
            openSearchMode(weekSelector, searchContainer, searchInput, searchClearButton)
        }
    }
    
    private fun openSearchMode(
        weekSelector: View?,
        searchContainer: LinearLayout?,
        searchInput: com.google.android.material.textfield.TextInputEditText?,
        searchClearButton: View?
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
                            // Focus sur le champ de recherche SANS ouvrir le clavier automatiquement
                            searchInput?.requestFocus()
                            // Le clavier s'ouvrira seulement quand l'utilisateur clique dans le champ
                        }
                        .start()
                }
            }
            ?.start()
        
        // Setup des listeners
        searchClearButton?.setOnClickListener {
            searchInput?.setText("")
            searchClearButton.visibility = View.GONE
        }
        
        
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchClearButton?.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                // Recherche en temps réel pendant la frappe
                viewModel.searchProducts(s?.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        // Plus de searchActionButton - la recherche se fait automatiquement via TextWatcher
        
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

    /**
     * Animation fluide et cohérente pour la mise à jour des produits
     */
    private fun animateProductsUpdate(products: List<com.nextjsclient.android.data.models.ScamarkProduct>) {
        // Réinitialiser immédiatement les propriétés de la RecyclerView
        binding.recyclerView.clearAnimation()
        binding.recyclerView.scaleX = 1f
        binding.recyclerView.scaleY = 1f
        
        // Si c'est la même liste, pas d'animation
        if (productsAdapter.currentList == products) {
            productsAdapter.submitList(products)
            return
        }
        
        // Pour éviter l'affichage flash, masquer immédiatement et vider la liste
        binding.recyclerView.alpha = 0f
        productsAdapter.submitList(emptyList()) {
            // Attendre un court délai pour s'assurer que la liste est bien vide
            binding.recyclerView.postDelayed({
                // Maintenant soumettre les nouveaux produits
                productsAdapter.submitList(products) {
                    // Afficher avec une animation fade-in douce
                    binding.recyclerView.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
            }, 50) // Court délai pour s'assurer que la liste vide est affichée
        }
    }

    /**
     * Met à jour l'affichage de la vue vide selon les produits et le contexte de recherche
     */
    private fun updateEmptyView(products: List<com.nextjsclient.android.data.models.ScamarkProduct>) {
        val emptyView = binding.root.findViewById<View>(R.id.emptyView)
        val emptyText = binding.root.findViewById<TextView>(R.id.emptyText)
        val emptySubtext = binding.root.findViewById<TextView>(R.id.emptySubtext)
        val emptyIcon = binding.root.findViewById<android.widget.ImageView>(R.id.emptyIcon)
        val recyclerView = binding.recyclerView
        
        val searchQuery = viewModel.searchQuery.value
        val hasSearchQuery = !searchQuery.isNullOrBlank()
        
        // Ne pas afficher la vue vide si on est en cours d'animation de changement de produits
        if (binding.recyclerView.alpha < 1f) {
            // RecyclerView en cours d'animation, ne pas afficher la vue vide
            emptyView.visibility = View.GONE
            return
        }
        
        // Mettre à jour l'icône selon le fournisseur sélectionné
        updateEmptyViewIcon(emptyIcon)
        
        // La vue vide s'affiche maintenant même avec les suggestions visibles
        
        if (products.isEmpty() && hasSearchQuery) {
            // Pas de produits trouvés dans la recherche - afficher même si suggestions visibles
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            
            emptyText.text = "Ce produit n'est pas référencé cette semaine"
            emptyText.textAlignment = View.TEXT_ALIGNMENT_CENTER
            emptySubtext.text = "Cliquez sur \"Afficher tous les produits\" dans les suggestions pour voir les semaines passées"
            emptySubtext.textAlignment = View.TEXT_ALIGNMENT_CENTER
            
        } else if (products.isEmpty() && !hasSearchQuery) {
            // Pas de produits mais pas de recherche active
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            
            emptyText.text = getString(R.string.no_products_found)
            emptySubtext.text = getString(R.string.add_products_start)
            
        } else {
            // Il y a des produits à afficher ou les suggestions sont visibles
            emptyView.visibility = View.GONE
            recyclerView.visibility = if (products.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }
    
    /**
     * Met à jour l'icône de la vue vide selon le fournisseur sélectionné
     */
    private fun updateEmptyViewIcon(emptyIcon: android.widget.ImageView?) {
        val supplier = viewModel.selectedSupplier.value
        emptyIcon?.let { icon ->
            when (supplier?.lowercase()) {
                "anecoop" -> icon.setImageResource(R.drawable.anecoop)
                "solagora" -> icon.setImageResource(R.drawable.ic_solagora)
                else -> icon.setImageResource(R.drawable.anecoop) // Par défaut
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
