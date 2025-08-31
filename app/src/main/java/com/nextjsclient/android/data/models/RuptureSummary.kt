package com.nextjsclient.android.data.models

data class RuptureSummary(
    val totalRuptures: Int = 0,
    val totalCommanded: Int = 0,
    val totalDelivered: Int = 0,
    val totalMissing: Int = 0,
    val deliveryRate: Double = 0.0,
    val periodStart: String = ""
)