package com.nextjsclient.android.utils

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
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
    }
}