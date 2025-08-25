package com.nextjsclient.android.utils

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.TextView

/**
 * Utilitaire pour créer des animations de comptage premium sur les TextViews
 */
class CountUpAnimator {
    
    companion object {
        /**
         * Anime un TextView de 0 vers la valeur finale avec un effet de comptage
         */
        fun animateCountUp(
            textView: TextView,
            targetValue: Int,
            duration: Long = 800L,
            prefix: String = "",
            suffix: String = ""
        ) {
            val animator = ValueAnimator.ofInt(0, targetValue)
            animator.duration = duration
            animator.interpolator = DecelerateInterpolator()
            
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                textView.text = "$prefix$value$suffix"
            }
            
            animator.start()
        }
        
        /**
         * Anime un TextView de la valeur actuelle vers la nouvelle valeur
         */
        fun animateCountFromTo(
            textView: TextView,
            fromValue: Int,
            toValue: Int,
            duration: Long = 600L,
            prefix: String = "",
            suffix: String = ""
        ) {
            val animator = ValueAnimator.ofInt(fromValue, toValue)
            animator.duration = duration
            animator.interpolator = DecelerateInterpolator()
            
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                textView.text = "$prefix$value$suffix"
            }
            
            animator.start()
        }
        
        /**
         * Anime plusieurs TextViews en séquence pour un effet cascadé
         */
        fun animateCountUpSequence(
            textViews: List<Pair<TextView, Int>>,
            delayBetween: Long = 150L,
            animationDuration: Long = 600L,
            prefix: String = "",
            suffix: String = ""
        ) {
            textViews.forEachIndexed { index, (textView, targetValue) ->
                val startDelay = index * delayBetween
                
                val animator = ValueAnimator.ofInt(0, targetValue)
                animator.duration = animationDuration
                animator.startDelay = startDelay
                animator.interpolator = DecelerateInterpolator()
                
                animator.addUpdateListener { animation ->
                    val value = animation.animatedValue as Int
                    textView.text = "$prefix$value$suffix"
                }
                
                animator.start()
            }
        }
        
        /**
         * Anime avec un effet de pulsation pour mettre en valeur
         */
        fun animateCountUpWithPulse(
            textView: TextView,
            targetValue: Int,
            duration: Long = 800L,
            prefix: String = "",
            suffix: String = ""
        ) {
            // Animation de comptage
            val countAnimator = ValueAnimator.ofInt(0, targetValue)
            countAnimator.duration = duration
            countAnimator.interpolator = DecelerateInterpolator()
            
            countAnimator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                textView.text = "$prefix$value$suffix"
            }
            
            // Effet de pulsation à la fin
            countAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Petite pulsation pour attirer l'attention
                    textView.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(200L)
                        .withEndAction {
                            textView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(200L)
                                .start()
                        }
                        .start()
                }
            })
            
            countAnimator.start()
        }
        
        /**
         * Anime avec un effet de va-et-vient : compte jusqu'à la valeur max puis redescend
         */
        fun animateCountUpAndDown(
            textView: TextView,
            maxValue: Int,
            totalDuration: Long = 2000L,
            prefix: String = "",
            suffix: String = ""
        ) {
            // Préparer la position initiale avec slide-in
            textView.translationY = 30f
            textView.alpha = 0f
            
            // Animation d'entrée
            textView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300L)
                .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                .start()
            
            // Animation de comptage aller-retour
            val halfDuration = totalDuration / 2
            
            // Phase 1: Monter de 0 à maxValue
            val upAnimator = ValueAnimator.ofInt(0, maxValue)
            upAnimator.duration = halfDuration
            upAnimator.interpolator = DecelerateInterpolator()
            
            upAnimator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                textView.text = "$prefix$value$suffix"
            }
            
            // Phase 2: Redescendre de maxValue à 0
            upAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    val downAnimator = ValueAnimator.ofInt(maxValue, 0)
                    downAnimator.duration = halfDuration
                    downAnimator.interpolator = DecelerateInterpolator()
                    
                    downAnimator.addUpdateListener { animation ->
                        val value = animation.animatedValue as Int
                        textView.text = "$prefix$value$suffix"
                    }
                    
                    downAnimator.start()
                }
            })
            
            upAnimator.start()
        }
        
        /**
         * Animation continue avec va-et-vient jusqu'à interruption externe
         */
        fun animateCountUpAndDownContinuous(
            textView: TextView,
            maxValue: Int,
            cycleDuration: Long = 2000L,
            prefix: String = "",
            suffix: String = ""
        ) {
            // Préparer la position initiale avec slide-in
            textView.translationY = 30f
            textView.alpha = 0f
            
            // Animation d'entrée
            textView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300L)
                .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                .withEndAction {
                    // Une fois l'entrée terminée, démarrer les cycles continus
                    startContinuousCycle(textView, maxValue, cycleDuration, prefix, suffix)
                }
                .start()
        }
        
        private fun startContinuousCycle(
            textView: TextView,
            maxValue: Int,
            cycleDuration: Long,
            prefix: String,
            suffix: String
        ) {
            val halfDuration = cycleDuration / 2
            
            // Phase 1: Monter de 0 à maxValue
            val upAnimator = ValueAnimator.ofInt(0, maxValue)
            upAnimator.duration = halfDuration
            upAnimator.interpolator = DecelerateInterpolator()
            
            upAnimator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                textView.text = "$prefix$value$suffix"
            }
            
            // Phase 2: Redescendre de maxValue à 0
            upAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Vérifier si l'animation doit continuer (tag non null = continue)
                    val shouldContinue = textView.tag != null
                    android.util.Log.d("CountUpAnimator", "⬇️ Up phase ended - shouldContinue: $shouldContinue, tag: ${textView.tag}")
                    if (shouldContinue) {
                        val downAnimator = ValueAnimator.ofInt(maxValue, 0)
                        downAnimator.duration = halfDuration
                        downAnimator.interpolator = DecelerateInterpolator()
                        
                        downAnimator.addUpdateListener { animation ->
                            val value = animation.animatedValue as Int
                            textView.text = "$prefix$value$suffix"
                        }
                        
                        downAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                // Vérifier encore si l'animation doit continuer
                                val shouldContinueDown = textView.tag != null
                                android.util.Log.d("CountUpAnimator", "⬇️ Down phase ended - shouldContinue: $shouldContinueDown, tag: ${textView.tag}")
                                if (shouldContinueDown) {
                                    android.util.Log.d("CountUpAnimator", "🔄 Starting new cycle...")
                                    // Redémarrer un nouveau cycle
                                    startContinuousCycle(textView, maxValue, cycleDuration, prefix, suffix)
                                } else {
                                    android.util.Log.d("CountUpAnimator", "🛑 Cycle stopped - no tag")
                                }
                            }
                        })
                        
                        downAnimator.start()
                    }
                }
            })
            
            // Marquer l'animator actif dans le tag
            textView.tag = upAnimator
            upAnimator.start()
        }
        
        /**
         * Arrête l'animation continue et transition vers la vraie valeur
         */
        fun stopContinuousAndTransitionTo(
            textView: TextView,
            finalValue: Int,
            prefix: String = "",
            suffix: String = ""
        ) {
            // Arrêter l'animation continue en cours
            val currentAnimator = textView.tag as? ValueAnimator
            currentAnimator?.cancel()  // Annuler l'animator actuel immédiatement
            textView.tag = null  // Signal d'arrêt pour les cycles
            
            // Obtenir la valeur actuelle affichée
            val currentText = textView.text.toString()
            val currentValue = currentText.replace(prefix, "").replace(suffix, "").toIntOrNull() ?: 0
            
            android.util.Log.d("CountUpAnimator", "🛑 Stopping continuous animation - current: $currentValue, target: $finalValue")
            
            // Transition fluide vers la vraie valeur
            val transitionAnimator = ValueAnimator.ofInt(currentValue, finalValue)
            transitionAnimator.duration = 800L
            transitionAnimator.interpolator = DecelerateInterpolator()
            
            transitionAnimator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                textView.text = "$prefix$value$suffix"
            }
            
            transitionAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    android.util.Log.d("CountUpAnimator", "✅ Transition completed to: $finalValue")
                }
            })
            
            transitionAnimator.start()
        }

        /**
         * Animation Material 3 de placeholder pendant le chargement
         */
        fun animateLoadingPlaceholder(textView: TextView, placeholder: String = "--") {
            // Animation de shimmer/pulsation douce
            textView.text = placeholder
            textView.alpha = 0.6f
            
            val shimmerAnimator = ValueAnimator.ofFloat(0.6f, 1.0f, 0.6f)
            shimmerAnimator.duration = 1500L
            shimmerAnimator.repeatCount = ValueAnimator.INFINITE
            shimmerAnimator.interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            
            shimmerAnimator.addUpdateListener { animation ->
                textView.alpha = animation.animatedValue as Float
            }
            
            // Stocker l'animator dans le tag pour pouvoir l'arrêter plus tard
            textView.tag = shimmerAnimator
            shimmerAnimator.start()
        }

        /**
         * Arrête l'animation de placeholder et lance l'animation de comptage
         */
        fun stopPlaceholderAndCountUp(
            textView: TextView,
            targetValue: Int,
            duration: Long = 800L,
            prefix: String = "",
            suffix: String = "",
            withPulse: Boolean = true
        ) {
            // Arrêter l'animation de placeholder
            val placeholderAnimator = textView.tag as? ValueAnimator
            placeholderAnimator?.cancel()
            textView.tag = null
            
            // Reset alpha
            textView.alpha = 1.0f
            
            // Démarrer l'animation de comptage
            if (withPulse) {
                animateCountUpWithPulse(textView, targetValue, duration, prefix, suffix)
            } else {
                animateCountUp(textView, targetValue, duration, prefix, suffix)
            }
        }

        /**
         * Animation Material 3 avec effet de slide-in depuis le bas
         */
        fun animateCountUpWithSlideIn(
            textView: TextView,
            targetValue: Int,
            duration: Long = 800L,
            prefix: String = "",
            suffix: String = ""
        ) {
            // Préparer la position initiale
            textView.translationY = 30f
            textView.alpha = 0f
            
            // Animation d'entrée
            textView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400L)
                .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                .withEndAction {
                    // Lancer l'animation de comptage après l'entrée
                    animateCountUp(textView, targetValue, duration - 400L, prefix, suffix)
                }
                .start()
        }
        
        /**
         * Animation progressive lente depuis 0 vers une valeur cible
         * Idéale pour les animations de loading qui montent graduellement
         */
        fun animateProgressiveCountUp(
            textView: TextView,
            targetValue: Int,
            duration: Long = 3000L,
            prefix: String = "",
            suffix: String = ""
        ) {
            // Commencer à 0
            textView.text = "${prefix}0${suffix}"
            
            // Animation progressive avec DecelerateInterpolator pour un effet naturel
            val animator = ValueAnimator.ofInt(0, targetValue)
            animator.duration = duration
            animator.interpolator = DecelerateInterpolator(2.0f) // Deceleration forte pour effet naturel
            
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                textView.text = "$prefix$value$suffix"
            }
            
            // Marquer l'animator dans le tag pour pouvoir l'arrêter
            textView.tag = animator
            android.util.Log.d("CountUpAnimator", "🚀 Starting progressive animation 0 → $targetValue over ${duration}ms")
            animator.start()
        }
    }
}