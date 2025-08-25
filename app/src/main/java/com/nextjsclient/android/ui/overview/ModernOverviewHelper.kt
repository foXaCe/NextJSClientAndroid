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
        val startTime = System.currentTimeMillis()
        android.util.Log.d("ModernOverviewHelper", "🎨 Updating ${supplier.uppercase()} card...")
        
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
        
        android.util.Log.d("ModernOverviewHelper", "✅ ${supplier.uppercase()} card updated in ${System.currentTimeMillis() - startTime}ms")
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
        val startTime = System.currentTimeMillis()
        // Détecter si c'est la première fois qu'on charge ces valeurs
        val totalValue = card.findViewById<TextView>(R.id.totalProductsValue)
        val currentText = totalValue?.text.toString() ?: ""
        val isFirstLoad = currentText == "--" || currentText.isEmpty()
        
        android.util.Log.d("ModernOverviewHelper", "📊 Updating card values - Current text: '$currentText', First load: $isFirstLoad")
        
        if (isFirstLoad) {
            android.util.Log.d("ModernOverviewHelper", "🎭 Starting loading animations...")
            // Première fois : démarrer avec l'animation de placeholder puis le comptage
            startLoadingAnimations(card)
            
            // Démarrer les animations de comptage après un délai pour l'effet
            card.postDelayed({
                if (fragment.isAdded && fragment.context != null) {
                    android.util.Log.d("ModernOverviewHelper", "🎬 Finishing loading animations...")
                    finishLoadingAnimations(card, stats)
                }
            }, 800L) // Délai pour laisser l'animation de placeholder
        } else {
            android.util.Log.d("ModernOverviewHelper", "🔄 Animated value update...")
            // Toujours utiliser l'animation pour le feedback visuel, même lors des mises à jour
            updateValuesWithAnimation(card, stats)
        }
        
        android.util.Log.d("ModernOverviewHelper", "✅ Card values setup in ${System.currentTimeMillis() - startTime}ms")
    }
    
    private fun startLoadingAnimations(card: View) {
        // Total produits (stat principale)
        val totalValue = card.findViewById<TextView>(R.id.totalProductsValue)
        totalValue?.let { com.nextjsclient.android.utils.CountUpAnimator.animateLoadingPlaceholder(it) }
        
        // Entrants
        val inValue = card.findViewById<TextView>(R.id.productsInValue)
        inValue?.let { com.nextjsclient.android.utils.CountUpAnimator.animateLoadingPlaceholder(it) }
        
        // Sortants
        val outValue = card.findViewById<TextView>(R.id.productsOutValue)
        outValue?.let { com.nextjsclient.android.utils.CountUpAnimator.animateLoadingPlaceholder(it) }
        
        // Promotions
        val promoValue = card.findViewById<TextView>(R.id.productsPromoValue)
        promoValue?.let { com.nextjsclient.android.utils.CountUpAnimator.animateLoadingPlaceholder(it) }
    }
    
    private fun finishLoadingAnimations(card: View, stats: ScamarkStats) {
        // Total produits avec slide-in et pulsation
        val totalValue = card.findViewById<TextView>(R.id.totalProductsValue)
        totalValue?.let { 
            com.nextjsclient.android.utils.CountUpAnimator.stopPlaceholderAndCountUp(
                it, stats.totalProducts, 1000L, withPulse = true
            )
        }
        
        // Animation en cascade pour les autres valeurs
        card.postDelayed({
            if (fragment.isAdded && fragment.context != null) {
                val inValue = card.findViewById<TextView>(R.id.productsInValue)
                inValue?.let { 
                    com.nextjsclient.android.utils.CountUpAnimator.stopPlaceholderAndCountUp(
                        it, stats.productsIn, 800L, withPulse = false
                    )
                }
            }
        }, 200L)
        
        card.postDelayed({
            if (fragment.isAdded && fragment.context != null) {
                val outValue = card.findViewById<TextView>(R.id.productsOutValue)
                outValue?.let { 
                    com.nextjsclient.android.utils.CountUpAnimator.stopPlaceholderAndCountUp(
                        it, stats.productsOut, 800L, withPulse = false
                    )
                }
            }
        }, 400L)
        
        // Promotions (conditionnelle)
        val promoCard = card.findViewById<MaterialCardView>(R.id.productsPromoCard)
        val promoValue = card.findViewById<TextView>(R.id.productsPromoValue)
        
        if (stats.totalPromos > 0) {
            promoCard?.visibility = View.VISIBLE
            card.postDelayed({
                if (fragment.isAdded && fragment.context != null) {
                    promoValue?.let { 
                        com.nextjsclient.android.utils.CountUpAnimator.stopPlaceholderAndCountUp(
                            it, stats.totalPromos, 600L, withPulse = false
                        )
                    }
                }
            }, 600L)
        } else {
            promoCard?.visibility = View.GONE
        }
        
        // Indicateur de tendance avec délai pour l'effet final
        card.postDelayed({
            // Vérifier que le fragment est toujours attaché avant de continuer
            if (fragment.isAdded && fragment.context != null) {
                updateTrendIndicator(card, stats)
            }
        }, 800L)
    }
    
    private fun updateValuesWithAnimation(card: View, stats: ScamarkStats) {
        // Total produits avec animation plus rapide
        val totalValue = card.findViewById<TextView>(R.id.totalProductsValue)
        totalValue?.let { 
            com.nextjsclient.android.utils.CountUpAnimator.animateCountUp(
                it, stats.totalProducts, 500L
            )
        }
        
        // Animation en cascade rapide pour les autres valeurs
        card.postDelayed({
            if (fragment.isAdded && fragment.context != null) {
                val inValue = card.findViewById<TextView>(R.id.productsInValue)
                inValue?.let { 
                    com.nextjsclient.android.utils.CountUpAnimator.animateCountUp(
                        it, stats.productsIn, 400L
                    )
                }
            }
        }, 100L)
        
        card.postDelayed({
            if (fragment.isAdded && fragment.context != null) {
                val outValue = card.findViewById<TextView>(R.id.productsOutValue)
                outValue?.let { 
                    com.nextjsclient.android.utils.CountUpAnimator.animateCountUp(
                        it, stats.productsOut, 400L
                    )
                }
            }
        }, 200L)
        
        // Promotions (conditionnelle)
        val promoCard = card.findViewById<MaterialCardView>(R.id.productsPromoCard)
        val promoValue = card.findViewById<TextView>(R.id.productsPromoValue)
        
        if (stats.totalPromos > 0) {
            promoCard?.visibility = View.VISIBLE
            card.postDelayed({
                if (fragment.isAdded && fragment.context != null) {
                    promoValue?.let { 
                        com.nextjsclient.android.utils.CountUpAnimator.animateCountUp(
                            it, stats.totalPromos, 300L
                        )
                    }
                }
            }, 300L)
        } else {
            promoCard?.visibility = View.GONE
        }
        
        // Indicateur de tendance avec délai
        card.postDelayed({
            if (fragment.isAdded && fragment.context != null) {
                updateTrendIndicator(card, stats)
            }
        }, 400L)
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