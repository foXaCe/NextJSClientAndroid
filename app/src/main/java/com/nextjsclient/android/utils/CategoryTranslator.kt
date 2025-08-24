package com.nextjsclient.android.utils

import android.content.Context
import com.nextjsclient.android.R

object CategoryTranslator {
    
    /**
     * Traduit une catégorie de produit depuis Firebase vers la langue locale
     */
    fun translateCategory(context: Context, category: String?): String? {
        if (category.isNullOrBlank()) return category
        
        return when (category.lowercase().trim()) {
            "légumes", "legumes", "vegetables" -> context.getString(R.string.vegetables_category)
            "fruits", "fruit" -> context.getString(R.string.fruits_category)
            "agrumes", "citrus" -> context.getString(R.string.citrus_category)
            else -> category // Retourne la catégorie originale si pas de traduction
        }
    }
}