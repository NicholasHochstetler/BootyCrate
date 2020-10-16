/* Copyright 2020 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracermerchant.bootycrate

import android.animation.LayoutTransition
import android.app.Activity
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.integer_edit_layout.view.*
import kotlinx.android.synthetic.main.inventory_item_details_layout.view.*
import kotlinx.android.synthetic.main.inventory_item_details_layout.view.collapseButton
import kotlinx.android.synthetic.main.inventory_item_layout.view.*
import kotlinx.android.synthetic.main.inventory_item_layout.view.editButton
import kotlinx.android.synthetic.main.inventory_item_layout.view.extraInfoEdit
import kotlinx.android.synthetic.main.inventory_item_layout.view.nameEdit

/** A layout to display the contents of an inventory item.
 *
 *  InventoryItemView is a ConstraintLayout subclass that inflates a layout to
 *  display the data of an InventoryItem instance. Its update(InventoryItem)
 *  function updates the contained views with the information of the Inventory-
 *  Item instance. Its expand and collapse functions allow for an optional anim-
 *  ation. */
class InventoryItemView(context: Context) :
    ConstraintLayout(ContextThemeWrapper(context, R.style.RecyclerViewItemStyle))
{
    val isExpanded get() = _isExpanded
    private var _isExpanded = false
    private val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
    private val editButtonIconController: AnimatedIconController

    init {
        inflate(context, R.layout.inventory_item_layout, this)
        editButtonIconController = AnimatedImageViewController(editButton)
        editButtonIconController.addTransition(
            editButtonIconController.addState("edit"), editButtonIconController.addState("more_options"),
            ContextCompat.getDrawable(context, R.drawable.animated_edit_to_more_options_icon) as AnimatedVectorDrawable,
            ContextCompat.getDrawable(context, R.drawable.animated_more_options_to_edit_icon) as AnimatedVectorDrawable)

        editButton.setOnClickListener {
            if (isExpanded) //TODO Implement more options menu
            else            expand()
        }
        collapseButton.setOnClickListener{ collapse() }

        layoutTransition = delaylessLayoutTransition()
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                 ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun update(item: InventoryItem, isExpanded: Boolean = false) {
        nameEdit.setText(item.name)
        extraInfoEdit.setText(item.extraInfo)
        val colorIndex = item.color.coerceIn(ViewModelItem.Colors.indices)
        colorEdit.drawable.setTint(ViewModelItem.Colors[colorIndex])
        inventoryAmountEdit.initCurrentValue(item.amount)

        addToShoppingListCheckBox.isChecked = item.addToShoppingList
        addToShoppingListTriggerEdit.initCurrentValue(item.addToShoppingListTrigger)

        setExpanded(isExpanded)
    }

    fun expand() = setExpanded(true)
    fun collapse() = setExpanded(false)
    fun setExpanded(expanded: Boolean = true) {
        if (!expanded && nameEdit.isFocused || extraInfoEdit.isFocused ||
            inventoryAmountEdit.valueEdit.isFocused ||
            addToShoppingListTriggerEdit.valueEdit.isFocused)
                imm?.hideSoftInputFromWindow(windowToken, 0)

        _isExpanded = expanded
        nameEdit.isEditable = expanded
        inventoryAmountEdit.isEditable = expanded
        extraInfoEdit.isEditable = expanded
        addToShoppingListTriggerEdit.isEditable = expanded

        editButtonIconController.setState(if (expanded) "more_options" else "edit")

        val newVisibility = if (expanded) View.VISIBLE
                            else          View.GONE
        inventoryItemDetailsGroup.visibility = newVisibility
        if (extraInfoEdit.text.isNullOrBlank())
            extraInfoEdit.visibility = newVisibility

        // For some reason, expanding an inventory item whose extra info is not blank
        // was causing a flicker. Shopping list items seemed to be okay regardless of
        // whether its extra info was blank. The difference was tracked down to Shop-
        // pingListItem's setAmountEditable. It appears that changing the layout params
        // of a view and requesting a layout for it stops this flicker from occurring.
        // Given that the bug seems to originate from somewhere deep in Android code,
        // having this useless layout parameter changing and requested layout is an
        // easy and performance negligible way to prevent the bug.
        spacer.layoutParams.width = if (expanded) 1 else 0
        spacer.requestLayout()
    }
}