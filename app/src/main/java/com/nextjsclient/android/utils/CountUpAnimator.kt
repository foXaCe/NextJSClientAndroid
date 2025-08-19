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
    }
}