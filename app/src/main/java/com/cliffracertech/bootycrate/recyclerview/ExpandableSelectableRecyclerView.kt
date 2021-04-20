/* Copyright 2020 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracertech.bootycrate.recyclerview

import android.content.Context
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.cliffracertech.bootycrate.database.ExpandableSelectableItem
import com.cliffracertech.bootycrate.ExpandableSelectableItemView
import com.cliffracertech.bootycrate.ExpandableSelectableItemViewModel
import com.cliffracertech.bootycrate.utils.AnimatorConfig

/**
 * A ViewModelRecyclerView subclass that enables multi-selection and expansion of items.
 *
 * ExpandableSelectableRecyclerView extends BootyCrateRecyclerView via the use
 * of an ExpandableSelectableItemViewModel along with the new function setExpandedItem.
 * Its selection property provides an interface for manipulating the recycler
 * view selection (see the documentation for the inner class Selection for more
 * details) as well as the function setExpandedItem to change or set to null
 * the currently expanded item. It also utilizes its own custom view holder to
 * enforce the use of an ExpandableSelectableItemView, and a custom adapter
 * that in turn enforces the use of ExpandableSelectableItemViewHolder.
 */
@Suppress("LeakingThis")
abstract class ExpandableSelectableRecyclerView<Entity: ExpandableSelectableItem>(
    context: Context,
    attrs: AttributeSet
) : BootyCrateRecyclerView<Entity>(context, attrs) {
    abstract override val viewModel: ExpandableSelectableItemViewModel<Entity>
    protected val itemAnimator = ExpandableItemAnimator(this, AnimatorConfig.translation(context))

    val selection = Selection()

    init {
        addItemDecoration(ItemSpacingDecoration(context))
        setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        setItemAnimator(itemAnimator)
    }

    fun setExpandedItem(pos: Int?) {
        viewModel.setExpandedId(if (pos == null) null
                                else adapter.currentList[pos].id)
        itemAnimator.notifyExpandedItemChanged(pos)
    }

    /**
     * A class that provides an interface for manipulating the selection of the parent recycler view.
     *
     * Selection is a memberless class whose purpose is to make the manipulation
     * of the recycler view selection more idiomatic (e.g. recyclerView.selection.add()
     * instead of recyclerView.addToSelection()). The size of the selection can be
     * queried through the properties size and isEmpty, as well as sizeLiveData in
     * case an observable selection size is desired.
     *
     * The contents of the selection are modified for single items through the
     * self-explanatory functions add, remove, and toggle, which accept the stable
     * id of the item being operated on. The function clear will erase the
     * selection entirely.
     */
    inner class Selection {
        val itemsLiveData get() = viewModel.selectedItems
        val items get() = itemsLiveData.value
        val size get() = items?.size ?: 0
        val isEmpty get() = size == 0
        val isNotEmpty get() = size != 0

        fun addAll() = viewModel.selectAll()
        fun add(id: Long) = viewModel.updateIsSelected(id, true)
        fun remove(id: Long) = viewModel.updateIsSelected(id, false)
        fun toggle(id: Long) = viewModel.toggleIsSelected(id)
        fun clear() = viewModel.clearSelection()
    }

    /**
     * An abstract (due to not implementing onCreateViewHolder) subclass of
     * BootyCrateItemAdapter that enforces the use of ExpandableSelectableItemViewHolder.
     */
    abstract inner class ExpandableSelectableItemAdapter<VHType: ExpandableSelectableItemViewHolder> :
        BootyCrateRecyclerView<Entity>.BootyCrateItemAdapter<VHType>()
    {
        override fun onBindViewHolder(holder: VHType, position: Int) {
            if (holder.item.isExpanded)
                itemAnimator.notifyExpandedItemChanged(position)
            super.onBindViewHolder(holder, position)
        }
    }

    /**
     * A ViewHolder subclass that wraps an instance of ExpandableSelectableItemView.
     *
     * ExpandableSelectableItemView updates the onClickListeners of the wrapped item
     * view to enable the selection and expansion of the items.
     */
    open inner class ExpandableSelectableItemViewHolder(
        view: ExpandableSelectableItemView<Entity>
    ) : BootyCrateRecyclerView<Entity>.BootyCrateItemViewHolder(view) {
        init {
            val onClick = OnClickListener { if (!selection.isEmpty) selection.toggle(itemId) }
            val onLongClick = OnLongClickListener { selection.toggle(itemId); true }

            view.apply {
                startAnimationsImmediately = false
                setOnClickListener(onClick)
                ui.nameEdit.setOnClickListener(onClick)
                ui.extraInfoEdit.setOnClickListener(onClick)
                ui.amountEdit.ui.valueEdit.setOnClickListener(onClick)

                setOnLongClickListener(onLongClick)
                ui.nameEdit.setOnLongClickListener(onLongClick)
                ui.extraInfoEdit.setOnLongClickListener(onLongClick)
                ui.amountEdit.ui.valueEdit.setOnLongClickListener(onLongClick)
                ui.editButton.setOnClickListener {
                    setExpandedItem(if (!view.isExpanded) adapterPosition else null)
                }
            }
        }
    }
}