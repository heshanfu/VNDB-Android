package com.booboot.vndbandroid.model.vndbandroid

object Genre {
    /**
     * List of existing genres, based on MAL advanced search
     */
    private val GENRES = listOf("Action", "Adventure", "Cars", "Comedy", "Dementia", "Demons", "Drama", "Ecchi", "Fantasy", "Game", "Harem", "Hentai", "Historical", "Horror", "Josei", "Kids",
            "Magic", "Martial Arts", "Mecha", "Military", "Music", "Mystery", "Parody", "Police", "Psychological", "Romance", "Samurai", "School", "Sci-Fi", "Seinen", "Shoujo", "Shoujo Ai",
            "Shounen", "Shounen Ai", "Slice of Life", "Space", "Sports", "Super Power", "Supernatural", "Thriller", "Vampire", "Yaoi", "Yuri")

    operator fun contains(tagName: String) =
            GENRES.any { genre -> genre.toUpperCase() == tagName.trim().toUpperCase() }
}