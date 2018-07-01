package com.booboot.vndbandroid.model.vndb

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings

@SuppressWarnings(RoomWarnings.DEFAULT_CONSTRUCTOR)
@Entity(tableName = "wishlist")
data class Wishlist(
        @PrimaryKey override var vn: Int = 0,
        override var added: Int = 0,
        var priority: Int = 0
) : AccountItem()