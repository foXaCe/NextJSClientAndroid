package com.nextjsclient.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.LinearLayout
import com.google.android.material.snackbar.Snackbar
import com.nextjsclient.android.data.models.ScamarkProduct
import com.nextjsclient.android.data.repository.FirebaseRepository
import com.nextjsclient.android.databinding.ActivityProductDetailBinding
import java.text.DecimalFormat
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class ProductDetailActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProductDetailBinding
    private val priceFormat = DecimalFormat("#,##0.00")
    private val firebaseRepository = FirebaseRepository()
    
    // Animation de chargement
    private var loadingAnimationJob: kotlinx.coroutines.Job? = null
    private var isAnimating = false
    
    companion object {
        const val EXTRA_PRODUCT_NAME = "product_name"
        const val EXTRA_SUPPLIER = "supplier"
        const val EXTRA_PRICE_RETENU = "price_retenu"
        const val EXTRA_PRICE_OFFERT = "price_offert"
        const val EXTRA_IS_PROMO = "is_promo"
        const val EXTRA_PRODUCT_CODE = "product_code"
        const val EXTRA_EAN = "ean"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_BRAND = "brand"
        const val EXTRA_ORIGIN = "origin"
        const val EXTRA_CLIENTS_COUNT = "clients_count"
        const val EXTRA_CLIENTS_NAMES = "clients_names"
        const val EXTRA_CLIENTS_TYPES = "clients_types"
        const val EXTRA_CLIENTS_TIMES = "clients_times"
        const val EXTRA_CONSECUTIVE_WEEKS = "consecutive_weeks"
        const val EXTRA_TOTAL_REFERENCES = "total_references"
        const val EXTRA_SELECTED_YEAR = "selected_year"
        const val EXTRA_SELECTED_WEEK = "selected_week"
        
        fun createIntent(context: Context, product: ScamarkProduct, selectedYear: Int? = null, selectedWeek: Int? = null): Intent {
            return Intent(context, ProductDetailActivity::class.java).apply {
                putExtra(EXTRA_PRODUCT_NAME, product.productName)
                putExtra(EXTRA_SUPPLIER, product.supplier)
                putExtra(EXTRA_PRICE_RETENU, product.prixRetenu)
                putExtra(EXTRA_PRICE_OFFERT, product.prixOffert)
                putExtra(EXTRA_IS_PROMO, product.isPromo)
                putExtra(EXTRA_PRODUCT_CODE, product.articleInfo?.codeProduit ?: 
                    product.decisions.firstOrNull()?.codeProduit ?: "N/A")
                putExtra(EXTRA_EAN, product.articleInfo?.gencode ?: product.articleInfo?.ean ?: "Non disponible")
                putExtra(EXTRA_CATEGORY, product.articleInfo?.categorie ?: product.articleInfo?.category ?: "Non défini")
                putExtra(EXTRA_BRAND, product.articleInfo?.marque ?: "Non défini")
                putExtra(EXTRA_ORIGIN, product.articleInfo?.origine ?: "Non défini")
                putExtra(EXTRA_CLIENTS_COUNT, product.totalScas)
                
                // Ajouter les paramètres de semaine/année sélectionnée
                selectedYear?.let { putExtra(EXTRA_SELECTED_YEAR, it) }
                selectedWeek?.let { putExtra(EXTRA_SELECTED_WEEK, it) }
                
                // Extraire les données des clients
                val clientNames = arrayListOf<String>()
                val clientTypes = arrayListOf<String>()
                val clientTimes = arrayListOf<String>()
                
                product.decisions.forEach { decision ->
                    clientNames.add(decision.clientInfo?.nom ?: decision.nomClient)
                    clientTypes.add(decision.clientInfo?.typeCaisse ?: "standard")
                    clientTimes.add(decision.clientInfo?.heureDepart ?: "Non défini")
                }
                
                putStringArrayListExtra(EXTRA_CLIENTS_NAMES, clientNames)
                putStringArrayListExtra(EXTRA_CLIENTS_TYPES, clientTypes)
                putStringArrayListExtra(EXTRA_CLIENTS_TIMES, clientTimes)
                
                // Calculer les statistiques de palmarès (valeurs d'exemple pour l'instant)
                val consecutiveWeeks = calculateConsecutiveWeeks(product)
                val totalReferences = calculateTotalReferences(product)
                putExtra(EXTRA_CONSECUTIVE_WEEKS, consecutiveWeeks)
                putExtra(EXTRA_TOTAL_REFERENCES, totalReferences)
            }
        }
        
        private fun calculateConsecutiveWeeks(product: ScamarkProduct): Int {
            // Valeur par défaut en cas de problème Firebase
            return 5
        }
        
        private fun calculateTotalReferences(product: ScamarkProduct): Int {
            // Valeur par défaut en cas de problème Firebase  
            return 15
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupViews()
        setupClientsList()
        loadProductData()
        loadRealPalmaresData()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Détails du produit"
    }
    
    private fun setupViews() {
        // Setup copy buttons
        binding.copyCodeButton.setOnClickListener {
            copyToClipboard(binding.productCode.text.toString(), "Code produit copié")
        }
        
        binding.copyEanButton.setOnClickListener {
            copyToClipboard(binding.productEan.text.toString(), "EAN copié")
        }
    }
    
    private fun setupClientsList() {
        // Le LinearLayout sera peuplé directement dans loadProductData
    }
    
    private fun loadProductData() {
        val productName = intent.getStringExtra(EXTRA_PRODUCT_NAME) ?: ""
        val supplier = intent.getStringExtra(EXTRA_SUPPLIER) ?: ""
        val priceRetenu = intent.getDoubleExtra(EXTRA_PRICE_RETENU, 0.0)
        val priceOffert = intent.getDoubleExtra(EXTRA_PRICE_OFFERT, 0.0)
        val isPromo = intent.getBooleanExtra(EXTRA_IS_PROMO, false)
        val productCode = intent.getStringExtra(EXTRA_PRODUCT_CODE) ?: "N/A"
        val ean = intent.getStringExtra(EXTRA_EAN) ?: "Non disponible"
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Non défini"
        val brand = intent.getStringExtra(EXTRA_BRAND) ?: "Non défini"
        val origin = intent.getStringExtra(EXTRA_ORIGIN) ?: "Non défini"
        val clientsCount = intent.getIntExtra(EXTRA_CLIENTS_COUNT, 0)
        val consecutiveWeeks = intent.getIntExtra(EXTRA_CONSECUTIVE_WEEKS, 0)
        val totalReferences = intent.getIntExtra(EXTRA_TOTAL_REFERENCES, 0)
        
        // Set palmarès data avec SpannableString pour colorer les chiffres
        val consecutiveText = "Consécutif $consecutiveWeeks semaines"
        val consecutiveSpannable = android.text.SpannableString(consecutiveText)
        val consecutiveStart = consecutiveText.indexOf("$consecutiveWeeks")
        val consecutiveEnd = consecutiveStart + "$consecutiveWeeks semaines".length
        consecutiveSpannable.setSpan(
            android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)),
            consecutiveStart,
            consecutiveEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        consecutiveSpannable.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            consecutiveStart,
            consecutiveEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        consecutiveSpannable.setSpan(
            android.text.style.RelativeSizeSpan(1.3f),
            consecutiveStart,
            consecutiveEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.consecutiveWeeks.text = consecutiveSpannable
        
        // Version initiale avec pourcentage par défaut
        val defaultPercentage = if (totalReferences > 0) 30 else 0 // Pourcentage par défaut
        val totalText = "Depuis le 01 Octobre $totalReferences fois"
        val totalSpannable = android.text.SpannableString(totalText)
        val totalStart = totalText.indexOf("$totalReferences fois")
        val totalEnd = totalStart + "$totalReferences fois".length
        totalSpannable.setSpan(
            android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)),
            totalStart,
            totalEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        totalSpannable.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            totalStart,
            totalEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        totalSpannable.setSpan(
            android.text.style.RelativeSizeSpan(1.3f),
            totalStart,
            totalEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // Affichage du pourcentage séparé
        updatePercentageDisplay(defaultPercentage)
        binding.totalReferences.text = totalSpannable
        
        // Set product name
        binding.productName.text = productName
        
        // Set supplier with colors
        binding.supplierBadge.text = supplier.uppercase()
        setSupplierColors(supplier)
        
        // Set promo indicator
        binding.promoIndicator.visibility = if (isPromo) View.VISIBLE else View.GONE
        
        // Set product information
        binding.productCode.text = productCode
        binding.productEan.text = ean
        binding.productCategory.text = category
        binding.productBrand.text = brand
        binding.productOrigin.text = origin
        
        // Hide EAN layout if not available
        if (ean == "Non disponible") {
            binding.eanLayout.visibility = View.GONE
        }
        
        // Set prices avec indicateur vs S-1 sur la même ligne
        if (priceRetenu > 0) {
            val baseText = "${priceFormat.format(priceRetenu)}€"
            
            // Calculer la variation vs S-1 (ici on utilise priceOffert comme référence S-1)
            if (priceOffert > 0 && priceRetenu != priceOffert) {
                val variation = ((priceRetenu - priceOffert) / priceOffert) * 100
                val sign = if (variation > 0) "+" else ""
                val priceS1 = priceFormat.format(priceOffert)
                val variationText = "  ${sign}${String.format("%.1f", variation)}% vs S-1 (${priceS1}€)"
                val fullText = "$baseText$variationText"
                
                // Créer un SpannableString pour colorer le pourcentage
                val spannable = android.text.SpannableString(fullText)
                val variationStart = fullText.indexOf(variationText.trim())
                val variationEnd = fullText.length
                
                // Couleur selon la variation (vert si positif, rouge si négatif)
                val variationColor = if (variation > 0) {
                    ContextCompat.getColor(this, android.R.color.holo_green_dark)
                } else {
                    ContextCompat.getColor(this, android.R.color.holo_red_dark)
                }
                
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(variationColor),
                    variationStart,
                    variationEnd,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                // Taille plus petite pour le vs S-1
                spannable.setSpan(
                    android.text.style.RelativeSizeSpan(0.7f),
                    variationStart,
                    variationEnd,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                binding.priceRetenu.text = spannable
            } else {
                binding.priceRetenu.text = baseText
            }
        } else {
            binding.priceRetenu.text = "Non défini"
        }
        
        binding.priceOffert.text = if (priceOffert > 0) {
            "${priceFormat.format(priceOffert)}€"
        } else {
            "Non défini"
        }
        
        // Couleur conditionnelle pour le prix offert seulement (prix retenu géré au-dessus avec vs S-1)
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val defaultColor = ContextCompat.getColor(this, typedValue.resourceId)
        binding.priceOffert.setTextColor(defaultColor)
        
        // La couleur du prix retenu est maintenant gérée dans sa section avec le SpannableString
        // Le prix de base reste en couleur normale, seul le pourcentage vs S-1 est coloré
        
        // Set clients count
        binding.clientsCount.text = clientsCount.toString()
        
        // Load clients data
        val clientNames = intent.getStringArrayListExtra(EXTRA_CLIENTS_NAMES) ?: arrayListOf()
        val clientTypes = intent.getStringArrayListExtra(EXTRA_CLIENTS_TYPES) ?: arrayListOf()
        val clientTimes = intent.getStringArrayListExtra(EXTRA_CLIENTS_TIMES) ?: arrayListOf()
        
        // Peupler le LinearLayout directement
        populateClientsLayout(clientNames, clientTypes, clientTimes)
    }
    
    private fun populateClientsLayout(clientNames: List<String>, clientTypes: List<String>, clientTimes: List<String>) {
        val clientsLayout = binding.clientsLinearLayout
        clientsLayout.removeAllViews()
        
        clientNames.forEachIndexed { index, name ->
            val clientView = LayoutInflater.from(this).inflate(R.layout.item_client_detail, clientsLayout, false)
            
            val clientNameTextView = clientView.findViewById<TextView>(R.id.clientNameTextView)
            val clientTypeTextView = clientView.findViewById<TextView>(R.id.clientTypeTextView)
            val departureTimeTextView = clientView.findViewById<TextView>(R.id.departureTimeTextView)
            
            // Nom du client
            clientNameTextView.text = name
            
            // Type de caisse avec couleur
            val type = if (index < clientTypes.size) clientTypes[index] else "standard"
            clientTypeTextView.text = type.uppercase()
            val textColor = getClientTypeTextColor(type)
            clientTypeTextView.setTextColor(textColor)
            // Le nom du client garde la couleur par défaut (pas de couleur spéciale)
            
            // Heure de départ
            val time = if (index < clientTimes.size) clientTimes[index] else "Non défini"
            if (time != "Non défini") {
                departureTimeTextView.text = "🕐 $time"
            } else {
                departureTimeTextView.text = "🕐 Non défini"
            }
            
            clientsLayout.addView(clientView)
        }
    }
    
    private fun getClientTypeTextColor(type: String): Int {
        return when (type.uppercase()) {
            "BLL" -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            "EUROPOOL" -> ContextCompat.getColor(this, android.R.color.holo_purple)
            else -> ContextCompat.getColor(this, R.color.md_theme_light_onSurfaceVariant)
        }
    }
    
    private fun setSupplierColors(supplier: String) {
        val supplierColors = when (supplier.lowercase()) {
            "anecoop" -> Pair(
                ContextCompat.getColor(this, android.R.color.holo_blue_dark),
                ContextCompat.getColor(this, android.R.color.white)
            )
            "solagora" -> Pair(
                ContextCompat.getColor(this, android.R.color.holo_green_dark),
                ContextCompat.getColor(this, android.R.color.white)
            )
            else -> Pair(
                ContextCompat.getColor(this, android.R.color.darker_gray),
                ContextCompat.getColor(this, android.R.color.white)
            )
        }
        
        binding.supplierBadge.setBackgroundColor(supplierColors.first)
        binding.supplierBadge.setTextColor(supplierColors.second)
    }
    
    private fun copyToClipboard(text: String, message: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Product Info", text)
        clipboard.setPrimaryClip(clip)
        
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    /**
     * Charge les vraies données de palmarès depuis Firebase
     */
    private fun loadRealPalmaresData() {
        val productName = intent.getStringExtra(EXTRA_PRODUCT_NAME) ?: return
        val supplier = intent.getStringExtra(EXTRA_SUPPLIER) ?: return
        val productCode = intent.getStringExtra(EXTRA_PRODUCT_CODE) ?: ""
        
        // Récupérer la semaine/année sélectionnée depuis les extras
        val selectedYear = intent.getIntExtra(EXTRA_SELECTED_YEAR, 0).takeIf { it > 0 }
        val selectedWeek = intent.getIntExtra(EXTRA_SELECTED_WEEK, 0).takeIf { it > 0 }
        
        android.util.Log.d("ProductDetail", "🔍 UI_PALMARES_START: Chargement palmarès réel pour '$productName' code='$productCode' ($supplier) - semaine sélectionnée: $selectedYear-S$selectedWeek")
        
        // Démarrer l'animation de chargement
        startLoadingAnimation()
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("ProductDetail", "🔍 UI_PALMARES_FIREBASE_CALL: Appel Firebase pour '$productName' avec code='$productCode' et semaine $selectedYear-S$selectedWeek")
                val palmares = firebaseRepository.getProductHistorySinceOctober(productName, supplier, productCode, selectedYear, selectedWeek)
                
                android.util.Log.d("ProductDetail", "📊 UI_PALMARES_RECEIVED: Palmarès récupéré - ${palmares.consecutiveWeeks} consécutives, ${palmares.totalReferences} total, ${palmares.percentage}%, ${palmares.referencedWeeks.size} semaines dans la liste")
                
                // Arrêter l'animation et mettre à jour avec les vraies données
                stopLoadingAnimation()
                android.util.Log.d("ProductDetail", "🎨 UI_PALMARES_UPDATE: Mise à jour affichage avec consécutif=${palmares.consecutiveWeeks}, total=${palmares.totalReferences}, pourcentage=${palmares.percentage}%")
                updatePalmaresDisplay(palmares.consecutiveWeeks, palmares.totalReferences, palmares.percentage)
                
            } catch (e: Exception) {
                android.util.Log.e("ProductDetail", "🚨 UI_PALMARES_ERROR: Erreur chargement palmarès: ${e.message}")
                android.util.Log.e("ProductDetail", "🚨 UI_PALMARES_STACK: ${e.stackTrace.joinToString("\n")}")
                // Arrêter l'animation même en cas d'erreur
                stopLoadingAnimation()
            }
        }
    }
    
    /**
     * Met à jour l'affichage du palmarès avec les vraies données
     */
    private fun updatePalmaresDisplay(consecutiveWeeks: Int, totalReferences: Int, percentage: Int = 0) {
        android.util.Log.d("ProductDetail", "🎨 UI_UPDATE_START: Début mise à jour affichage - consécutif=$consecutiveWeeks, total=$totalReferences, pourcentage=$percentage%")
        
        // Mettre à jour les semaines consécutives
        val consecutiveText = "Consécutif $consecutiveWeeks semaines"
        val consecutiveSpannable = android.text.SpannableString(consecutiveText)
        val consecutiveStart = consecutiveText.indexOf("$consecutiveWeeks")
        val consecutiveEnd = consecutiveStart + "$consecutiveWeeks semaines".length
        
        android.util.Log.d("ProductDetail", "🎨 UI_UPDATE_CONSECUTIVE: Texte='$consecutiveText', span=$consecutiveStart-$consecutiveEnd")
        
        consecutiveSpannable.setSpan(
            android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)),
            consecutiveStart,
            consecutiveEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        consecutiveSpannable.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            consecutiveStart,
            consecutiveEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        consecutiveSpannable.setSpan(
            android.text.style.RelativeSizeSpan(1.3f),
            consecutiveStart,
            consecutiveEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.consecutiveWeeks.text = consecutiveSpannable
        android.util.Log.d("ProductDetail", "🎨 UI_UPDATE_CONSECUTIVE_SET: TextView mis à jour avec '$consecutiveText'")
        
        // Mettre à jour le total des références (sans pourcentage)
        val totalText = "Depuis le 01 Octobre $totalReferences fois"
        val totalSpannable = android.text.SpannableString(totalText)
        val totalStart = totalText.indexOf("$totalReferences fois")
        val totalEnd = totalStart + "$totalReferences fois".length
        
        android.util.Log.d("ProductDetail", "🎨 UI_UPDATE_TOTAL: Texte='$totalText', span=$totalStart-$totalEnd, pourcentage=$percentage%")
        
        // Colorer "XX fois" en bleu
        totalSpannable.setSpan(
            android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)),
            totalStart,
            totalEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        totalSpannable.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            totalStart,
            totalEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        totalSpannable.setSpan(
            android.text.style.RelativeSizeSpan(1.3f),
            totalStart,
            totalEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // Mettre à jour le pourcentage séparément avec couleur conditionnelle
        updatePercentageDisplay(percentage)
        binding.totalReferences.text = totalSpannable
        android.util.Log.d("ProductDetail", "🎨 UI_UPDATE_TOTAL_SET: TextView mis à jour avec '$totalText'")
        
        android.util.Log.d("ProductDetail", "✅ UI_UPDATE_COMPLETE: Affichage mis à jour avec les vraies données - consécutif=$consecutiveWeeks, total=$totalReferences")
    }
    
    /**
     * Démarre l'animation de chargement avec des chiffres qui défilent
     */
    private fun startLoadingAnimation() {
        if (isAnimating) return
        isAnimating = true
        
        android.util.Log.d("ProductDetail", "🎬 ANIMATION_START: Démarrage animation de chargement")
        
        loadingAnimationJob = lifecycleScope.launch {
            val consecutiveNumbers = listOf(3, 7, 2, 9, 5, 8, 4, 6, 1, 12, 15, 8, 3, 11)
            val totalNumbers = listOf(8, 15, 23, 7, 19, 12, 31, 5, 18, 26, 14, 21, 9, 35)
            val percentageNumbers = listOf(25, 45, 67, 32, 58, 71, 89, 43, 76, 52, 38, 84, 29, 65)
            
            var index = 0
            while (isAnimating) {
                try {
                    val consecutive = consecutiveNumbers[index % consecutiveNumbers.size]
                    val total = totalNumbers[index % totalNumbers.size]
                    val percentage = percentageNumbers[index % percentageNumbers.size]
                    
                    // Mettre à jour l'affichage avec les chiffres animés
                    runOnUiThread {
                        updateConsecutiveDisplay(consecutive, true)
                        updateTotalDisplay(total, true)
                        updatePercentageDisplay(percentage)
                    }
                    
                    kotlinx.coroutines.delay(150) // Animation rapide
                    index++
                } catch (e: Exception) {
                    break
                }
            }
        }
    }
    
    /**
     * Arrête l'animation de chargement
     */
    private fun stopLoadingAnimation() {
        android.util.Log.d("ProductDetail", "🛑 ANIMATION_STOP: Arrêt animation de chargement")
        isAnimating = false
        loadingAnimationJob?.cancel()
        loadingAnimationJob = null
    }
    
    /**
     * Met à jour seulement l'affichage des semaines consécutives
     */
    private fun updateConsecutiveDisplay(consecutiveWeeks: Int, isLoading: Boolean = false) {
        val consecutiveText = "Consécutif $consecutiveWeeks semaines"
        val consecutiveSpannable = android.text.SpannableString(consecutiveText)
        val consecutiveStart = consecutiveText.indexOf("$consecutiveWeeks")
        val consecutiveEnd = consecutiveStart + "$consecutiveWeeks semaines".length
        
        consecutiveSpannable.setSpan(
            android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)),
            consecutiveStart,
            consecutiveEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        consecutiveSpannable.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            consecutiveStart,
            consecutiveEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        consecutiveSpannable.setSpan(
            android.text.style.RelativeSizeSpan(1.3f),
            consecutiveStart,
            consecutiveEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.consecutiveWeeks.text = consecutiveSpannable
    }
    
    /**
     * Met à jour seulement l'affichage du total
     */
    private fun updateTotalDisplay(totalReferences: Int, isLoading: Boolean = false) {
        val totalText = "Depuis le 01 Octobre $totalReferences fois"
        val totalSpannable = android.text.SpannableString(totalText)
        val totalStart = totalText.indexOf("$totalReferences fois")
        val totalEnd = totalStart + "$totalReferences fois".length
        
        totalSpannable.setSpan(
            android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)),
            totalStart,
            totalEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        totalSpannable.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            totalStart,
            totalEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        totalSpannable.setSpan(
            android.text.style.RelativeSizeSpan(1.3f),
            totalStart,
            totalEnd,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.totalReferences.text = totalSpannable
    }
    
    /**
     * Met à jour l'affichage du pourcentage avec une couleur conditionnelle
     */
    private fun updatePercentageDisplay(percentage: Int) {
        val percentageText = "$percentage% des semaines"
        binding.percentageWeeks.text = percentageText
        
        // Couleur conditionnelle selon le pourcentage
        val color = when {
            percentage >= 80 -> ContextCompat.getColor(this, android.R.color.holo_green_dark) // Vert foncé: excellent
            percentage >= 60 -> ContextCompat.getColor(this, android.R.color.holo_green_light) // Vert clair: très bon
            percentage >= 40 -> ContextCompat.getColor(this, android.R.color.holo_orange_dark) // Orange: moyen
            percentage >= 20 -> ContextCompat.getColor(this, android.R.color.holo_red_light) // Rouge clair: faible
            else -> ContextCompat.getColor(this, android.R.color.holo_red_dark) // Rouge foncé: très faible
        }
        
        binding.percentageWeeks.setTextColor(color)
        
        android.util.Log.d("ProductDetail", "🎨 UI_PERCENTAGE: $percentage% affiché avec couleur ${when {
            percentage >= 80 -> "VERT_FONCE"
            percentage >= 60 -> "VERT_CLAIR" 
            percentage >= 40 -> "ORANGE"
            percentage >= 20 -> "ROUGE_CLAIR"
            else -> "ROUGE_FONCE"
        }}")
    }
    
    /**
     * Calcule la semaine ISO courante
     */
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
}