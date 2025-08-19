package com.nextjsclient.android.data.models

/**
 * Information sur une semaine disponible
 */
data class WeekInfo(
    val year: Int,
    val week: Int,
    val supplier: String = ""
)