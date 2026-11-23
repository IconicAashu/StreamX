package com.streamx.app.utils

import java.util.Locale

fun formatRating(rating: Double): String {
    return String.format(Locale.US, "%.1f", rating)
}
