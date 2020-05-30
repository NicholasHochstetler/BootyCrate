package com.cliffracermerchant.bootycrate

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_list_item")
class ShoppingListItem {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")           var id:           Long = 0
    @ColumnInfo(name = "name")         var name:         String
    @ColumnInfo(name = "extraInfo")    var extraInfo:    String
    @ColumnInfo(name = "amountInCart") var amountInCart: Int
    @ColumnInfo(name = "amount")       var amount:       Int
    @ColumnInfo(name = "inTrash")      var inTrash:      Boolean = false

    constructor(name: String,
                extraInfo: String = "",
                amountInCart: Int = 0,
                amount: Int = 1) {
        this.name = name
        this.extraInfo = extraInfo
        this.amountInCart = amountInCart
        this.amount = amount
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || other !is ShoppingListItem) return false
        return this.id == other.id &&
               this.name == other.name &&
               this.extraInfo == other.extraInfo &&
               this.amountInCart == other.amountInCart &&
               this.amount == other.amount
    }

    override fun toString(): String {
        return "\nid = " + id +
               "\nname = " + name +
               "\nextraInfo = " + extraInfo +
               "\namountInCart = " + amountInCart +
               "\namount = " + amount
    }

    override fun hashCode(): Int = id.hashCode()
}