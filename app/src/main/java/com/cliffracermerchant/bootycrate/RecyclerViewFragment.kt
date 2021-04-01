/* Copyright 2020 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracermerchant.bootycrate

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.cliffracermerchant.bootycrate.databinding.MainActivityBinding
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.kennyc.view.MultiStateView

/**
 * An fragment to display a ExpandableSelectableRecyclerView to the user.
 *
 * RecyclerViewFragment is an abstract fragment whose main purpose is to
 * display an instance of a ExpandableSelectableRecyclerView to the user.
 * It has three abstract properties, viewModel, recyclerView, and action-
 * ModeCallback, that must be overridden in subclasses with concrete
 * implementations of ExpandableSelectableViewModel, ExpandableSelectable-
 * RecyclerView, and a RecyclerViewActionBar.ActionModeCallback respecti-
 * vely. Because RecyclerViewFragment's implementation of onViewCreated
 * references its abstract recyclerView property, it is important that
 * subclasses override the recyclerView property and initialize it before
 * calling super.onViewCreated, or an exception will occur.
 */
@Suppress("LeakingThis")
abstract class RecyclerViewFragment<Entity: ExpandableSelectableItem> :
    Fragment(), MainActivity.MainActivityFragment
{
    protected abstract val viewModel: ExpandableSelectableItemViewModel<Entity>
    protected abstract val recyclerView: ExpandableSelectableRecyclerView<Entity>?
    protected abstract val actionModeCallback: RecyclerViewActionBar.ActionModeCallback
    private lateinit var sortModePrefKey: String

    private var actionBar: RecyclerViewActionBar? = null
    private val searchIsActive get() = actionBar?.activeSearchQuery != null
    private val actionModeIsStarted get() = actionBar?.actionMode != null

    init { setHasOptionsMenu(true) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        sortModePrefKey = getString(R.string.pref_sort, recyclerView!!.collectionName)
        val sortStr = prefs.getString(sortModePrefKey, ViewModelItem.Sort.Color.toString())
        recyclerView?.apply {
            sort = ViewModelItem.Sort.fromString(sortStr)
            selection.itemsLiveData.observe(viewLifecycleOwner, ::onSelectionChanged)
            observeViewModel(viewLifecycleOwner)
        }

        val multiStateView = view as? MultiStateView
        val emptyTextView = multiStateView?.getView(MultiStateView.ViewState.EMPTY) as? TextView
        emptyTextView?.text = getString(R.string.empty_recycler_view_message, recyclerView?.collectionName)

        viewModel.items.observe(viewLifecycleOwner) { items ->
            val stateView = view as? MultiStateView ?: return@observe
            stateView.viewState = when { items.isNotEmpty() -> MultiStateView.ViewState.CONTENT
                                         searchIsActive ->     MultiStateView.ViewState.ERROR
                                         else ->               MultiStateView.ViewState.EMPTY }
        }

        //activeSearchQuery = savedInstanceState?.getString("activeSearchQuery", null)
        val isActive = this.isActiveTemp ?: return
        val activityUi = this.activityUiTemp ?: return
        onActiveStateChanged(isActive, activityUi)
        isActiveTemp = null
        activityUiTemp = null
    }

    override fun onDetach() {
        super.onDetach()
        actionBar = null
        recyclerView?.snackBarAnchor = null
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        if (item.isChecked) false
        else when (item.itemId) {
            R.id.share_menu_item -> { openShareDialog() }
            R.id.select_all_menu_item -> {  recyclerView?.selection?.addAll(); true }
            R.id.color_option -> { saveSortingOption(ViewModelItem.Sort.Color, item) }
            R.id.name_ascending_option -> { saveSortingOption(ViewModelItem.Sort.NameAsc, item) }
            R.id.name_descending_option -> { saveSortingOption(ViewModelItem.Sort.NameDesc, item) }
            R.id.amount_ascending_option -> { saveSortingOption(ViewModelItem.Sort.AmountAsc, item) }
            R.id.amount_descending_option -> { saveSortingOption(ViewModelItem.Sort.AmountDesc, item) }
            else -> activity?.onOptionsItemSelected(item) ?: false
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        //outState.putString("activeSearchQuery", activeSearchQuery.toString())
    }

    /** Open a ShareDialog.
     * @return whether the dialog was successfully started. */
    private fun openShareDialog() : Boolean {
        val recyclerView = this.recyclerView ?: return false
        val items = if (viewModel.selectedItems.value?.isNotEmpty() == true)
            viewModel.selectedItems.value ?: emptyList()
        else viewModel.items.value ?: emptyList()
        ShareDialog(recyclerView.collectionName, items,
                    recyclerView.snackBarAnchor ?: recyclerView)
                    .show(childFragmentManager, null)
        return true
    }

    private fun onDeleteButtonClicked() {
        val view = this.view ?: return
        val size = viewModel.selectedItems.value?.size ?: 0
        viewModel.deleteSelected()
        val text = getString(R.string.delete_snackbar_text, size)
        Snackbar.make(view, text, Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.bottomAppBar)
            .setAction(R.string.delete_snackbar_undo_text) { viewModel.undoDelete() }
            .addCallback(object: BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(a: Snackbar?, b: Int) = viewModel.emptyTrash()
            }).show()
    }

    /** Set the recyclerView's sort to @param sort, check the @param
     * sortMenuItem, and save the sort to sharedPreferences.
     * @return whether the option was successfully saved to preferences. */
    private fun saveSortingOption(sort: ViewModelItem.Sort, sortMenuItem: MenuItem) : Boolean {
        recyclerView?.sort = sort
        sortMenuItem.isChecked = true
        val context = this.context ?: return false
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(sortModePrefKey, recyclerView?.sort.toString()).apply()
        return true
    }

    private fun onSelectionChanged(newList: List<Entity>) {
        if (newList.isNotEmpty()) {
            if (!actionModeIsStarted)
                actionBar?.startActionMode(actionModeCallback)
            actionBar?.actionMode?.title =
                activity?.getString(R.string.action_mode_title, newList.size) ?: ""
        } else actionBar?.actionMode?.finish()
    }

    override fun showsOptionsMenu() = true
    override fun showsBottomAppBar() = true
    override fun showsCheckoutButton() = false
    override fun onBackPressed() = when {
        actionModeIsStarted  -> { recyclerView?.selection?.clear(); true }
        searchIsActive       -> { actionBar?.activeSearchQuery = null; true }
        else                 -> false
    }

    private var isActiveTemp: Boolean? = null
    private var activityUiTemp: MainActivityBinding? = null
    @CallSuper override fun onActiveStateChanged(isActive: Boolean, activityUi: MainActivityBinding) {
        val recyclerView = this.recyclerView
        if (recyclerView == null) {
            // If recyclerView is null, the view probably hasn't been created yet. In
            // this case, we'll store the parameter values and call onActiveStateChanged
            // manually with the stored parameters at the end of onViewCreated.
            isActiveTemp = isActive
            activityUiTemp = activityUi
            return
        }
        if (!isActive) {
            if (searchIsActive)
                viewModel.searchFilter = null
            actionBar?.actionMode?.finish()
            actionBar = null
        } else {
            recyclerView.snackBarAnchor = activityUi.bottomAppBar
            actionBar = activityUi.actionBar
            activityUi.actionBar.onSearchQueryChangedListener = { newText ->
                recyclerView.searchFilter = newText.toString()
            }
            when { recyclerView.selection.isNotEmpty ->
                       onSelectionChanged(recyclerView.selection.items!!)
                   activityUi.actionBar.actionMode != null ->
                       activityUi.actionBar.actionMode?.finish()
                   activityUi.actionBar.activeSearchQuery != null ->
                       activityUi.actionBar.activeSearchQuery = null }

            activityUi.actionBar.changeSortMenu.findItem(when (recyclerView.sort) {
                ViewModelItem.Sort.Color ->      R.id.color_option
                ViewModelItem.Sort.NameAsc ->    R.id.name_ascending_option
                ViewModelItem.Sort.NameDesc ->   R.id.name_descending_option
                ViewModelItem.Sort.AmountAsc ->  R.id.amount_ascending_option
                ViewModelItem.Sort.AmountDesc -> R.id.amount_descending_option
            })?.isChecked = true
        }
    }
}