package com.streamx.app.domain.model

/**
 * Local user profile entity.
 * Represents an on-device local user identity without requiring cloud accounts or passwords.
 */
data class UserProfile(
    val displayName: String = "",
    val avatarUri: String? = null,
    val isCreated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Generates a 1 or 2 letter uppercase initials monogram for default avatar rendering.
     * Examples:
     * - "Rahul" -> "R"
     * - "Rahul Sharma" -> "RS"
     * - "" -> "U"
     */
    val initials: String
        get() {
            val trimmed = displayName.trim()
            if (trimmed.isEmpty()) return "U"
            val parts = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return if (parts.size >= 2) {
                "${parts.first().take(1)}${parts.last().take(1)}".uppercase()
            } else {
                trimmed.take(1).uppercase()
            }
        }
}
