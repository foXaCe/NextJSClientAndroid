package com.nextjsclient.android.ui.overview

import android.animation.ValueAnimator
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.nextjsclient.android.R
import com.nextjsclient.android.data.models.ScamarkStats

/**
 * Helper class pour gérer l'interface moderne du fragment Overview
 */
class ModernOverviewHelper(private val fragment: OverviewFragment) {
    /**
     * Met à jour une card fournisseur moderne
     */
    fun updateSupplierCard(supplier: String, stats: ScamarkStats, isAnecoop: Boolean = true) {
        val binding = fragment.binding
        
        val cardId = if (isAnecoop) R.id.anecoopModernCard else R.id.solagoraModernCard
        val card = binding.root.findViewById<View>(cardId) ?: return
        
        
        // Vérifier si ce fournisseur doit être visible selon les préférences
        val supplierPreferences = fragment.supplierPreferences
        val shouldShowCard = when (supplier.lowercase()) {
            "anecoop" -> supplierPreferences.isAnecoopEnabled
            "solagora" -> supplierPreferences.isSolagoraEnabled
            else -> true
        }
        
        
        if (!shouldShowCard) {
            card.visibility = View.GONE
            return
        }
        
        // Afficher la card si elle était cachée
        card.visibility = View.VISIBLE
        
        // Configurer les couleurs et thème de la card
        configureSupplierCardTheme(card, isAnecoop)
        
        // Mettre à jour les valeurs
        updateCardValues(card, stats)
        
        // Configurer les actions
        setupCardActions(card, supplier, stats)
    }

    /**
     * Configure le thème visuel de la card (couleurs, logos, etc.)
     */
    private fun configureSupplierCardTheme(card: View, isAnecoop: Boolean) {
        val context = fragment.requireContext()
        
        // Header background
        val header = card.findViewById<View>(R.id.supplierHeader)
        header?.background = ContextCompat.getDrawable(context, 
            if (isAnecoop) R.drawable.card_header_gradient_anecoop 
            else R.drawable.card_header_gradient_solagora
        )
        
        // Logo
        val logo = card.findViewById<ImageView>(R.id.supplierLogo)
        logo?.let {
            it.setImageResource(if (isAnecoop) R.drawable.ic_anecoop else R.drawable.ic_solagora)
            it.setColorFilter(ContextCompat.getColor(context,
                if (isAnecoop) R.color.anecoop_primary else R.color.solagora_primary
            ))
        }
        
        // Nom
        val name = card.findViewById<TextView>(R.id.supplierName)
        name?.text = if (isAnecoop) "ANECOOP" else "SOLAGORA"
        
        // L'affichage de la semaine a été déplacé dans l'en-tête principal
        // Donc rien à faire ici
        
        // Les éléments sont maintenant masqués directement dans le layout XML
    }

    /**
     * Met à jour les valeurs numériques dans la card
     */
    private fun updateCardValues(card: View, stats: ScamarkStats) {
        // Total produits (stat principale)
        val totalValue = card.findViewById<TextView>(R.id.totalProductsValue)
        totalValue?.let { animateNumber(it, stats.totalProducts) }
        
        // Entrants
        val inValue = card.findViewById<TextView>(R.id.productsInValue)
        inValue?.let { animateNumber(it, stats.productsIn) }
        
        // Sortants
        val outValue = card.findViewById<TextView>(R.id.productsOutValue)
        outValue?.let { animateNumber(it, stats.productsOut) }
        
        // Promotions (conditionnelle)
        val promoCard = card.findViewById<MaterialCardView>(R.id.productsPromoCard)
        val promoValue = card.findViewById<TextView>(R.id.productsPromoValue)
        
        if (stats.totalPromos > 0) {
            promoCard?.visibility = View.VISIBLE
            promoValue?.let { animateNumber(it, stats.totalPromos) }
        } else {
            promoCard?.visibility = View.GONE
        }
        
        // Indicateur de tendance
        updateTrendIndicator(card, stats)
    }

    /**
     * Met à jour l'indicateur de tendance dans la stat principale
     */
    private fun updateTrendIndicator(card: View, stats: ScamarkStats) {
        val trendIcon = card.findViewById<ImageView>(R.id.trendIndicatorIcon)
        val trendText = card.findViewById<TextView>(R.id.trendIndicatorText)
        
        val netChange = stats.productsIn - stats.productsOut
        val context = fragment.requireContext()
        
        trendIcon?.let { icon ->
            if (netChange >= 0) {
                icon.setImageResource(R.drawable.ic_trending_up_24)
                icon.setColorFilter(ContextCompat.getColor(context, R.color.stat_in_color))
            } else {
                icon.setImageResource(R.drawable.ic_trending_down_24)
                icon.setColorFilter(ContextCompat.getColor(context, R.color.stat_out_color))
            }
        }
        
        trendText?.let { text ->
            val sign = if (netChange >= 0) "+" else ""
            text.text = "${sign}${netChange} vs S-1"
            text.setTextColor(ContextCompat.getColor(context,
                if (netChange >= 0) R.color.stat_in_color else R.color.stat_out_color
            ))
        }
    }

    /**
     * Configure les actions des boutons dans la card
     */
    private fun setupCardActions(card: View, supplier: String, stats: ScamarkStats) {
        // Les boutons sont maintenant masqués directement dans le layout XML
        
        // Card principale cliquable (produits actifs) - navigation vers fournisseur sans filtre
        val totalProductsValue = card.findViewById<TextView>(R.id.totalProductsValue)
        val mainStatsArea = totalProductsValue?.parent as? LinearLayout
        mainStatsArea?.setOnClickListener {
            if (stats.totalProducts > 0) {
                fragment.navigateToSupplierWithoutFilter(supplier)
            }
        }
        
        // Cards cliquables pour les stats
        card.findViewById<MaterialCardView>(R.id.productsInCard)?.setOnClickListener {
            if (stats.productsIn > 0) {
                fragment.navigateToSupplierWithEntrantsFilterFromCard(supplier, "entrants")
            }
        }
        
        card.findViewById<MaterialCardView>(R.id.productsOutCard)?.setOnClickListener {
            if (stats.productsOut > 0) {
                fragment.navigateToSupplierWithSortantsFilter(supplier, "sortants")
            }
        }
        
        // Carte promo cliquable
        card.findViewById<MaterialCardView>(R.id.productsPromoCard)?.setOnClickListener {
            if (stats.totalPromos > 0) {
                fragment.navigateToSupplierWithPromoFilterFromCard(supplier, "promo")
            }
        }
    }

    /**
     * Anime un changement de nombre dans un TextView
     */
    private fun animateNumber(textView: TextView, targetValue: Int) {
        val currentText = textView.text.toString()
        val currentValue = currentText.toIntOrNull() ?: 0
        
        if (currentValue == targetValue) {
            textView.text = targetValue.toString()
            return
        }
        
        ValueAnimator.ofInt(currentValue, targetValue).apply {
            duration = 600
            addUpdateListener { animation ->
                textView.text = animation.animatedValue.toString()
            }
            start()
        }
    }

    /**
     * Met à jour l'affichage de la semaine dans le hero
     */
    fun updateWeekDisplay(weekText: String) {
        val binding = fragment.binding
        binding.root.findViewById<TextView>(R.id.currentWeekInfo)?.text = weekText
    }
    
    /**
     * Récupère le numéro de semaine ISO courante
     */
    private fun getCurrentISOWeek(): Int {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.WEEK_OF_YEAR)
    }
}