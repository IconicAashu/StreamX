package com.streamx.app.domain.model

enum class ChannelFilterType(val typeKey: String) {
    GENRE("genre"),
    COUNTRY("country"),
    YEAR("year"),
    SORT("sort"),
    RATE("rate"),
    CLASSIFY_LANGUAGE("classify"),
    OTHER("other");

    companion object {
        fun fromKey(key: String): ChannelFilterType {
            return entries.find { it.typeKey.equals(key, ignoreCase = true) } ?: OTHER
        }
    }
}

data class FilterOption(
    val id: String,
    val name: String
)

data class FilterRange(
    val minVal: Float = 0f,
    val maxVal: Float = 10f
)

data class ChannelStyle(
    val colNum: Int = 3,
    val shape: String = "Vertical"
)

data class ChannelFilter(
    val type: ChannelFilterType,
    val rawFilterType: String,
    val title: String,
    val showOut: Boolean,
    val options: List<FilterOption> = emptyList(),
    val range: FilterRange? = null
)

data class ChannelType(
    val channelId: Int,
    val channelName: String,
    val subjectType: Int = 1,
    val style: ChannelStyle = ChannelStyle(colNum = 3, shape = "Vertical"),
    val filters: List<ChannelFilter> = emptyList()
)

data class ActiveFilterState(
    val channelId: Int = 1,
    val channelName: String = "Movie",
    val selectedGenre: String = "All",
    val selectedCountry: String = "All",
    val selectedYear: String = "All",
    val selectedSort: String = "ForYou",
    val selectedMinRating: Float = 0f,
    val selectedLanguage: String = "All"
) {
    val hasActiveFilters: Boolean
        get() = selectedGenre != "All" ||
                selectedCountry != "All" ||
                selectedYear != "All" ||
                selectedSort != "ForYou" ||
                selectedMinRating > 0f ||
                selectedLanguage != "All"

    val activeFilterCount: Int
        get() {
            var count = 0
            if (selectedGenre != "All") count++
            if (selectedCountry != "All") count++
            if (selectedYear != "All") count++
            if (selectedSort != "ForYou") count++
            if (selectedMinRating > 0f) count++
            if (selectedLanguage != "All") count++
            return count
        }
}
