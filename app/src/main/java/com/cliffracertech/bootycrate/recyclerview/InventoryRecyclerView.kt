/* Copyright 2020 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracertech.bootycrate.recyclerview

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.cliffracertech.bootycrate.InventoryItemView
import com.cliffracertech.bootycrate.database.InventoryItem
import com.cliffracertech.bootycrate.inventoryViewModel
import java.util.*

/**
 * A RecyclerView to display the data provided by an InventoryViewModel
 */
class InventoryRecyclerView(context: Context, attrs: AttributeSet) :
    ExpandableSelectableRecyclerView<InventoryItem>(context, attrs)
{
    override val diffUtilCallback = InventoryItemDiffUtilCallback()
    override val adapter = InventoryAdapter()
    override val viewModel = inventoryViewModel(context)

    init { itemAnimator.registerAdapterDataObserver(adapter) }

    /**
     * A RecyclerView.Adapter to display the contents of a list of inventory items.
     *
     * InventoryAdapter is a subclass of ExpandableSelectableItemAdapter using
     * InventoryItemViewHolder instances to represent inventory items. Its over-
     * rides of onBindViewHolder make use of the InventoryItem.Field values passed
     * by InventoryItemDiffUtilCallback to support partial binding. Note that
     * InventoryAdapter assumes that any payloads passed to it are of the type
     * EnumSet<InventoryItem.Field>. If a payload of another type is passed to it,
     * an exception will be thrown.
     */
    inner class InventoryAdapter : ExpandableSelectableItemAdapter<InventoryItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            InventoryItemViewHolder(InventoryItemView(context, itemAnimator.animatorConfig))

        override fun onBindViewHolder(holder: InventoryItemViewHolder, position: Int) {
            holder.view.update(holder.item)
            super.onBindViewHolder(holder, position)
        }

        override fun onBindViewHolder(
            holder: InventoryItemViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            if (payloads.size == 0)
                return onBindViewHolder(holder, position)
            val unhandledChanges = mutableListOf<Any>()

            for (payload in payloads) {
                if (payload is EnumSet<*>) {
                    val item = getItem(position)
                    @Suppress("UNCHECKED_CAST")
                    val changes = payload as EnumSet<InventoryItem.Field>
                    val ui = holder.view.ui
                    val detailsUi = holder.view.detailsUi

                    if (changes.contains(InventoryItem.Field.Name) &&
                        ui.nameEdit.text.toString() != item.name)
                            ui.nameEdit.setText(item.name)
                    if (changes.contains(InventoryItem.Field.ExtraInfo) &&
                        ui.extraInfoEdit.text.toString() != item.extraInfo)
                            ui.extraInfoEdit.setText(item.extraInfo)
                    if (changes.contains(InventoryItem.Field.Color) &&
                        ui.checkBox.colorIndex != item.color)
                            ui.checkBox.colorIndex = item.color
                    if (changes.contains(InventoryItem.Field.Amount) &&
                        ui.amountEdit.value != item.amount)
                            ui.amountEdit.value = item.amount
                    if (changes.contains(InventoryItem.Field.IsExpanded) &&
                        holder.view.isExpanded != item.isExpanded)
                            holder.view.setExpanded(item.isExpanded)
                    if (changes.contains(InventoryItem.Field.IsSelected) &&
                        holder.view.isInSelectedState != item.isSelected)
                            holder.view.setSelectedState(item.isSelected)
                    if (changes.contains(InventoryItem.Field.AddToShoppingList) &&
                        detailsUi.addToShoppingListCheckBox.isChecked != item.addToShoppingList)
                            detailsUi.addToShoppingListCheckBox.isChecked = item.addToShoppingList
                    if (changes.contains(InventoryItem.Field.AddToShoppingListTrigger) &&
                        detailsUi.addToShoppingListTriggerEdit.value != item.addToShoppingListTrigger)
                            detailsUi.addToShoppingListTriggerEdit.value = item.addToShoppingListTrigger
                }
                else unhandledChanges.add(payload)
            }
            if (unhandledChanges.isNotEmpty())
                super.onBindViewHolder(holder, position, unhandledChanges)
        }
    }

    /**
     * A ExpandableSelectableItemViewHolder that wraps an instance of InventoryItemView.
     *
     * InventoryItemViewHolder is a subclass of ExpandableSelectableItemViewHolder
     * that holds an instance of InventoryItemView to display the data for an
     * InventoryItem. It also connects changes in the InventoryItemView extra
     * details section to view model update calls.
     */
    inner class InventoryItemViewHolder(val view: InventoryItemView) :
        ExpandableSelectableItemViewHolder(view) {

        init {
            view.detailsUi.addToShoppingListCheckBox.setOnCheckedChangeListener { _, checked ->
                viewModel.updateAddToShoppingList(item.id, checked)
            }
            view.detailsUi.addToShoppingListTriggerEdit.onValueChangedListener = { value ->
                if (adapterPosition != -1)
                    viewModel.updateAddToShoppingListTrigger(item.id, value)
            }
        }
    }

    /**
     * Computes a diff between two inventory item lists.
     *
     * InventoryItemDiffUtilCallback uses the ids of inventory items to determine
     * if they are the same or not. If they are the same, the change payload will
     * be an instance of EnumSet<InventoryItem.Field> that contains the Inventory-
     * Item.Field values for all of the fields that were changed.
     */
    class InventoryItemDiffUtilCallback : DiffUtil.ItemCallback<InventoryItem>() {
        private val listChanges = mutableMapOf<Long, EnumSet<InventoryItem.Field>>()
        private val itemChanges = EnumSet.noneOf(InventoryItem.Field::class.java)

        override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem) =
            itemChanges.apply {
                clear()
                if (newItem.name != oldItem.name)             add(InventoryItem.Field.Name)
                if (newItem.extraInfo != oldItem.extraInfo)   add(InventoryItem.Field.ExtraInfo)
                if (newItem.color != oldItem.color)           add(InventoryItem.Field.Color)
                if (newItem.amount != oldItem.amount)         add(InventoryItem.Field.Amount)
                if (newItem.isExpanded != oldItem.isExpanded) add(InventoryItem.Field.IsExpanded)
                if (newItem.isSelected != oldItem.isSelected) add(InventoryItem.Field.IsSelected)
                if (newItem.addToShoppingList != oldItem.addToShoppingList)
                    add(InventoryItem.Field.AddToShoppingList)
                if (newItem.addToShoppingListTrigger != oldItem.addToShoppingListTrigger)
                    add(InventoryItem.Field.AddToShoppingListTrigger)

                if (!isEmpty())
                    listChanges[newItem.id] = EnumSet.copyOf(this)
            }.isEmpty()

        override fun getChangePayload(oldItem: InventoryItem, newItem: InventoryItem) =
            listChanges.remove(newItem.id)
    }
}