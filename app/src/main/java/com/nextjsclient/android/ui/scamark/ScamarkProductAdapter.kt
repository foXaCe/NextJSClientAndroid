package com.nextjsclient.android.ui.scamark

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.flexbox.FlexboxLayout
import com.nextjsclient.android.R
import com.nextjsclient.android.data.models.ScamarkProduct
import com.nextjsclient.android.data.models.ClientDecision
import com.nextjsclient.android.ui.scamark.ProductStatus
import com.nextjsclient.android.utils.CategoryTranslator
import java.text.DecimalFormat

class ScamarkProductAdapter(
    private val onItemClick: (ScamarkProduct) -> Unit,
    private val onEditClick: (ScamarkProduct) -> Unit,
    private val onDeleteClick: (ScamarkProduct) -> Unit,
    private val getProductStatus: ((String) -> ProductStatus)? = null
) : ListAdapter<ScamarkProduct, ScamarkProductAdapter.ScamarkViewHolder>(ScamarkDiffCallback) {
    
    // Variable pour savoir si nous affichons des produits sortants
    var isShowingSortants: Boolean = false
    
    // Variable pour savoir si nous affichons des produits entrants
    var isShowingEntrants: Boolean = false
    
    // Track des éléments qui ont été animés pour éviter de réanimer
    private var shouldAnimateNewItems = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScamarkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scamark_product, parent, false)
        return ScamarkViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScamarkViewHolder, position: Int) {
        val product = getItem(position)
        val isLastItem = position == itemCount - 1
        holder.bind(product, isLastItem)
        
        // TOUJOURS s'assurer que l'élément est dans un état normal
        holder.itemView.clearAnimation()
        holder.itemView.alpha = 1f
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
        holder.itemView.translationY = 0f
        
        // Animation fade très légère et fluide pour les nouveaux éléments
        if (shouldAnimateNewItems) {
            // Animation plus subtile avec délais réduits
            holder.itemView.alpha = 0.6f
            holder.itemView.translationY = 20f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200) // Durée fixe plus courte
                .setStartDelay(position * 10L) // Délai réduit pour plus de fluidité
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    // Désactiver après le dernier élément visible (limite à 10 pour éviter trop de délais)
                    if (position == itemCount - 1 || position >= 9) {
                        shouldAnimateNewItems = false
                    }
                }
                .start()
        }
    }
    
    // Fonction pour activer les animations lors d'un changement de fournisseur
    fun enableEntranceAnimations() {
        shouldAnimateNewItems = true
    }

    inner class ScamarkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val productNameTextView: TextView = itemView.findViewById(R.id.productNameTextView)
        private val supplierTextView: TextView = itemView.findViewById(R.id.supplierTextView)
        private val priceRetenuTextView: TextView = itemView.findViewById(R.id.priceRetenuTextView)
        private val priceOffertTextView: TextView = itemView.findViewById(R.id.priceOffertTextView)
        private val promoIndicator: View = itemView.findViewById(R.id.promoIndicator)
        private val clientsFlexboxLayout: FlexboxLayout = itemView.findViewById(R.id.clientsFlexboxLayout)
        private val clientsCountTextView: TextView = itemView.findViewById(R.id.clientsCountTextView)
        private val categoryTextView: TextView = itemView.findViewById(R.id.categoryTextView)
        private val brandsTextView: TextView = itemView.findViewById(R.id.brandsTextView)

        private val priceFormat = DecimalFormat("#,##0.00")

        fun bind(product: ScamarkProduct, isLastItem: Boolean = false) {
            // Nom du produit avec indication promo et coloration selon statut
            val productText = if (product.isPromo) {
                "🔥 ${product.productName}"
            } else {
                product.productName
            }
            
            // Appliquer la couleur selon le statut du produit (entrant/sortant)
            val spannable = SpannableString(productText)
            
            // Si nous affichons des sortants, tous les produits sont en rouge
            if (isShowingSortants) {
                val color = ContextCompat.getColor(itemView.context, R.color.stat_out_color)
                spannable.setSpan(ForegroundColorSpan(color), 0, productText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                productNameTextView.text = spannable
            } else if (isShowingEntrants) {
                // Si nous affichons des entrants, tous les produits sont en vert
                val color = ContextCompat.getColor(itemView.context, R.color.stat_in_color)
                spannable.setSpan(ForegroundColorSpan(color), 0, productText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                productNameTextView.text = spannable
            } else if (getProductStatus != null) {
                // Sinon, utiliser la fonction de statut si disponible
                val status = getProductStatus.invoke(product.productName)
                @Suppress("UNUSED_VARIABLE")
                val colorType = when (status) {
                    ProductStatus.ENTRANT -> "ENTRANT (vert)"
                    ProductStatus.SORTANT -> "SORTANT (rouge)"
                    ProductStatus.NEUTRAL -> "NEUTRAL"
                }
                val color = when (status) {
                    ProductStatus.ENTRANT -> ContextCompat.getColor(itemView.context, R.color.stat_in_color) // Vert
                    ProductStatus.SORTANT -> ContextCompat.getColor(itemView.context, R.color.stat_out_color) // Rouge
                    ProductStatus.NEUTRAL -> productNameTextView.currentTextColor // Couleur normale
                }
                spannable.setSpan(ForegroundColorSpan(color), 0, productText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                productNameTextView.text = spannable
            } else {
                // Utiliser le texte normal
                productNameTextView.text = productText
            }
            
            // Fournisseur avec couleur de badge
            supplierTextView.text = product.supplier.uppercase()
            
            // Appliquer les couleurs selon le fournisseur
            setSupplierColors(product.supplier, supplierTextView)
            
            // Prix avec labels et couleurs conditionnelles - afficher même si 0
            if (product.prixRetenu >= 0) {
                val prixRetenuStr = "${priceFormat.format(product.prixRetenu)}€"
                val retainedPrefix = itemView.context.getString(R.string.price_retained_prefix)
                val prixText = if (product.isPromo) {
                    "$retainedPrefix $prixRetenuStr 🔥"
                } else {
                    "$retainedPrefix $prixRetenuStr"
                }
                priceRetenuTextView.text = prixText
                priceRetenuTextView.visibility = View.VISIBLE
            } else {
                priceRetenuTextView.visibility = View.GONE
            }
            
            // Afficher prix offert même si 0
            if (product.prixOffert >= 0) {
                val prixOffertStr = "${priceFormat.format(product.prixOffert)}€"
                val offeredPrefix = itemView.context.getString(R.string.price_offered_prefix)
                val prixText = if (product.isPromo) {
                    "$offeredPrefix $prixOffertStr ⚡"
                } else {
                    "$offeredPrefix $prixOffertStr"
                }
                priceOffertTextView.text = prixText
                priceOffertTextView.visibility = View.VISIBLE
            } else {
                priceOffertTextView.visibility = View.GONE
            }
            
            // Couleur conditionnelle selon la comparaison des prix
            when {
                product.prixRetenu >= 0 && product.prixOffert >= 0 -> {
                    if (product.prixRetenu < product.prixOffert) {
                        // Prix retenu plus faible = rouge
                        priceRetenuTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark))
                        val typedValue = android.util.TypedValue()
                        itemView.context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                        val defaultColor = ContextCompat.getColor(itemView.context, typedValue.resourceId)
                        priceOffertTextView.setTextColor(defaultColor)
                    } else if (product.prixRetenu > product.prixOffert) {
                        // Prix retenu plus élevé = vert
                        priceRetenuTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
                        val typedValue = android.util.TypedValue()
                        itemView.context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                        val defaultColor = ContextCompat.getColor(itemView.context, typedValue.resourceId)
                        priceOffertTextView.setTextColor(defaultColor)
                    } else {
                        // Prix égaux = couleur normale qui s'adapte au thème
                        val typedValue = android.util.TypedValue()
                        itemView.context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                        val defaultColor = ContextCompat.getColor(itemView.context, typedValue.resourceId)
                        priceRetenuTextView.setTextColor(defaultColor)
                        priceOffertTextView.setTextColor(defaultColor)
                    }
                }
                else -> {
                    // Un des prix non défini = couleur par défaut qui s'adapte au thème
                    val typedValue = android.util.TypedValue()
                    itemView.context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                    val defaultColor = ContextCompat.getColor(itemView.context, typedValue.resourceId)
                    priceRetenuTextView.setTextColor(defaultColor)
                    priceOffertTextView.setTextColor(defaultColor)
                }
            }
            
            // Indicateur promo et style de carte
            promoIndicator.visibility = if (product.isPromo) View.VISIBLE else View.GONE
            
            // Appliquer un style spécial pour les cartes en promotion
            if (product.isPromo) {
                // Bordure dorée pour les promotions
                (itemView as com.google.android.material.card.MaterialCardView).apply {
                    strokeWidth = 4
                    strokeColor = ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                    cardElevation = 4f
                }
            } else {
                // Style normal
                (itemView as com.google.android.material.card.MaterialCardView).apply {
                    strokeWidth = 2
                    strokeColor = ContextCompat.getColor(itemView.context, R.color.md_theme_light_outline)
                    cardElevation = 2f
                }
            }
            
            // Nombre de clients par type avec couleurs
            val bllCount = product.decisions.count { decision ->
                decision.clientInfo?.typeCaisse?.uppercase() == "BLL"
            }
            val europoolCount = product.decisions.count { decision ->
                decision.clientInfo?.typeCaisse?.uppercase() == "EUROPOOL"
            }
            val totalCount = product.totalScas
            
            // Créer le texte avec couleurs
            val text = itemView.context.getString(R.string.sca_count_format, bllCount, europoolCount, totalCount)
            val spannableString = SpannableString(text)
            
            // Couleur orange pour BLL
            val bllStart = text.indexOf("$bllCount BLL")
            val bllEnd = bllStart + "$bllCount BLL".length
            if (bllStart >= 0) {
                spannableString.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)),
                    bllStart,
                    bllEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            // Couleur violette pour EUROPOOL
            val europoolStart = text.indexOf("$europoolCount EUROPOOL")
            val europoolEnd = europoolStart + "$europoolCount EUROPOOL".length
            if (europoolStart >= 0) {
                spannableString.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(itemView.context, android.R.color.holo_purple)),
                    europoolStart,
                    europoolEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            clientsCountTextView.text = spannableString
            
            // Catégorie et marques - masquer si non défini
            val category = product.articleInfo?.categorie
            if (!category.isNullOrBlank()) {
                val translatedCategory = CategoryTranslator.translateCategory(itemView.context, category)
                categoryTextView.text = translatedCategory
                categoryTextView.visibility = View.VISIBLE
            } else {
                categoryTextView.visibility = View.GONE
            }
            
            val brandsInfo = buildString {
                product.articleInfo?.marque?.let { append("$it ") }
                product.articleInfo?.origine?.let { append("• $it") }
            }
            if (brandsInfo.isNotBlank()) {
                brandsTextView.text = brandsInfo
                brandsTextView.visibility = View.VISIBLE
            } else {
                brandsTextView.visibility = View.GONE
            }
            
            // Ajouter les cartes clients au FlexboxLayout
            populateClientsFlexbox(product.decisions)
            
            
            // Click listener
            itemView.setOnClickListener {
                onItemClick(product)
            }
            
            // Ajuster la marge pour le dernier élément
            val layoutParams = itemView.layoutParams as ViewGroup.MarginLayoutParams
            if (isLastItem) {
                // Ajouter une marge inférieure importante pour le dernier élément
                layoutParams.bottomMargin = itemView.context.resources.displayMetrics.density.toInt() * 120 // 120dp
            } else {
                // Marge normale pour les autres éléments
                layoutParams.bottomMargin = itemView.context.resources.displayMetrics.density.toInt() * 8 // 8dp
            }
            itemView.layoutParams = layoutParams
        }
        
        private fun setSupplierColors(supplier: String, textView: TextView) {
            when (supplier.lowercase()) {
                "anecoop" -> {
                    // Vert pour Anecoop
                    textView.background = ContextCompat.getDrawable(itemView.context, R.drawable.supplier_badge_anecoop)
                    textView.setTextColor(ContextCompat.getColor(itemView.context, R.color.anecoop_on_primary))
                }
                "solagora" -> {
                    // Orange pour Solagora
                    textView.background = ContextCompat.getDrawable(itemView.context, R.drawable.supplier_badge_solagora)
                    textView.setTextColor(ContextCompat.getColor(itemView.context, R.color.solagora_on_primary))
                }
                else -> {
                    // Couleur par défaut
                    textView.background = ContextCompat.getDrawable(itemView.context, R.drawable.supplier_badge)
                    textView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                }
            }
        }
        
        /**
         * Remplit le FlexboxLayout avec les cartes clients
         */
        private fun populateClientsFlexbox(decisions: List<ClientDecision>) {
            // Vider le layout
            clientsFlexboxLayout.removeAllViews()
            
            decisions.forEach { decision ->
                // Créer la vue pour chaque client
                val clientView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.item_client_decision, clientsFlexboxLayout, false)
                
                // Configurer la vue
                val clientNameTextView = clientView.findViewById<TextView>(R.id.clientNameTextView)
                val clientName = decision.clientInfo?.nom ?: decision.nomClient
                clientNameTextView.text = clientName
                
                // Couleur selon le type de caisse
                val clientType = decision.clientInfo?.typeCaisse ?: "standard"
                val textColor = getClientTypeTextColor(clientType)
                clientNameTextView.setTextColor(textColor)
                
                // Paramètres de layout pour le FlexboxLayout
                val layoutParams = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(0, 0, 8, 8) // Marge droite et bas
                clientView.layoutParams = layoutParams
                
                // Ajouter au FlexboxLayout
                clientsFlexboxLayout.addView(clientView)
            }
        }
        
        /**
         * Obtient la couleur selon le type de caisse
         */
        private fun getClientTypeTextColor(type: String): Int {
            return when (type.uppercase()) {
                "BLL" -> ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                "EUROPOOL" -> ContextCompat.getColor(itemView.context, android.R.color.holo_purple)
                else -> ContextCompat.getColor(itemView.context, R.color.md_theme_light_onSurfaceVariant)
            }
        }
        
    }

    /**
     * Adaptateur pour les décisions clients (SCA)
     */
    class ClientDecisionAdapter : ListAdapter<ClientDecision, ClientDecisionAdapter.ClientViewHolder>(ClientDiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_client_decision, parent, false)
            return ClientViewHolder(view)
        }

        override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ClientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val clientNameTextView: TextView = itemView.findViewById(R.id.clientNameTextView)

            fun bind(decision: ClientDecision) {
                // Afficher le nom du client (champ nom de la collection clients)
                val clientName = decision.clientInfo?.nom ?: decision.nomClient
                clientNameTextView.text = clientName
                
                // Couleur selon le type de caisse
                val clientType = decision.clientInfo?.typeCaisse ?: "standard"
                val textColor = getClientTypeTextColor(clientType)
                clientNameTextView.setTextColor(textColor)
            }
            
            private fun getClientTypeTextColor(type: String): Int {
                return when (type.uppercase()) {
                    "BLL" -> ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                    "EUROPOOL" -> ContextCompat.getColor(itemView.context, android.R.color.holo_purple)
                    else -> ContextCompat.getColor(itemView.context, R.color.md_theme_light_onSurfaceVariant)
                }
            }
        }

        object ClientDiffCallback : DiffUtil.ItemCallback<ClientDecision>() {
            override fun areItemsTheSame(oldItem: ClientDecision, newItem: ClientDecision): Boolean {
                return oldItem.codeClient == newItem.codeClient && oldItem.codeProduit == newItem.codeProduit
            }

            override fun areContentsTheSame(oldItem: ClientDecision, newItem: ClientDecision): Boolean {
                return oldItem == newItem
            }
        }
    }

    object ScamarkDiffCallback : DiffUtil.ItemCallback<ScamarkProduct>() {
        override fun areItemsTheSame(oldItem: ScamarkProduct, newItem: ScamarkProduct): Boolean {
            // Utiliser une combinaison unique : nom produit + fournisseur
            return oldItem.productName == newItem.productName && oldItem.supplier == newItem.supplier
        }

        override fun areContentsTheSame(oldItem: ScamarkProduct, newItem: ScamarkProduct): Boolean {
            return oldItem == newItem
        }
    }
}