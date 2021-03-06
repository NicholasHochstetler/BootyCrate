/* Copyright 2021 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracertech.bootycrate.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.fragment.app.activityViewModels
import com.cliffracertech.bootycrate.*
import com.cliffracertech.bootycrate.database.InventoryItem
import com.cliffracertech.bootycrate.database.InventoryViewModel
import com.cliffracertech.bootycrate.database.ShoppingListViewModel
import com.cliffracertech.bootycrate.databinding.InventoryFragmentBinding
import com.cliffracertech.bootycrate.databinding.MainActivityBinding
import com.cliffracertech.bootycrate.recyclerview.InventoryRecyclerView
import com.cliffracertech.bootycrate.utils.NewInventoryItemDialog
import com.cliffracertech.bootycrate.view.RecyclerViewActionBar

/**
 * A fragment to display and modify the user's inventory.
 *
 * InventoryFragment is a RecyclerViewFragment subclass to view and modify the
 * user's inventory using an InventoryRecyclerView. It does almost nothing
 * different from RecyclerViewFragment apart from implementing its abstract
 * properties with values suitable for display of an InventoryRecyclerView,
 * and using its own action mode callback.
 */
@Keep class InventoryFragment: RecyclerViewFragment<InventoryItem>() {
    override val viewModel: InventoryViewModel by activityViewModels()
    private val shoppingListViewModel: ShoppingListViewModel by activityViewModels()
    override var recyclerView: InventoryRecyclerView? = null
    override lateinit var collectionName: String
    override val actionModeCallback = InventoryActionModeCallback()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = InventoryFragmentBinding.inflate(inflater, container, false).apply {
        recyclerView = inventoryRecyclerView
        collectionName = inflater.context.getString(R.string.inventory_item_collection_name)
    }.root

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView = null
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        if (item.itemId == R.id.add_to_shopping_list_button) {
            shoppingListViewModel.addFromSelectedInventoryItems()
            recyclerView?.selection?.clear()
            true
        } else super.onOptionsItemSelected(item)

    override fun showsCheckoutButton() = false
    override fun onActiveStateChanged(isActive: Boolean, activityUi: MainActivityBinding) {
        super.onActiveStateChanged(isActive, activityUi)
        activityUi.actionBar.optionsMenu.setGroupVisible(R.id.inventory_view_menu_group, isActive)
        if (!isActive) return
        activityUi.addButton.setOnClickListener {
            val activity = this.activity ?: return@setOnClickListener
            NewInventoryItemDialog(activity).show(activity.supportFragmentManager, null)
        }
        activityUi.checkoutButton.checkoutCallback = null
    }

    /** An override of SelectionActionModeCallback that alters the visibility of menu items specific to inventory items. */
    inner class InventoryActionModeCallback : SelectionActionModeCallback() {
        override fun onStart(actionMode: RecyclerViewActionBar.ActionMode, actionBar: RecyclerViewActionBar) {
            super.onStart(actionMode, actionBar)
            actionBar.optionsMenu.setGroupVisible(R.id.inventory_view_action_mode_menu_group, true)
        }

        override fun onFinish(actionMode: RecyclerViewActionBar.ActionMode, actionBar: RecyclerViewActionBar) =
            actionBar.optionsMenu.setGroupVisible(R.id.inventory_view_action_mode_menu_group, false)
    }
}