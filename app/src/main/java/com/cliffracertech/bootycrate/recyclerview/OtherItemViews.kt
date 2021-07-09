/* Copyright 2021 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracertech.bootycrate.recyclerview

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import androidx.annotation.CallSuper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.cliffracertech.bootycrate.R
import com.cliffracertech.bootycrate.database.BootyCrateItem
import com.cliffracertech.bootycrate.database.InventoryItem
import com.cliffracertech.bootycrate.database.ShoppingListItem
import com.cliffracertech.bootycrate.databinding.BootyCrateItemBinding
import com.cliffracertech.bootycrate.databinding.InventoryItemBinding
import com.cliffracertech.bootycrate.databinding.InventoryItemDetailsBinding
import com.cliffracertech.bootycrate.utils.AnimatorConfig
import com.cliffracertech.bootycrate.utils.SoftKeyboard
import com.cliffracertech.bootycrate.utils.applyConfig

/**
 * A layout to display the data for a BootyCrateItem.
 *
 * BootyCrateItemView displays the data for an instance of BootyCrateItem. The
 * displayed data can be updated for a new item with the function update.
 *
 * By default BootyCrateItemView inflates itself with the contents of
 * R.layout.booty_crate_item.xml and initializes its BootyCrateItemBinding
 * member ui. In case this layout needs to be overridden in a subclass, the
 * BootyCrateItemView can be constructed with the parameter useDefaultLayout
 * equal to false. If useDefaultLayout is false, it will be up to the subclass
 * to inflate the desired layout and initialize the member ui with an instance
 * of a BootyCrateItemBinding. If the ui member is not initialized then a
 * kotlin.UninitializedPropertyAccessException will be thrown.
 */
@Suppress("LeakingThis")
@SuppressLint("ViewConstructor")
open class BootyCrateItemView<T: BootyCrateItem>(
    context: Context,
    useDefaultLayout: Boolean = true,
) : ConstraintLayout(context) {

    lateinit var ui: BootyCrateItemBinding

    init {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                 ViewGroup.LayoutParams.WRAP_CONTENT)
        if (useDefaultLayout)
            ui = BootyCrateItemBinding.inflate(LayoutInflater.from(context), this)

        val verticalPadding = resources.getDimensionPixelSize(R.dimen.recycler_view_item_vertical_padding)
        setPadding(0, verticalPadding, 0, verticalPadding)
    }

    @CallSuper open fun update(item: T) {
        ui.nameEdit.setText(item.name)
        setExtraInfoText(item.extraInfo)
        val colorIndex = item.color.coerceIn(BootyCrateItem.Colors.indices)
        ui.checkBox.initColorIndex(colorIndex)
        ui.amountEdit.initValue(item.amount)
    }

    /** Update the text of the extra info edit, while also updating the extra info
     * edit's visibility, if needed, to account for the new text. It is recommended
     * to use this function rather than changing the extra info edit's text directly
     * to ensure that its visibility is set correctly. */
    fun setExtraInfoText(newText: String) {
        ui.extraInfoEdit.setText(newText)
        if (ui.extraInfoEdit.text.isNullOrBlank() == ui.extraInfoEdit.isVisible)
            ui.extraInfoEdit.isVisible = !ui.extraInfoEdit.isVisible
    }
}



/**
 * An ExpandableSelectableItemView to display the contents of a shopping list item.
 *
 * ShoppingListItemView is a ExpandableSelectableItemView subclass that
 * displays the data of a ShoppingListItem instance. It has an update override
 * that updates the check state of the checkbox, it overrides the setExpanded
 * function with an implementation that toggles the checkbox between its normal
 * checkbox mode and its color edit mode, and it has a convenience method
 * setStrikeThroughEnabled that will set the strike through state for both the
 * name and extra info edit at the same time.
 */
@SuppressLint("ViewConstructor")
class ShoppingListItemView(context: Context, animatorConfig: AnimatorConfig? = null) :
    ExpandableSelectableItemView<ShoppingListItem>(context, animatorConfig)
{
    init { ui.checkBox.onCheckedChangedListener = ::setStrikeThroughEnabled }

    override fun update(item: ShoppingListItem) {
        ui.checkBox.initIsChecked(item.isChecked)
        setStrikeThroughEnabled(enabled = item.isChecked, animate = false)
        super.update(item)
    }

    override fun onExpandedChanged(expanded: Boolean, animate: Boolean) {
        ui.checkBox.setInColorEditMode(expanded, animate)
    }

    fun setStrikeThroughEnabled(enabled: Boolean) = setStrikeThroughEnabled(enabled, true)
    private fun setStrikeThroughEnabled(enabled: Boolean, animate: Boolean) {
        ui.nameEdit.setStrikeThroughEnabled(enabled, animate)
        ui.extraInfoEdit.setStrikeThroughEnabled(enabled, animate)
    }
}



/**
 * An ExpandableSelectableItemView to display the contents of an inventory item.
 *
 * InventoryItemView is a ExpandableSelectableItemView subclass that displays
 * the data of an InventoryItem instance. It has an update override for the
 * extra fields that InventoryItem adds to its parent class, and has a
 * setExpanded override that also shows or hides these extra fields.
 */
@SuppressLint("ViewConstructor")
class InventoryItemView(context: Context, animatorConfig: AnimatorConfig? = null) :
    ExpandableSelectableItemView<InventoryItem>(context, animatorConfig, useDefaultLayout = false)
{
    val detailsUi: InventoryItemDetailsBinding
    private var pendingDetailsAnimation: ViewPropertyAnimator? = null

    init {
        val tempUi = InventoryItemBinding.inflate(LayoutInflater.from(context), this)
        ui = BootyCrateItemBinding.bind(tempUi.root)
        detailsUi = InventoryItemDetailsBinding.bind(tempUi.root)
        ui.editButton.setOnClickListener { toggleExpanded() }
        ui.checkBox.setInColorEditMode(true, animate = false)
        ui.amountEdit.minValue = 0
        this.animatorConfig = animatorConfig
    }

    override fun update(item: InventoryItem) {
        detailsUi.autoAddToShoppingListCheckBox.initIsChecked(item.autoAddToShoppingList)
        detailsUi.autoAddToShoppingListCheckBox.initColorIndex(item.color)
        detailsUi.autoAddToShoppingListAmountEdit.initValue(item.autoAddToShoppingListAmount)
        super.update(item)
    }

    override fun onExpandedChanged(expanded: Boolean, animate: Boolean) {
        if (!expanded && detailsUi.autoAddToShoppingListAmountEdit.ui.valueEdit.isFocused)
            SoftKeyboard.hide(this)
        val view = detailsUi.inventoryItemDetailsLayout
        if (!animate)
            view.isVisible = expanded
        else {
            val translationAmount = ui.checkBox.height * (if (expanded) 1f else -1f)

            if (!expanded) overlay.add(view)
            else view.translationY = -translationAmount
            view.alpha = if (expanded) 0f else 1f
            view.isVisible = true

            val anim = view.animate().applyConfig(animatorConfig)
                .withLayer().alpha(if (expanded) 1f else 0f)
                .translationY(if (expanded) 0f else translationAmount)
            if (!expanded) anim.withEndAction {
                view.translationY = 0f
                view.isVisible = false
                overlay.remove(view)
                addView(view)
            }
            pendingDetailsAnimation = anim
        }
    }

    override fun runPendingAnimations() {
        super.runPendingAnimations()
        pendingDetailsAnimation?.start()
        pendingDetailsAnimation = null
    }
}