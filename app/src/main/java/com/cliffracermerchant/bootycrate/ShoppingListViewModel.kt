package com.cliffracermerchant.bootycrate

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.launch

class ShoppingListViewModel(app: Application) : AndroidViewModel(app) {

    private val dao: ShoppingListItemDao = BootyCrateDatabase.get(app).shoppingListItemDao()
    private val sortLiveData = MutableLiveData(Sort.OriginalInsertionOrder)
    var sort get() = sortLiveData.value
             set(value) { sortLiveData.value = value }
    private val items = Transformations.switchMap(sortLiveData) { sort ->
        when (sort) {
            null -> dao.getAll()
            Sort.OriginalInsertionOrder -> dao.getAll()
            Sort.NameAsc -> dao.getAllSortedByNameAsc()
            Sort.NameDesc -> dao.getAllSortedByNameDesc()
            Sort.AmountAsc -> dao.getAllSortedByAmountAsc()
            Sort.AmountDesc -> dao.getAllSortedByAmountDesc()
        }
    }

    init { viewModelScope.launch{ dao.emptyTrash() } }

    fun getAll() = items

    fun insert(vararg items: ShoppingListItem) = viewModelScope.launch {
        dao.insert(*items)
    }
    fun insertFromInventoryItems(vararg itemIds: Long) = viewModelScope.launch {
        dao.insertFromInventoryItems(*itemIds)
    }
    fun updateName(id: Long, name: String) = viewModelScope.launch {
        dao.updateName(id, name)
    }
    fun updateNameFromLinkedInventoryItem(inventoryItemId: Long, name: String) = viewModelScope.launch {
        dao.updateNameFromLinkedInventoryItem(inventoryItemId, name)
    }
    fun updateExtraInfo(id: Long, extraInfo: String) = viewModelScope.launch {
        dao.updateExtraInfo(id, extraInfo)
    }
    fun updateExtraInfoFromLinkedInventoryItem(inventoryItemId: Long, extraInfo: String) = viewModelScope.launch {
        dao.updateExtraInfoFromLinkedInventoryItem(inventoryItemId, extraInfo)
    }
    fun updateAmountInCart(id: Long, amountInCart: Int) = viewModelScope.launch {
        dao.updateAmountInCart(id, amountInCart)
    }
    fun updateAmount(id: Long, amount: Int) = viewModelScope.launch {
        dao.updateAmount(id, amount)
    }
    fun updateLinkedInventoryItemId(id: Long, linkedInventoryItem: InventoryItem) = viewModelScope.launch {
        dao.updateLinkedInventoryItemId(id, linkedInventoryItem.id,
                                        linkedInventoryItem.name,
                                        linkedInventoryItem.extraInfo)
    }
    fun deleteAll() = viewModelScope.launch {
        dao.deleteAll()
    }
    fun emptyTrash() = viewModelScope.launch {
        dao.emptyTrash()
    }
    fun delete(vararg ids: Long) = viewModelScope.launch {
        dao.delete(*ids)
    }
    fun undoDelete() = viewModelScope.launch {
        dao.undoDelete()
    }
}
