package com.nextjsclient.android.data.models

data class RuptureHistory(
    val date: String = "",
    val supplier: String = "",
    val time: String = "",
    val timestamp: Long = 0L,
    val week: Int = 0,
    val year: Int = 0,
    val products: List<RuptureProduct> = emptyList(),
    val ruptureCount: Int = 0,
    val totalMissing: Int = 0
)

data class RuptureProduct(
    val codeProduit: String = "",
    val productName: String = "",
    val category: String = "",
    val stockDisponible: Int = 0,
    val totalMissing: Int = 0,
    val approvisionneur: Approvisionneur = Approvisionneur(),
    val scasAffected: List<ScaAffected> = emptyList()
)

data class Approvisionneur(
    val nom: String = "",
    val prenom: String = "",
    val email: String = ""
)

data class ScaAffected(
    val codeClient: String = "",
    val clientName: String = "",
    val quantityAvailable: Int = 0,
    val quantityCommanded: Int = 0,
    val quantityMissing: Int = 0
)