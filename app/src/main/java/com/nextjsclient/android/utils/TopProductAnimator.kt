package com.nextjsclient.android.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.nextjsclient.android.data.models.ScamarkProduct

/**
 * Animations Material 3 expressives pour les Top Produits
 */
object TopProductAnimator {
    
    /**
     * Animation d'apparition expressive avec effet de rebond et scale
     */
    fun animateTopProductsEntrance(products: List<ScamarkProduct>, parentView: View, onComplete: () -> Unit = {}) {
        val topItems = listOf(
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca1),
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca2), 
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca3)
        )
        
        // Préparer l'état initial : invisible et décalé
        topItems.forEach { item ->
            item?.apply {
                alpha = 0f
                scaleX = 0.8f
                scaleY = 0.8f
                translationY = 40f
                visibility = View.INVISIBLE
            }
        }
        
        // Animer chaque élément avec un délai progressif
        val animatorSet = AnimatorSet()
        val animations = mutableListOf<AnimatorSet>()
        
        products.take(3).forEachIndexed { index, product ->
            val item = topItems.getOrNull(index) ?: return@forEachIndexed
            
            // Rendre visible avant l'animation
            item.visibility = View.VISIBLE
            
            // Animation de scale avec overshoot
            val scaleXAnimator = ObjectAnimator.ofFloat(item, "scaleX", 0.8f, 1.05f, 1.0f)
            val scaleYAnimator = ObjectAnimator.ofFloat(item, "scaleY", 0.8f, 1.05f, 1.0f)
            
            // Animation de translation avec bounce
            val translationAnimator = ObjectAnimator.ofFloat(item, "translationY", 40f, -8f, 0f)
            
            // Animation d'alpha
            val alphaAnimator = ObjectAnimator.ofFloat(item, "alpha", 0f, 1f)
            
            // Créer l'ensemble d'animation pour cet élément
            val itemAnimatorSet = AnimatorSet()
            itemAnimatorSet.playTogether(scaleXAnimator, scaleYAnimator, translationAnimator, alphaAnimator)
            itemAnimatorSet.duration = 600L + (index * 100L) // Duration progressive
            itemAnimatorSet.interpolator = OvershootInterpolator(0.8f)
            itemAnimatorSet.startDelay = index * 150L // Délai en cascade
            
            animations.add(itemAnimatorSet)
            
            // Animation des nombres SCA avec comptage
            val scaIds = listOf(
                com.nextjsclient.android.R.id.topSca1Sca,
                com.nextjsclient.android.R.id.topSca2Sca,
                com.nextjsclient.android.R.id.topSca3Sca
            )
            val scaTextView = item.findViewById<TextView>(scaIds[index])
            scaTextView?.let { textView ->
                // Délai pour le comptage après l'apparition de l'élément
                item.postDelayed({
                    CountUpAnimator.animateCountUp(textView, product.totalScas, 800L, "", " SCA")
                }, itemAnimatorSet.startDelay + 400L)
            }
        }
        
        // Démarrer toutes les animations
        animatorSet.playTogether(animations.toList())
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
        })
        animatorSet.start()
    }
    
    /**
     * Animation de mise à jour avec effet de pulsation
     */
    fun animateTopProductsUpdate(products: List<ScamarkProduct>, parentView: View) {
        val topItems = listOf(
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca1),
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca2),
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca3)
        )
        
        products.take(3).forEachIndexed { index, product ->
            val item = topItems.getOrNull(index) ?: return@forEachIndexed
            
            // Animation de pulsation légère
            val pulseAnimator = ObjectAnimator.ofFloat(item, "scaleX", 1.0f, 1.02f, 1.0f)
            val pulseAnimatorY = ObjectAnimator.ofFloat(item, "scaleY", 1.0f, 1.02f, 1.0f)
            
            val animatorSet = AnimatorSet()
            animatorSet.playTogether(pulseAnimator, pulseAnimatorY)
            animatorSet.duration = 300L
            animatorSet.startDelay = index * 80L
            animatorSet.interpolator = FastOutSlowInInterpolator()
            animatorSet.start()
            
            // Mettre à jour le texte SCA avec animation
            val scaIds = listOf(
                com.nextjsclient.android.R.id.topSca1Sca,
                com.nextjsclient.android.R.id.topSca2Sca,
                com.nextjsclient.android.R.id.topSca3Sca
            )
            val scaTextView = item.findViewById<TextView>(scaIds[index])
            scaTextView?.let { textView ->
                item.postDelayed({
                    CountUpAnimator.animateCountFromTo(textView, 
                        textView.text.toString().replace(" SCA", "").toIntOrNull() ?: 0,
                        product.totalScas, 400L, "", " SCA")
                }, animatorSet.startDelay + 150L)
            }
        }
    }
    
    /**
     * Animation de transition élégante entre différents fournisseurs
     */
    fun animateTopProductsTransition(
        oldProducts: List<ScamarkProduct>,
        newProducts: List<ScamarkProduct>, 
        parentView: View,
        onComplete: () -> Unit = {}
    ) {
        val topItems = listOf(
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca1),
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca2),
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca3)
        )
        
        // Phase 1: Faire disparaître les anciens éléments
        val fadeOutAnimators = topItems.mapIndexed { index, item ->
            item?.let {
                val fadeOut = ObjectAnimator.ofFloat(it, "alpha", 1f, 0f)
                val scaleDownX = ObjectAnimator.ofFloat(it, "scaleX", 1f, 0.9f)
                val scaleDownY = ObjectAnimator.ofFloat(it, "scaleY", 1f, 0.9f)
                val slideUp = ObjectAnimator.ofFloat(it, "translationY", 0f, -20f)
                
                AnimatorSet().apply {
                    playTogether(fadeOut, scaleDownX, scaleDownY, slideUp)
                    duration = 300L
                    startDelay = index * 50L
                    interpolator = FastOutSlowInInterpolator()
                }
            }
        }.filterNotNull()
        
        val fadeOutSet = AnimatorSet()
        fadeOutSet.playTogether(fadeOutAnimators)
        fadeOutSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Phase 2: Faire apparaître les nouveaux éléments
                animateTopProductsEntrance(newProducts, parentView, onComplete)
            }
        })
        fadeOutSet.start()
    }
    
    /**
     * Animation de révélation avec effet shimmer
     */
    fun animateTopProductsReveal(products: List<ScamarkProduct>, parentView: View) {
        val topItems = listOf(
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca1),
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca2),
            parentView.findViewById<View>(com.nextjsclient.android.R.id.topSca3)
        )
        
        products.take(3).forEachIndexed { index, product ->
            val item = topItems.getOrNull(index) ?: return@forEachIndexed
            
            // Effet de révélation de gauche à droite
            val clipAnimator = ValueAnimator.ofFloat(0f, 1f)
            clipAnimator.duration = 500L + (index * 100L)
            clipAnimator.startDelay = index * 120L
            clipAnimator.interpolator = FastOutSlowInInterpolator()
            
            // État initial
            item.alpha = 0f
            item.translationX = -50f
            item.visibility = View.VISIBLE
            
            clipAnimator.addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                item.alpha = progress
                item.translationX = -50f * (1 - progress)
                
                // Effet shimmer sur le texte SCA
                if (progress > 0.5f) {
                    val scaIds = listOf(
                        com.nextjsclient.android.R.id.topSca1Sca,
                        com.nextjsclient.android.R.id.topSca2Sca,
                        com.nextjsclient.android.R.id.topSca3Sca
                    )
                    val scaTextView = item.findViewById<TextView>(scaIds[index])
                    scaTextView?.alpha = (progress - 0.5f) * 2f
                }
            }
            
            clipAnimator.start()
        }
    }
}