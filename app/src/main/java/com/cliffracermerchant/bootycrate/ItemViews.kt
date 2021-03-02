/* Copyright 2020 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracermerchant.bootycrate

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.cliffracermerchant.bootycrate.databinding.InventoryItemBinding
import com.cliffracermerchant.bootycrate.databinding.InventoryItemDetailsBinding
import com.cliffracermerchant.bootycrate.databinding.ViewModelItemBinding

/**
 * A layout to display the data for a ViewModelItem.
 *
 * ViewModelItemView displays the data for an instance of ViewModelItem. The
 * displayed data can be updated for a new item with the function update.
 *
 * By default ViewModelItemView inflates itself with the contents of R.layout.-
 * view_model_item_view.xml and initializes its ViewModelItemBinding member ui.
 * In case this layout needs to be overridden in a subclass, the ViewModelItem-
 * View can be constructed with the parameter useDefaultLayout equal to false.
 * If useDefaultLayout is false, it will be up to the subclass to inflate the
 * desired layout and initialize the member ui with an instance of a ViewModel-
 * ItemBinding. If the ui member is not initialized then a kotlin.Uninitialized-
 * PropertyAccessException will be thrown.
 */
@Suppress("LeakingThis")
@SuppressLint("ViewConstructor")
open class ViewModelItemView<Entity: ViewModelItem>(
    context: Context,
    useDefaultLayout: Boolean = true,
) : ConstraintLayout(context) {

    protected val inputMethodManager = inputMethodManager(context)
    protected var itemIsLinked = false

    lateinit var ui: ViewModelItemBinding

    init {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                 ViewGroup.LayoutParams.WRAP_CONTENT)
        if (useDefaultLayout)
            ui = ViewModelItemBinding.inflate(LayoutInflater.from(context), this)

        val verticalPadding = resources.getDimensionPixelSize(R.dimen.recycler_view_item_vertical_padding)
        setPadding(0, verticalPadding, 0, verticalPadding)
    }

    @CallSuper open fun update(item: Entity) {
        ui.nameEdit.setText(item.name)
        ui.extraInfoEdit.setText(item.extraInfo)
        val colorIndex = item.color.coerceIn(ViewModelItem.Colors.indices)
        ui.checkBox.setColorIndex(colorIndex, animate = false)
        ui.amountEdit.initValue(item.amount)
        itemIsLinked = item.linkedItemId != null
    }
}

/**
 * A ViewModelItemView subclass that provides an interface for a selection and expansion of the view.
 *
 * ExpandableSelectableItemView will display the information of an instance of
 * ExpandableSelectableItem, while also providing an interface for expansion
 * and selection. The update override will update the view to reflect the
 * selection and expansion state of the ExpandableSelectableItem passed to it.
 *
 * The interface for selection and deselection consists of the functions
 * select, deselect, and setSelectedState. With the default background these
 * functions will give the view a surrounding gradient outline or hide the
 * outline depending on the item's selection state. Unless setSelectedState is
 * called with the parameter animate set to false, the change in selection
 * state will be animated with a fade in or out animation.
 *
 * The interface for item expansion consists of expand, collapse, setExpanded,
 * and toggleExpanded. If subclasses need to alter the visibility of additional
 * views during expansion or collapse, they can override the function
 * onExpandedChanged with their additional changes. Like setSelectedState, set-
 * Expanded will animated the changes inside the view unless it is called with
 * the parameter animate equal to false.
 *
 * In order to allow for easier synchronization with concurrent animations out-
 * side the view, ExpandableSelectableItemView has the properties animatorCon-
 * fig, startAnimationsImmediately, and pendingAnimations. The constructor
 * parameter and property animatorConfig will determine the animator config
 * used for the view's internal animations. The default value of AnimatorCon-
 * figs.translation can be overridden in order to make sure the view's anima-
 * tions use the same config as others outside the view. The property start-
 * AnimationsImmediately determines whether the animations prepared during set-
 * Expanded will be started immediately. If it is set to false, the animations
 * will instead be stored. In this case it is up to the containing view to play
 * these animations at the same time as its own using the function runPending-
 * Animations.
 */
@SuppressLint("ViewConstructor")
open class ExpandableSelectableItemView<Entity: ExpandableSelectableItem>(
    context: Context,
    val animatorConfig: AnimatorConfig = AnimatorConfig.translation,
    useDefaultLayout: Boolean = true,
) : ViewModelItemView<Entity>(context, useDefaultLayout) {

    val isExpanded get() = _isExpanded
    private var _isExpanded = false
    private val gradientOutline: GradientDrawable

    var startAnimationsImmediately = true
    private val pendingAnimations = mutableListOf<Animator>()
    fun runPendingAnimations() { for (anim in pendingAnimations)
                                     anim.start()
                                 pendingAnimations.clear() }

    init {
        val background = ContextCompat.getDrawable(context, R.drawable.recycler_view_item_background) as LayerDrawable
        gradientOutline = (background.getDrawable(1) as LayerDrawable).getDrawable(0) as GradientDrawable
        gradientOutline.setTintList(null)
        gradientOutline.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        val colors = IntArray(5)
        colors[0] = ContextCompat.getColor(context, R.color.colorInBetweenPrimaryAccent2)
        colors[1] = ContextCompat.getColor(context, R.color.colorInBetweenPrimaryAccent1)
        colors[2] = ContextCompat.getColor(context, R.color.colorPrimary)
        colors[3] = colors[1]
        colors[4] = colors[0]
        gradientOutline.colors = colors
        this.background = background
        clipChildren = false
        if (useDefaultLayout) {
            ui.editButton.setOnClickListener { toggleExpanded() }
            ui.nameEdit.animatorConfig = animatorConfig
            ui.extraInfoEdit.animatorConfig = animatorConfig
            ui.amountEdit.animatorConfig = animatorConfig
        }
    }

    override fun update(item: Entity) {
        super.update(item)
        setExpanded(item.isExpanded, animate = false)
        setSelectedState(item.isSelected, animate = false)
    }

    fun select() = setSelectedState(true)
    fun deselect() = setSelectedState(false)
    fun setSelectedState(selected: Boolean, animate: Boolean = true) {
        if (animate) valueAnimatorOfInt(
            setter = gradientOutline::setAlpha,
            fromValue = if (selected) 0 else 255,
            toValue = if (selected) 255 else 0,
            config = if (selected) AnimatorConfig.fadeIn
                     else          AnimatorConfig.fadeOut
        ).start()
        else gradientOutline.alpha = if (selected) 255 else 0
    }

    fun expand() = setExpanded(true)
    fun collapse() = setExpanded(false)
    fun toggleExpanded() = setExpanded(!isExpanded)
    open fun onExpandedChanged(expanded: Boolean = true, animate: Boolean = true) { }

    fun setExpanded(expanding: Boolean = true, animate: Boolean = true) {
        _isExpanded = expanding
        if (!expanding &&
            ui.nameEdit.isFocused ||
            ui.extraInfoEdit.isFocused ||
            ui.amountEdit.ui.valueEdit.isFocused)
                inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)

     /* While a LayoutTransition would achieve the same thing as all of the following
        custom animations and would be much more readable, it is unfortunately imposs-
        ible to synchronize LayoutTransition animations with other animations because
        the LayoutTransition API does not permit pausing and resuming, or manual star-
        ting after they are prepared, for the animators it uses internally. Unless this
        is changed, the internal expand / collapse animations must be done manually in
        case they need to be synchronized with other animations. */

        val editableTextFieldHeight = resources.getDimension(R.dimen.editable_text_field_min_height)
        ui.spacer.layoutParams.height =
            (editableTextFieldHeight * if (expanding) 2 else 1).toInt()

        if (itemIsLinked)
            if (animate) ui.linkIndicator.showOrHideWithAnimation(showing = expanding)
            else ui.linkIndicator.isVisible = expanding
        if (animate) setupCheckBoxAnimation()
        val nameEditHeightChange = updateNameEditState(expanding, animate)
        updateExtraInfoState(expanding, animate, nameEditHeightChange)
        updateAmountEditState(expanding, animate)
        onExpandedChanged(expanding, animate)
        updateEditButtonState(expanding, animate)

        if (animate && startAnimationsImmediately)
            runPendingAnimations()
    }

    private fun setupCheckBoxAnimation() {
        val checkBoxNewTop = paddingTop + (ui.spacer.layoutParams.height - ui.checkBox.height) / 2
        val checkBoxTopChange = checkBoxNewTop - ui.checkBox.top.toFloat()
        ui.checkBox.translationY = -checkBoxTopChange
        val checkBoxAnim = valueAnimatorOfFloat(
            setter = ui.checkBox::setTranslationY,
            fromValue = -checkBoxTopChange,
            toValue = 0f, config = animatorConfig)
        pendingAnimations.add(checkBoxAnim)
    }

    /** Update the editable state of nameEdit, animating if @param animate == true,
     * and @return the height change of the nameEdit, or 0 if no animation occurred. */
    private fun updateNameEditState(expanding: Boolean, animate: Boolean): Int {
        val nameEditAnimInfo = ui.nameEdit.setEditable(expanding, animate) ?: return 0
        nameEditAnimInfo.translateAnimator.pause()
        nameEditAnimInfo.underlineAnimator.pause()
        pendingAnimations.add(nameEditAnimInfo.translateAnimator)
        pendingAnimations.add(nameEditAnimInfo.underlineAnimator)
        // If the extra info edit is going to appear or disappear, then nameEdit's
        // translation animation's values will have to be adjusted by its top change.
        if (ui.extraInfoEdit.text.isNullOrBlank()) {
            val newTop = if (expanding) paddingTop
                         else paddingTop - nameEditAnimInfo.heightChange / 2
            val topChange = newTop - ui.nameEdit.top

            val startAdjustment = if (expanding) -topChange.toFloat() else 0f
            val endAdjustment = if (expanding) 0f else topChange.toFloat()
            ui.nameEdit.translationY += startAdjustment
            nameEditAnimInfo.adjustTranslationStartEnd(startAdjustment, endAdjustment)
            // If the ending translationY value is not zero, it needs to be set to zero
            // on the new layout after the animation has ended to avoid flickering.
            if (!expanding) nameEditAnimInfo.translateAnimator.doOnEnd {
                doOnNextLayout { ui.nameEdit.translationY = 0f }
            }
        }
        return nameEditAnimInfo.heightChange
    }

    /** Update the editable state of extraInfoEdit, animating if
     * @param animate == true and the extraInfoEdit is not blank. */
    private fun updateExtraInfoState(expanding: Boolean, animate: Boolean, nameEditHeightChange: Int) {
        val extraInfoIsBlank = ui.extraInfoEdit.text.isNullOrBlank()
        // If the extra info is blank and the view is being expanded, we can
        // avoid needing to animate its change in editable state by changing
        // the state before it fades in. If it is fading out, we should still
        // animate it because the user will be able to see it during the fade
        // out animation.
        val editableStateNeedsAnimated = animate && (!expanding || !extraInfoIsBlank)
        val animInfo = ui.extraInfoEdit.setEditable(
            editable = expanding, animate = editableStateNeedsAnimated)
        if (!animate) {
            // Since we have already set the editable state, if no animation
            // is needed we can just set the visibility and exit early.
            ui.extraInfoEdit.isVisible = expanding || !extraInfoIsBlank
            return
        }

        if (editableStateNeedsAnimated) {
            animInfo!!.translateAnimator.pause()
            animInfo.underlineAnimator.pause()
            pendingAnimations.add(animInfo.translateAnimator)
            pendingAnimations.add(animInfo.underlineAnimator)
            if (!extraInfoIsBlank) {
                // We have to adjust the extraInfoEdit starting translation by the
                // height change of the nameEdit to get the correct translation amount.
                ui.extraInfoEdit.translationY -= nameEditHeightChange
                animInfo.adjustTranslationStartEnd(-nameEditHeightChange.toFloat(), 0f)
            }
        }

        if (extraInfoIsBlank) {
            val anim = ui.extraInfoEdit.showOrHideWithAnimation(showing = expanding)
            // Because nameEdit is constrained to extraInfoEdit, adding extra-
            // InfoEdit to the overlay during showOrHideWithAnimation will alter
            // nameEdit's position. To avoid this we'll add nameEdit to the over-
            // lay as well for the duration of the animation.
            if (!expanding) {
                overlay.add(ui.nameEdit)
                anim.doOnEnd { overlay.remove(ui.nameEdit)
                               addView(ui.nameEdit) }
            }
        }
    }

    /** Update the editable state of amountEdit, animating if @param animate == true. */
    private fun updateAmountEditState(expanding: Boolean, animate: Boolean) {
        val amountEditAnimInfo = ui.amountEdit.setValueIsFocusable(expanding, animate)
            ?: return

        for (anim in amountEditAnimInfo.animators) anim.pause()
        pendingAnimations.addAll(amountEditAnimInfo.animators)

        // IntegerEdit's internal animation will only take into account its
        // width change. We have to make another translate animation to take
        // into account the amountEdit's left/start change.
        ui.amountEditSpacer.isVisible = !expanding
        val amountEndChange = ui.amountEditSpacer.layoutParams.width * if (expanding) 1 else -1
        val amountLeftChange = amountEndChange - amountEditAnimInfo.widthChange
        ui.amountEdit.translationX = -amountLeftChange.toFloat()
        val amountEditTranslateAnim = valueAnimatorOfFloat(
            setter = ui.amountEdit::setTranslationX,
            fromValue = ui.amountEdit.translationX,
            toValue = 0f, config = animatorConfig)
        pendingAnimations.add(amountEditTranslateAnim)

        // Because their ends are constrained to amountEdit's start, nameEdit
        // and, if it wasn't hidden, extraInfoEdit will need to have their end
        // values animated as well.
        nameLockedWidth = ui.nameEdit.width
        pendingAnimations.add(valueAnimatorOfInt(
            setter = ui.nameEdit::setRight,
            fromValue = ui.nameEdit.right,
            toValue = ui.nameEdit.right + amountLeftChange,
            config = animatorConfig).apply {
            doOnStart { nameLockedWidth = null }
        })

        if (ui.extraInfoEdit.text.isNullOrBlank()) return
        extraInfoLockedWidth = ui.extraInfoEdit.width
        pendingAnimations.add(valueAnimatorOfInt(
            setter = ui.extraInfoEdit::setRight,
            fromValue = ui.extraInfoEdit.right,
            toValue = ui.extraInfoEdit.right + amountLeftChange,
            config = animatorConfig).apply {
            doOnStart { extraInfoLockedWidth = null }
        })
    }

    private fun updateEditButtonState(expanding: Boolean, animate: Boolean) {
        val wrapContentSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        measure(wrapContentSpec, wrapContentSpec)
        val heightChange = measuredHeight - height
        if (animate) {
            ui.editButton.translationY = -heightChange.toFloat()
            val editButtonAnim = valueAnimatorOfFloat(
                setter = ui.editButton::setTranslationY,
                fromValue = -heightChange * 1f,
                toValue = 0f, config = animatorConfig)
            // editButton uses a state list animator with state_activated as the trigger.
            editButtonAnim.doOnStart { ui.editButton.isActivated = expanding }
            pendingAnimations.add(editButtonAnim)
        }
        else ui.editButton.isActivated = expanding
    }

    /**
     * Show or hide the child view with a fade in or out animation, and @return the animator.
     *
     * showOrHideWithAnimation differs from a simple fade in/out animation
     * in that it temporarily removes the view from its parent so that
     * change appearing/disappearing animations in the parent view can
     * play concurrently with the fade in/out animation.
     *
     * Because removing the view from its parent can affect sibling views,
     * the fade in/out animator is returned to aid in synchronizing the
     * animation with countermeasures the parent might employ to hide the
     * effects of temporarily removing the child.
     */
    protected fun View.showOrHideWithAnimation(showing: Boolean): Animator {
        alpha = if (showing) 0f else 1f
        isVisible = true
        val animator = valueAnimatorOfFloat(
            setter = ::setAlpha, fromValue = alpha,
            toValue = if (showing) 1f else 0f,
            config = if (showing) AnimatorConfig.fadeIn
                     else         AnimatorConfig.fadeOut)

        if (!showing) {
            val parent = parent as ViewGroup
            parent.overlay.add(this)
            animator.doOnEnd {
                parent.overlay.remove(this)
                isVisible = false
                parent.addView(this)
            }
        }
        pendingAnimations.add(animator)
        return animator
    }

 /* For some reason (possibly because the text field edits internally call
    setMinHeight, which in turn calls requestLayout), both nameEdit and extra-
    InfoEdit have their width set to their new expanded width sometime after
    setExpanded but before the end animations are started, causing a visual
    flicker. The properties nameLockedWidth and extraInfoLockedWidth, when set
    to a non-null value, prevent this resize from taking place, and in turn
    prevent the flicker effect. */
    private var nameLockedWidth: Int? = null
    private var extraInfoLockedWidth: Int? = null
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        nameLockedWidth?.let {
            if (ui.nameEdit.width != it)
                ui.nameEdit.right = ui.nameEdit.left + it
        }
        extraInfoLockedWidth?.let {
            if (ui.extraInfoEdit.width != it)
                ui.extraInfoEdit.right = ui.extraInfoEdit.left + it
        }
    }
}

/**
 * An ExpandableSelectableItemView to display the contents of a shopping list item.
 *
 * ShoppingListItemView is a ExpandableSelectableItemView subclass that dis-
 * plays the data of a ShoppingListItem instance. It has an update override
 * that updates the check state of the checkbox, it overrides the setExpanded
 * function with an implementation that toggles the checkbox between its nor-
 * mal checkbox mode and its color edit mode, and it has a convenience method
 * setStrikeThroughEnabled that will set the strike through state for both the
 * name and extra info edit at the same time.
 */
class ShoppingListItemView(context: Context) :
    ExpandableSelectableItemView<ShoppingListItem>(context, AnimatorConfig.shoppingListItem)
{
    override fun update(item: ShoppingListItem) {
        ui.checkBox.initIsChecked(item.isChecked)
        setStrikeThroughEnabled(enabled = item.isChecked, animate = false)
        super.update(item)
    }

    override fun onExpandedChanged(expanded: Boolean, animate: Boolean) {
        ui.checkBox.inColorEditMode = expanded
    }

    fun setStrikeThroughEnabled(enabled: Boolean, animate: Boolean = true) {
        ui.nameEdit.setStrikeThroughEnabled(enabled, animate)
        ui.extraInfoEdit.setStrikeThroughEnabled(enabled, animate)
    }
}

/**
 * An ExpandableSelectableItemView to display the contents of an inventory item.
 *
 * InventoryItemView is a ExpandableSelectableItemView subclass that displays
 * the data of an InventoryItem instance. It has an update override for the
 * extra fields that InventoryItem adds to its parent class, and has a set-
 * Expanded override that also shows or hides these extra fields.
 */
class InventoryItemView(context: Context) :
    ExpandableSelectableItemView<InventoryItem>(context, useDefaultLayout = false)
{
    val detailsUi: InventoryItemDetailsBinding

    init {
        val tempUi = InventoryItemBinding.inflate(LayoutInflater.from(context), this)
        ui = ViewModelItemBinding.bind(tempUi.root)
        detailsUi = InventoryItemDetailsBinding.bind(tempUi.root)
        ui.editButton.setOnClickListener { toggleExpanded() }
        ui.checkBox.inColorEditMode = true
        ui.amountEdit.minValue = 0
        ui.nameEdit.animatorConfig = AnimatorConfig.translation
        ui.extraInfoEdit.animatorConfig = AnimatorConfig.translation
        ui.amountEdit.animatorConfig = AnimatorConfig.translation
    }

    override fun update(item: InventoryItem) {
        detailsUi.addToShoppingListCheckBox.isChecked = item.addToShoppingList
        detailsUi.addToShoppingListTriggerEdit.initValue(item.addToShoppingListTrigger)
        super.update(item)
    }

    override fun onExpandedChanged(expanded: Boolean, animate: Boolean) {
        if (!expanded && detailsUi.addToShoppingListTriggerEdit.ui.valueEdit.isFocused)
            inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
        detailsUi.inventoryItemDetailsGroup.isVisible = expanded
    }
}