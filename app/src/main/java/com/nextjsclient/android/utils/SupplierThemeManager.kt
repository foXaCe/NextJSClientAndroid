package com.nextjsclient.android.utils

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.nextjsclient.android.R

class SupplierThemeManager(private val context: Context) {
    
    data class SupplierTheme(
        val primaryColor: Int,
        val primaryVariantColor: Int,
        val secondaryColor: Int,
        val backgroundColor: Int,
        val surfaceColor: Int,
        val surfaceVariantColor: Int,
        val onPrimaryColor: Int,
        val onBackgroundColor: Int,
        val onSurfaceColor: Int
    )
    
    private val anecoopTheme = SupplierTheme(
        primaryColor = ContextCompat.getColor(context, R.color.anecoop_primary),
        primaryVariantColor = ContextCompat.getColor(context, R.color.anecoop_primary_variant),
        secondaryColor = ContextCompat.getColor(context, R.color.anecoop_secondary),
        backgroundColor = ContextCompat.getColor(context, R.color.anecoop_background),
        surfaceColor = ContextCompat.getColor(context, R.color.anecoop_surface),
        surfaceVariantColor = ContextCompat.getColor(context, R.color.anecoop_surface_variant),
        onPrimaryColor = ContextCompat.getColor(context, R.color.anecoop_on_primary),
        onBackgroundColor = ContextCompat.getColor(context, R.color.anecoop_on_background),
        onSurfaceColor = ContextCompat.getColor(context, R.color.anecoop_on_surface)
    )
    
    private val solagoraTheme = SupplierTheme(
        primaryColor = ContextCompat.getColor(context, R.color.solagora_primary),
        primaryVariantColor = ContextCompat.getColor(context, R.color.solagora_primary_variant),
        secondaryColor = ContextCompat.getColor(context, R.color.solagora_secondary),
        backgroundColor = ContextCompat.getColor(context, R.color.solagora_background),
        surfaceColor = ContextCompat.getColor(context, R.color.solagora_surface),
        surfaceVariantColor = ContextCompat.getColor(context, R.color.solagora_surface_variant),
        onPrimaryColor = ContextCompat.getColor(context, R.color.solagora_on_primary),
        onBackgroundColor = ContextCompat.getColor(context, R.color.solagora_on_background),
        onSurfaceColor = ContextCompat.getColor(context, R.color.solagora_on_surface)
    )
    
    fun getTheme(supplier: String): SupplierTheme {
        return when (supplier.lowercase()) {
            "anecoop" -> anecoopTheme
            "solagora" -> solagoraTheme
            else -> anecoopTheme // Default to Anecoop
        }
    }
    
    fun applySupplierTheme(supplier: String, vararg views: View) {
        val theme = getTheme(supplier)
        
        views.forEach { view ->
            when (view) {
                is MaterialCardView -> {
                    view.setCardBackgroundColor(theme.surfaceColor)
                    view.strokeColor = theme.primaryColor
                }
                is TextView -> {
                    view.setTextColor(theme.onSurfaceColor)
                }
            }
        }
    }
    
    fun applySupplierBackground(supplier: String, view: View) {
        val theme = getTheme(supplier)
        view.setBackgroundColor(theme.backgroundColor)
    }
    
    fun applySupplierPrimaryColor(supplier: String, view: View) {
        val theme = getTheme(supplier)
        view.backgroundTintList = ColorStateList.valueOf(theme.primaryColor)
    }
    
    fun getSupplierPrimaryColor(supplier: String): Int {
        return getTheme(supplier).primaryColor
    }
    
    fun getSupplierSecondaryColor(supplier: String): Int {
        return getTheme(supplier).secondaryColor
    }
    
    fun getSupplierSurfaceColor(supplier: String): Int {
        return getTheme(supplier).surfaceColor
    }
    
    fun getSupplierBackgroundColor(supplier: String): Int {
        return getTheme(supplier).backgroundColor
    }
}