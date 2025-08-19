package com.nextjsclient.android.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ScafelArticle(
    @DocumentId
    val id: String = "",
    val code: String = "",
    val designation: String = "",
    val famille: String = "",
    val sousFamille: String? = null,
    val fournisseur: String = "",
    val prixAchat: Double = 0.0,
    val prixVente: Double = 0.0,
    val tva: Double = 5.5,
    val stock: Int = 0,
    val stockMin: Int = 0,
    val unite: String = "U",
    val isActive: Boolean = true,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)