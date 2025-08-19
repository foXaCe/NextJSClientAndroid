package com.nextjsclient.android.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Structure pour les décisions Scamark selon la VRAIE architecture Firebase
 * Path: decisions_{supplier}/{year}/{week}/{productCode}
 */
data class ScamarkDecision(
    @DocumentId
    val id: String = "",
    val codeProduit: String = "",
    val libelle: String = "",
    val prixRetenu: Double = 0.0,
    val prixOffert: Double = 0.0,
    val supplier: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    
    // Champs promotions au niveau produit
    val isPromo: Boolean = false,
    val commentaire: String? = null,
    
    // Autres champs Firebase
    val categorie: String? = null,
    val marque: String? = null,
    val origine: String? = null,
    val gencode: String? = null,
    val fournisseur: String? = null,
    val fournisseurName: String? = null,
    val pcb: Int = 0,
    val semaine: String? = null,
    val totalScas: Int = 0,
    
    // Tableau des SCA
    val scas: List<ScaClient> = emptyList()
)

/**
 * Client SCA dans une décision produit - structure réelle Firebase
 */
data class ScaClient(
    val codeClient: String = "",
    val semaine: String? = null
)

/**
 * Informations détaillées d'un client
 */
data class ClientInfo(
    @DocumentId
    val documentId: String = "",
    val id: String = "",
    val nom: String = "",
    val nomClient: String? = null,
    val typeCaisse: String = "standard",
    val heureDepart: String = "Non défini",
    val sca: String = ""
)

/**
 * Article enrichi depuis la collection articles
 */
data class Article(
    @DocumentId
    val documentId: String = "",
    val id: String = "",
    val nom: String = "",
    val libelle: String? = null,
    val marque: String? = null,
    val origine: String? = null,
    val categorie: String? = null,
    val category: String? = null,
    val ean: String? = null,
    val gencode: String? = null,
    val codeProduit: String = "",
    val fournisseur: String? = null,
    val fournisseurNom: String? = null,
    val supplier: String? = null
)

/**
 * Produit enrichi pour l'affichage (équivalent du format processedProducts)
 */
data class ScamarkProduct(
    val productName: String = "",
    val supplier: String = "",
    val prixRetenu: Double = 0.0,
    val prixOffert: Double = 0.0,
    val isPromo: Boolean = false,
    val articleInfo: Article? = null,
    val decisions: List<ClientDecision> = emptyList(),
    val totalScas: Int = 0
)

/**
 * Décision enrichie avec info client
 */
data class ClientDecision(
    val codeClient: String = "",
    val nomClient: String = "",
    val codeProduit: String = "",
    val libelle: String = "",
    val prixRetenu: Double = 0.0,
    val prixOffert: Double = 0.0,
    val clientInfo: ClientInfo? = null
)

/**
 * Semaine disponible
 */
data class AvailableWeek(
    val year: Int = 0,
    val week: Int = 0,
    val supplier: String = ""
)

/**
 * Statistiques dashboard
 */
data class ScamarkStats(
    val totalProducts: Int = 0,
    val activeClients: Int = 0,
    val productsIn: Int = 0,
    val productsOut: Int = 0,
    val totalPromos: Int = 0
)

/**
 * Palmarès d'un produit depuis le 01 octobre
 */
data class ProductPalmares(
    val consecutiveWeeks: Int = 0,
    val totalReferences: Int = 0,
    val referencedWeeks: List<Pair<Int, Int>> = emptyList(), // List of (year, week) pairs
    val percentage: Int = 0 // Pourcentage des semaines où le produit était référencé
)