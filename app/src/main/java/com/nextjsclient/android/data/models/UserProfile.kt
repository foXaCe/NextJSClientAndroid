package com.nextjsclient.android.data.models

/**
 * Représente le profil d'un utilisateur commercial depuis Firebase
 */
data class UserProfile(
    val email: String = "",
    val nom: String = "",
    val prenom: String = "",
    val fournisseur: String = "",
    val anecoop: Boolean = false,
    val solagora: Boolean = false,
    val appro: Boolean = false,
    val commercial: Boolean = false
) {
    companion object {
        const val COLLECTION_NAME = "commerciaux"
    }
}