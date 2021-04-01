/* Copyright 2020 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracermerchant.bootycrate

import android.animation.LayoutTransition
import android.content.Context
import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.cliffracermerchant.bootycrate.databinding.RecyclerViewActionBarBinding

/**
 * A toolbar tailored towards interacting with a recycler view.
 *
 * RecyclerViewActionBar acts as an entirely custom (i.e. it eschews the
 * Android setSupportActionBar API in favor of its own) action bar with an
 * interface tailored towards activities or fragments that primarily show a
 * recycler view. Through its binding property ui, the ui elements available
 * are:
 *     - backButton, similar to the home as up indicator, hidden by default
 *     - customTitle, a TextSwitcher that can be used as an activity or frag-
 *       ment title, or an action mode title. The attribute android.R.attr.-
 *       text is used as the default text for the title.
 *     - searchView, a SearchView
 *     - changeSortButton, a button whose default on click action opens the
 *       changeSortMenu, but can also have isActivated set to true to change
 *       to a delete icon and call the property onDeleteButtonClickedListener
 *       instead.
 *     - menuButton, which opens the optionsMenu member.
 * The contents of the changeSortMenu and the optionsMenu can be set in XML
 * with the attributes R.attr.changeSortMenuResId and R.attr.optionsMenuResId.
 * The callbacks for the menu items being clicked can be set through the func-
 * tions setOnSortOptionClickedListener and setOnOptionsItemClickedListener.
 * If the default Android action bar menu item callback functionality (every
 * click being routed through onOptionsItemSelected) is desired, the functions
 * can be passed a lambda that manually calls onOptionsItemSelected for the
 * activity or fragment being used.
 */
@Suppress("LeakingThis")
open class RecyclerViewActionBar(context: Context, attrs: AttributeSet) :
    ConstraintLayout(context, attrs)
{
    val ui = RecyclerViewActionBarBinding.inflate(LayoutInflater.from(context), this)
    var animatorConfig: AnimatorConfig? = null
        set(value) { field = value
                     if (value != null)
                        layoutTransition = layoutTransition(value) }

    var onDeleteButtonClickedListener: (() -> Unit)? = null
    fun setOnSortOptionClickedListener(listener: (MenuItem) -> Boolean) =
        changeSortPopupMenu.setOnMenuItemClickListener(listener)
    fun setOnOptionsItemClickedListener(listener: (MenuItem) -> Boolean) =
        optionsPopupMenu.setOnMenuItemClickListener(listener)

    private val changeSortPopupMenu = PopupMenu(context, ui.changeSortButton)
    private val optionsPopupMenu = PopupMenu(context, ui.menuButton)
    val changeSortMenu get() = changeSortPopupMenu.menu
    val optionsMenu get() = optionsPopupMenu.menu
    var optionsMenuVisible: Boolean = true
        set(value) { field = value
                     ui.searchButton.isVisible = value
                     ui.changeSortButton.isVisible = value
                     ui.menuButton.isVisible = value }

    val actionMode get() = _actionMode
    private var _actionMode: ActionMode? = null
        set(value) { field?.finish()
                     field = value }
    fun startActionMode(callback: ActionModeCallback) { _actionMode = ActionMode(callback)
                                                        _actionMode?.start() }

    var activeSearchQuery get() = if (!ui.customTitle.showingSearchView) null
                                  else ui.customTitle.searchQuery
                          set(value) = _setActiveSearchQuery(value)
    var onSearchQueryChangedListener get() = ui.customTitle.onSearchQueryChangedListener
                                     set(value) { ui.customTitle.onSearchQueryChangedListener = value }
    private val imm = inputMethodManager(context)

    init {
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT)
        var a = context.obtainStyledAttributes(attrs, R.styleable.RecyclerViewActionBar)
        val changeSortMenuResId = a.getResourceId(R.styleable.RecyclerViewActionBar_changeSortMenuResId, 0)
        val optionsMenuResId = a.getResourceId(R.styleable.RecyclerViewActionBar_optionsMenuResId, 0)
        a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.text))
        ui.customTitle.title = a.getString(0)
        a.recycle()
        changeSortPopupMenu.menuInflater.inflate(changeSortMenuResId, changeSortMenu)
        optionsPopupMenu.menuInflater.inflate(optionsMenuResId, optionsMenu)

        layoutTransition = LayoutTransition()
        ui.searchButton.setOnClickListener {
            _setActiveSearchQuery(if (activeSearchQuery == null) "" else null)
        }
        ui.changeSortButton.setOnClickListener {
            if (!ui.changeSortButton.isActivated)
                changeSortPopupMenu.show()
            else onDeleteButtonClickedListener?.invoke()
        }
        ui.menuButton.setOnClickListener{ optionsPopupMenu.show() }
    }

    override fun onSaveInstanceState() = Bundle().apply {
        putParcelable("superState", super.onSaveInstanceState())
        //putBoolean("searchWasActive", !ui.searchView.isIconified)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val bundle = state as Bundle
        super.onRestoreInstanceState(bundle.getParcelable("superState"))
        val searchWasActive = bundle.getBoolean("searchWasActive", false)
        if (!ui.backButton.isVisible && searchWasActive)
            setBackButtonVisible(true, animate = false)
    }

    fun setBackButtonVisible(visible: Boolean, animate: Boolean = true) {
        if (ui.backButtonSpacer.isVisible == visible) return
        if (!animate) ui.backButton.isVisible = visible
        else {
            ui.backButton.isVisible = true
            ui.backButton.animate().withLayer()
                .alpha(if (visible) 1f else 0f)
                .applyConfig(animatorConfig).start()
        }
        ui.backButtonSpacer.isVisible = visible
    }

    private var backButtonWasVisible = false
    private fun _setActiveSearchQuery(query: CharSequence?) {
        val searchWasActive = activeSearchQuery != null
        if (query != null) {
            ui.customTitle.searchQuery = query
            if (!searchWasActive) {
                ui.customTitle.showSearchQuery()
                ui.searchButton.isActivated = true
                backButtonWasVisible = ui.backButtonSpacer.isVisible
                if (!backButtonWasVisible)
                    setBackButtonVisible(true)
            }
        } else {
            ui.customTitle.setSearchQuery("")
            ui.customTitle.showTitle()
            ui.searchButton.isActivated = false
            if (!backButtonWasVisible)
                setBackButtonVisible(false)
        }
    }

    /**
     * An reimplementation of ActionMode that uses an instance of RecyclerViewActionBar.
     *
     * RecyclerViewActionBar.ActionMode is intended to be a replacement for an
     * Android ActionMode that reuses a RecyclerViewActionBar instead of over-
     * laying the support action bar. It requires an instance of RecyclerView-
     * ActionBar, which can be passed in during the constructor, or set through
     * the property actionBar. The property is nullable and public so that frag-
     * ments that use a RecyclerViewActionBar.ActionMode can null the property
     * during their onDestroyView.
     *
     * Once the property actionBar is set, calling start will start the action
     * mode, display the back button on the action bar, switch the action bar's
     * changeSortButton to a delete icon, and switch the title to the action
     * mode's title, accessed through the property title. The function finish can
     * be called when desired to end the ActionMode and switch the custom title
     * back to its original text.
     *
     * Sub-classes should override onStart and onFinish to make the desired
     * changes to the action bar UI while the action mode is started. Because the
     * action bar's original options menu is used, the implementing activity or
     * fragment will have to respond to action item clicks in the action bar's
     * onOptionsItemSelectedListener.
     *
     * Due to the fact that the same action bar can be used for multiple Recycler-
     * ViewActionModes, and because the actionBar backButton can be used for mul-
     * tiple purpose, RecyclerViewActionBar.ActionMode does not set the onClick-
     * Listener of the action bar's backButton despite making it visible. It is
     * up to the implementing activity or fragment to make the backButton finish
     * the action mode.
     */
    inner class ActionMode(private val callback: ActionModeCallback) {
        var title: String? = null
            set(value) { field = value
                         ui.customTitle.actionModeTitle = value ?: "" }
        private var backButtonWasVisible = false
        private var searchWasActive = false

        fun start() {
            ui.customTitle.setActionModeTitle(title ?: "", switchTo = true)
            backButtonWasVisible = ui.backButtonSpacer.isVisible
            if (!backButtonWasVisible) setBackButtonVisible(true)
            searchWasActive = ui.customTitle.showingSearchView
            ui.searchButton.isVisible = false
            ui.changeSortButton.isActivated = true
            callback.onStart(this@RecyclerViewActionBar)
        }

        fun finish() {
            // The layout transition should take care of the search button fade in animation but doesn't for some reason.
            ui.searchButton.alpha = 0f
            ui.searchButton.isVisible = true
            ui.searchButton.animate().alpha(1f).withLayer().applyConfig(animatorConfig).start()
            ui.changeSortButton.isActivated = false
            if (searchWasActive)
                ui.customTitle.showSearchQuery()
            else {
                ui.customTitle.showTitle()
                if (!backButtonWasVisible) setBackButtonVisible(false)
            }
            callback.onFinish(this@RecyclerViewActionBar)
        }
    }

    interface ActionModeCallback {
        fun onStart(actionBar: RecyclerViewActionBar)
        fun onFinish(actionBar: RecyclerViewActionBar)
    }
}

/**
 * A RecyclerViewActionBar that has a bottom border and allows setting a gradient as a background and / or border.
 *
 * GradientActionBar acts as an RecyclerViewActionBar, except that a gradient
 * (in the form of a Shader) can be set as the background or as the paint to
 * use for its border. Setting a gradient background this way (as opposed to,
 * e.g. a ShapeDrawable with a gradient fill) allows for more customization
 * (e.g. a radial gradient with different x and y radii).
 *
 * The border width is derived from the attr borderWidth. The background and
 * border gradients can be set independently of each other through the prop-
 * erties backgroundGradient and borderGradient.
 */
class GradientActionBar(context: Context, attrs: AttributeSet) : RecyclerViewActionBar(context, attrs) {
    private val backgroundDrawable: GradientVectorDrawable
    private val borderDrawable: GradientVectorDrawable

    var backgroundGradient: Shader? = null
        set(value) { field = value; backgroundDrawable.gradient = value }
    var borderGradient: Shader? = null
        set(value) { field = value; borderDrawable.gradient = value }

    init {
        var a = context.obtainStyledAttributes(attrs, R.styleable.GradientActionBar)
        val borderWidth = a.getDimension(R.styleable.GradientActionBar_bottomBorderWidth, 0f)
        a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.layout_height))
        val height = a.getDimensionPixelSize(0, 0)
        a.recycle()

        val width = context.resources.displayMetrics.widthPixels
        val backgroundPathData = "L $width,0 L $width,$height L 0,$height Z"
        backgroundDrawable = GradientVectorDrawable(width * 1f, height * 1f, backgroundPathData)

        val borderPathData = "M 0,${height - borderWidth / 2} L $width,${height - borderWidth / 2}"
        borderDrawable = GradientVectorDrawable(width * 1f, height * 1f, borderPathData)
        borderDrawable.style = Paint.Style.STROKE
        borderDrawable.strokeWidth = borderWidth

        backgroundDrawable.gradient = backgroundGradient
        borderDrawable.gradient = borderGradient
        background = LayerDrawable(arrayOf(backgroundDrawable, borderDrawable))
    }
}

/**
 * A preconfigured TextSwitcher that comes with two text views and a property
 * that allows setting a paint shader for both TextViews at once.
 *
 * ShaderTextSwitcher is a TextSwitcher that adds two TextViews, passes xml
 * attributes on to these text views, preconfigures short fade in and out anim-
 * ations, and allows setting a Shader object to the shader property. Setting
 * this property will apply the shader to both member TextViews. If the Shader-
 * TextSwitcher is moved during a layout, the shader will be offset to create
 * the appearance that the shader is set in global coordinates, and the Text-
 * Views are moving relative to it, instead of the shader moving with the Text-
 * Views.
 */
class ShaderActionBarTitle(context: Context, attrs: AttributeSet) : ViewFlipper(context, attrs) {
    var shader: Shader? get() = (currentView as TextView).paint.shader
        set(value) { _title.paint.shader = value
                     _searchView.paint.shader = value
                     _actionModeTitle.paint.shader = value }

    private val imm = inputMethodManager(context)
    private val titlePos = 0
    private val searchViewPos = 1
    private val actionModeTitlePos = 2
    private val _title = AppCompatTextView(context, attrs)
    val _searchView = EditText(context, attrs)
    private val _actionModeTitle = AppCompatTextView (context, attrs)

    var title get() = _title.text
              set(value) = setTitle(value)
    var searchQuery: CharSequence get() = _searchView.text.toString()
                                  set(value) = setSearchQuery(value)
    var actionModeTitle: CharSequence get() = _actionModeTitle.text
                                      set(value) = setActionModeTitle(value)
    var onSearchQueryChangedListener: ((CharSequence?) -> Unit)? = null

    val showingTitle get() = displayedChild == titlePos
    val showingSearchView get() = displayedChild == searchViewPos
    val showingActionModeTitle get() = displayedChild == actionModeTitlePos

    init {
        addView(_title, titlePos)
        addView(_searchView, searchViewPos)
        addView(_actionModeTitle, actionModeTitlePos)
        setInAnimation(context, R.anim.fade_in)
        setOutAnimation(context, R.anim.fade_out)
        val a = context.obtainStyledAttributes(attrs, R.styleable.ShaderActionBarTitle)
        var fontId = a.getResourceId(R.styleable.ShaderActionBarTitle_titleFont, 0)
        _title.typeface = try { ResourcesCompat.getFont(context, fontId) }
                          catch(e: Resources.NotFoundException) { null }

        fontId = a.getResourceId(R.styleable.ShaderActionBarTitle_searchViewFont, 0)
        _searchView.typeface = try { ResourcesCompat.getFont(context, fontId) }
                               catch(e: Resources.NotFoundException) { null }

        fontId = a.getResourceId(R.styleable.ShaderActionBarTitle_actionModeTitleFont, 0)
        _actionModeTitle.typeface = try { ResourcesCompat.getFont(context, fontId) }
                                    catch(e: Resources.NotFoundException) { null }

        a.recycle()

        _searchView.doAfterTextChanged { text -> onSearchQueryChangedListener?.invoke(text) }
    }

    fun showTitle() { displayedChild = titlePos
                      hideSearchViewKeyboard() }
    fun showSearchQuery() { displayedChild = searchViewPos
                            imm?.showSoftInput(_searchView, InputMethodManager.SHOW_IMPLICIT) }
    fun showActionModeTitle() { displayedChild = actionModeTitlePos
                                hideSearchViewKeyboard() }

    private fun hideSearchViewKeyboard() {
        if (_searchView.isFocused) imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun setTitle(title: CharSequence, switchTo: Boolean = false) {
        _title.text = title
        if (switchTo) showTitle()
    }

    fun setSearchQuery(query: CharSequence, switchTo: Boolean = false) {
        _searchView.setText(query)
        if (switchTo) showSearchQuery()
    }

    fun setActionModeTitle(title: CharSequence, switchTo: Boolean = false) {
        _actionModeTitle.text = title
        if (switchTo) showActionModeTitle()
    }
}