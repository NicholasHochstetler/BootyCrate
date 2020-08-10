/* Copyright 2020 Nicholas Hochstetler
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0, or in the file
 * LICENSE in the project's root directory. */

package com.cliffracermerchant.bootycrate

import androidx.lifecycle.LiveData
import androidx.room.*

/** A Room DAO for BootyCrateDatabase's shopping_list_item table. */
@Dao abstract class ShoppingListItemDao : DataAccessObject<ShoppingListItem>() {
    @Query("SELECT * FROM shopping_list_item WHERE NOT inTrash AND name LIKE :filter ORDER BY color")
    abstract override fun getAllSortedByColor(filter: String): LiveData<List<ShoppingListItem>>

    @Query("SELECT * FROM shopping_list_item WHERE NOT inTrash AND name LIKE :filter ORDER BY name ASC")
    abstract override fun getAllSortedByNameAsc(filter: String): LiveData<List<ShoppingListItem>>

    @Query("SELECT * FROM shopping_list_item WHERE NOT inTrash AND name LIKE :filter ORDER BY name DESC")
    abstract override fun getAllSortedByNameDesc(filter: String): LiveData<List<ShoppingListItem>>

    @Query("SELECT * FROM shopping_list_item WHERE NOT inTrash AND name LIKE :filter ORDER BY amountOnList ASC")
    abstract override fun getAllSortedByAmountAsc(filter: String): LiveData<List<ShoppingListItem>>

    @Query("SELECT * FROM shopping_list_item WHERE NOT inTrash AND name LIKE :filter ORDER BY amountOnList DESC")
    abstract override fun getAllSortedByAmountDesc(filter: String): LiveData<List<ShoppingListItem>>

    @Insert abstract override suspend fun insert(item: ShoppingListItem): Long

    @Insert abstract override suspend fun insert(vararg items: ShoppingListItem)

    @Query("INSERT INTO shopping_list_item (name, extraInfo, color, " +
                                           "linkedInventoryItemId) " +
           "SELECT name, extraInfo, color, id " +
           "FROM inventory_item " +
           "WHERE inventory_item.id IN (:inventoryItemIds) " +
           "AND inventory_item.id NOT IN (SELECT linkedInventoryItemId " +
                                         "FROM shopping_list_item " +
                                         "WHERE NOT inTrash)")
    abstract suspend fun insertFromInventoryItems(vararg inventoryItemIds: Long)

    @Transaction
    open suspend fun autoAddFromInventoryItem(inventoryItemId: Long, minAmount: Int) {
        insertFromInventoryItems(inventoryItemId)
        setMinimumAmountFromLinkedItem(inventoryItemId, minAmount)
    }

    @Query("UPDATE shopping_list_item " +
           "SET name = :name WHERE id = :id")
    abstract suspend fun updateName(id: Long, name: String)

    @Query("UPDATE shopping_list_item " +
           "SET name = :name " +
           "WHERE linkedInventoryItemId = :inventoryItemId")
    abstract suspend fun updateNameFromLinkedInventoryItem(inventoryItemId: Long, name: String)

    @Query("UPDATE shopping_list_item " +
           "SET extraInfo = :extraInfo " +
           "WHERE id = :id")
    abstract suspend fun updateExtraInfo(id: Long, extraInfo: String)

    @Query("UPDATE shopping_list_item " +
           "SET extraInfo = :extraInfo " +
           "WHERE linkedInventoryItemId = :inventoryItemId")
    abstract suspend fun updateExtraInfoFromLinkedInventoryItem(inventoryItemId: Long, extraInfo: String)

    @Query("UPDATE shopping_list_item " +
           "SET color = :color " +
           "WHERE id = :id")
    abstract suspend fun updateColor(id: Long, color: Int)

    @Query("UPDATE shopping_list_item " +
           "SET isChecked = :isChecked, " +
               "amountInCart = CASE WHEN :isChecked = 0 THEN 0 " +
                                   "WHEN amountInCart < amountOnList THEN amountOnList " +
                                   "ELSE amountInCart END " +
           "WHERE id = :id")
    abstract suspend fun updateIsChecked(id: Long, isChecked: Boolean)

    @Query("UPDATE shopping_list_item " +
           "SET amountOnList = :amountOnList, " +
               "isChecked = CASE WHEN amountInCart >= :amountOnList THEN 1 " +
                                "ELSE 0 END " +
           "WHERE id = :id")
    abstract suspend fun updateAmountOnList(id: Long, amountOnList: Int)

    @Query("UPDATE shopping_list_item " +
           "SET amountOnList = CASE WHEN amountOnList < :minAmount THEN :minAmount " +
                                   "ELSE amountOnList END, " +
               "isChecked = CASE WHEN amountInCart >= amountOnList THEN 1 " +
                                "ELSE 0 END " +
           "WHERE linkedInventoryItemId = :inventoryItemId")
    abstract suspend fun setMinimumAmountFromLinkedItem(inventoryItemId: Long, minAmount: Int)

    @Query("UPDATE shopping_list_item " +
           "SET amountOnList = :amountOnList, " +
               "isChecked = CASE WHEN amountInCart >= :amountOnList THEN 1 " +
                                "ELSE 0 END " +
           "WHERE linkedInventoryItemId = :inventoryItemId")
    abstract suspend fun updateAmountOnListFromLinkedItem(inventoryItemId: Long, amountOnList: Int)

    @Query("UPDATE shopping_list_item " +
           "SET amountInCart = :amountInCart, " +
               "isChecked = CASE WHEN amountInCart >= amountOnList THEN 1 " +
                                "ELSE 0 END " +
           "WHERE id = :id")
    abstract suspend fun updateAmountInCart(id: Long, amountInCart: Int)

    @Query("UPDATE shopping_list_item " +
           "SET linkedInventoryItemId = :linkedInventoryItemId, " +
               "name = :linkedInventoryItemName, " +
               "extraInfo = :linkedInventoryItemExtraInfo " +
           "WHERE id = :id")
    abstract suspend fun updateLinkedInventoryItemId(id: Long, linkedInventoryItemId: Long,
                                                     linkedInventoryItemName: String,
                                                     linkedInventoryItemExtraInfo: String)

    @Query("UPDATE shopping_list_item " +
           "SET linkedInventoryItemId = null " +
           "WHERE id = :id")
    abstract suspend fun removeInventoryItemLink(id: Long)

    @Query("WITH bought_amounts AS (SELECT linkedInventoryItemId AS id, " +
                                          "amountInCart AS amount " +
                                   "FROM shopping_list_item " +
                                   "WHERE linkedInventoryItemId IS NOT NULL) " +
            "UPDATE inventory_item " +
            "SET amount = amount + (SELECT amount FROM bought_amounts " +
                                   "WHERE id = inventory_item.id) " +
            "WHERE id IN (SELECT id FROM bought_amounts)")
    abstract suspend fun updateInventoryItemAmountsFromAmountInCart()

    @Query("UPDATE shopping_list_item " +
           "SET amountOnList = amountOnList - amountInCart")
    abstract suspend fun subtractAmountInCartFromAmountOnList()

    @Query("DELETE FROM shopping_list_item WHERE amountOnList < 1")
    abstract suspend fun clearZeroOrNegativeAmountItems()

    /** The checkout function allows the user to clear all checked shopping
     *  list items at once and modify the amounts of linked inventory items
     *  appropriately. For non-linked shopping list items, which are simply
     *  removed from the list, the checkout functions acts no differently than
     *  removing them via swiping or the delete button. */
    @Transaction open suspend fun checkOut() {
        updateInventoryItemAmountsFromAmountInCart()
        subtractAmountInCartFromAmountOnList()
        clearZeroOrNegativeAmountItems()
    }

    @Query("DELETE FROM shopping_list_item")
    abstract override suspend fun deleteAll()

    @Query("DELETE FROM shopping_list_item " +
           "WHERE inTrash = 1")
    abstract override suspend fun emptyTrash()

    @Query("UPDATE shopping_list_item " +
           "SET inTrash = 1 " +
           "WHERE id IN (:ids)")
    abstract suspend fun moveToTrash(vararg ids: Long)

    @Transaction override suspend fun delete(vararg ids: Long) {
        emptyTrash()
        moveToTrash(*ids)
    }

    @Query("UPDATE shopping_list_item " +
           "SET inTrash = 0 " +
           "WHERE inTrash = 1")
    abstract override suspend fun undoDelete()
}