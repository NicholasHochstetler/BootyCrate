/* Copyright 2021 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracertech.bootycrate.utils

import android.view.View
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.*
import com.cliffracertech.bootycrate.R
import com.cliffracertech.bootycrate.database.BootyCrateItem
import com.cliffracertech.bootycrate.database.InventoryItem
import com.cliffracertech.bootycrate.database.ShoppingListItem
import com.cliffracertech.bootycrate.recyclerview.ExpandableSelectableItemView
import com.cliffracertech.bootycrate.recyclerview.ExpandableSelectableRecyclerView
import com.cliffracertech.bootycrate.recyclerview.InventoryItemView
import com.google.common.truth.Truth.assertThat
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher

/** A ViewAction that allows direct operation on a view.
 * Thanks to the author of this blog post for the idea.
 * https://medium.com/android-news/call-view-methods-when-testing-by-espresso-and-kotlin-in-android-781262f7348e */
fun <T>doStuff(method: (view: T) -> Unit): ViewAction {
    return object: ViewAction {
        override fun getDescription() = method.toString()
        override fun getConstraints() = isEnabled()
        @Suppress("UNCHECKED_CAST")
        override fun perform(uiController: UiController?, view: View) =
            method(view as? T ?: throw IllegalStateException("The matched view is null or not of type T"))
    }
}

/** Perform the given actions on the recycler view item view at the given position. */
fun actionsOnItemAtPosition(pos: Int, vararg actions: ViewAction) = object : ViewAction {
    override fun getConstraints() = allOf(isAssignableFrom(RecyclerView::class.java), isDisplayed())
    override fun getDescription() = "Perform ${actions.asList()} on the view of the item at pos $pos."
    override fun perform(uiController: UiController, view: View) {
        val vh = (view as RecyclerView).findViewHolderForAdapterPosition(pos)
        assertThat(vh).isNotNull()
        for (action in actions)
            action.perform(uiController, vh!!.itemView)
    }
}

fun actionOnChildWithId(viewId: Int, vararg actions: ViewAction) = object : ViewAction {
    override fun getConstraints() = null
    override fun getDescription() = "Perform an action on a child view with specified id."
    override fun perform(uiController: UiController, view: View) {
        val child = view.findViewById<View>(viewId)
        for (action in actions) action.perform(uiController, child)
    }
}

fun clickEditButton() = actionOnChildWithId(R.id.editButton, click())
fun clickCheckBox() = actionOnChildWithId(R.id.checkBox, click())
fun clickAddToShoppingListCheckBox() = actionOnChildWithId(R.id.autoAddToShoppingListCheckBox, click())
fun onAmount(vararg viewActions: ViewAction) = actionOnChildWithId(R.id.amountEdit, *viewActions)
fun onAddToShoppingListTrigger(vararg viewActions: ViewAction) =
    actionOnChildWithId(R.id.autoAddToShoppingListAmountEdit, *viewActions)
fun onIncreaseButton(vararg viewActions: ViewAction) = actionOnChildWithId(R.id.increaseButton, *viewActions)
fun onDecreaseButton(vararg viewActions: ViewAction) = actionOnChildWithId(R.id.decreaseButton, *viewActions)
fun replaceValueEditText(text: String) = actionOnChildWithId(R.id.valueEdit, click(), replaceText(text))

fun onPopupView(viewMatcher: Matcher<View>) = onView(viewMatcher).inRoot(isPlatformPopup())

/** Assert that the view is an ExpandableSelectableRecyclerView with only one expanded item at index expandedIndex. */
fun onlyExpandedIndexIs(expandedIndex: Int?) = ViewAssertion { view, e ->
    if (view == null) throw e!!
    assertThat(view).isInstanceOf(ExpandableSelectableRecyclerView::class.java)
    val it = view as ExpandableSelectableRecyclerView<*>
    val expandedViewHeight = if (expandedIndex == null) Integer.MAX_VALUE else
        it.findViewHolderForAdapterPosition(expandedIndex)?.itemView?.height ?: throw e
    for (i in 0 until it.adapter.itemCount) {
        val vh = it.findViewHolderForAdapterPosition(i)
        if (i != expandedIndex) assertThat(vh?.itemView?.height).isLessThan(expandedViewHeight)
        else                    assertThat(vh?.itemView?.height).isEqualTo(expandedViewHeight)
    }
}

/** Asserts that the view is an ExpandableSelectableRecyclerView, with the items
 * at the specified indices all selected, and with no other selected items. */
fun onlySelectedIndicesAre(vararg indices: Int) = ViewAssertion { view, e ->
    if (view == null) throw e!!
    assertThat(view).isInstanceOf(ExpandableSelectableRecyclerView::class.java)
    val it = view as ExpandableSelectableRecyclerView<*>
    for (i in 0 until it.adapter.itemCount) {
        val vh = it.findViewHolderForAdapterPosition(i)!! as ExpandableSelectableRecyclerView<*>.ViewHolder
        val shouldBeSelected = i in indices
        assertThat(vh.item.isSelected).isEqualTo(shouldBeSelected)
        val itemView = vh.itemView as ExpandableSelectableItemView<*>
        assertThat(itemView.isInSelectedState).isEqualTo(shouldBeSelected)
    }
}

/** Asserts that the view is an ExpandableSelectableRecyclerView that
    contains only the specified items of type T, in the order given. */
open class onlyShownItemsAre<T: BootyCrateItem>(vararg items: T) : ViewAssertion {
    private val items = items.asList()

    @CallSuper
    open fun assertItemFromViewMatchesOriginalItem(view: ExpandableSelectableItemView<T>, item: T) {
        assertThat(view.ui.nameEdit.text.toString()).isEqualTo(item.name)
        assertThat(view.ui.extraInfoEdit.text.toString()).isEqualTo(item.extraInfo)
        assertThat(view.ui.checkBox.colorIndex).isEqualTo(item.color)
        assertThat(view.ui.amountEdit.value).isEqualTo(item.amount)
    }

    override fun check(view: View?, noViewFoundException: NoMatchingViewException?) {
        if (view == null) throw noViewFoundException!!
        assertThat(view).isInstanceOf(ExpandableSelectableRecyclerView::class.java)
        val it = view as ExpandableSelectableRecyclerView<*>
        assertThat(items.size).isEqualTo(it.adapter.itemCount)
        for (i in 0 until it.adapter.itemCount) {
            val vh = it.findViewHolderForAdapterPosition(i)
            assertThat(vh).isNotNull()
            val itemView = vh!!.itemView as ExpandableSelectableItemView<T>
            assertThat(itemView).isNotNull()
            assertThat(assertItemFromViewMatchesOriginalItem(itemView, items[i]))
        }
    }
}

/** Asserts that the matching view is an ExpandableSelectableRecyclerView subclass
 * that only shows the provided shopping list items, in the order given. */
class onlyShownShoppingListItemsAre(vararg items: ShoppingListItem) :
    onlyShownItemsAre<ShoppingListItem>(*items)
{
    override fun assertItemFromViewMatchesOriginalItem(
        view: ExpandableSelectableItemView<ShoppingListItem>,
        item: ShoppingListItem
    ) {
        super.assertItemFromViewMatchesOriginalItem(view, item)
        assertThat(view.ui.checkBox.isChecked).isEqualTo(item.isChecked)
    }
}

/** Asserts that the matching view is an ExpandableSelectableRecyclerView subclass
 * that only shows the provided inventory items, in the order given. */
class onlyShownInventoryItemsAre(vararg items: InventoryItem) :
    onlyShownItemsAre<InventoryItem>(*items)
{
    override fun assertItemFromViewMatchesOriginalItem(
        view: ExpandableSelectableItemView<InventoryItem>,
        item: InventoryItem
    ) {
        super.assertItemFromViewMatchesOriginalItem(view, item)
        val view = view as InventoryItemView
        assertThat(view.detailsUi.autoAddToShoppingListCheckBox.isChecked).isEqualTo(item.autoAddToShoppingList)
        assertThat(view.detailsUi.autoAddToShoppingListAmountEdit.value).isEqualTo(item.autoAddToShoppingListAmount)
    }
}