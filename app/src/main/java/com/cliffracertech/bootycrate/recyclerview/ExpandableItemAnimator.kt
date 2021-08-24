/* Copyright 2021 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracertech.bootycrate.recyclerview

import android.animation.Animator
import android.view.ViewPropertyAnimator
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.cliffracertech.bootycrate.utils.AnimatorConfig
import com.cliffracertech.bootycrate.utils.applyConfig
import com.cliffracertech.bootycrate.utils.intValueAnimator
import com.cliffracertech.bootycrate.utils.setHeight

/**
 * A RecyclerView.ItemAnimator that animates the expanding and collapsing of items.
 *
 * ExpandableItemAnimator is a RecyclerView.ItemAnimator that provides an
 * override of animateChange to animate the height changes of expanding or
 * collapsing items. The recycler view that uses ExpandableItemAnimator must
 * use item views that implement the ExpandableRecyclerViewItem interface.
 *
 * Listeners can be set for the start or end of expand animations and the
 * start or end of collapse animations through the properties onItemExpandStartedListener,
 * onItemExpandEndedListener, onItemCollapseStartedListener, and onItemCollapseEndedListener,
 * respectively.
 */
class ExpandableItemAnimator(animatorConfig: AnimatorConfig) : DefaultItemAnimator() {
    private val pendingAnimators = mutableListOf<Animator>()
    private val pendingViewPropAnimators = mutableListOf<ViewPropertyAnimator>()
    private val changingViews = mutableListOf<ExpandableRecyclerViewItem>()

    var onItemExpandStartedListener: ((RecyclerView.ViewHolder) -> Unit)? = null
    var onItemExpandEndedListener: ((RecyclerView.ViewHolder) -> Unit)? = null
    var onItemCollapseStartedListener: ((RecyclerView.ViewHolder) -> Unit)? = null
    var onItemCollapseEndedListener: ((RecyclerView.ViewHolder) -> Unit)? = null

    var animatorConfig = animatorConfig
        set(value) { field = value
                     addDuration = value.duration
                     changeDuration = value.duration
                     removeDuration = value.duration
                     moveDuration = value.duration }

    init { this.animatorConfig = animatorConfig }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        preInfo: ItemHolderInfo,
        postInfo: ItemHolderInfo
    ): Boolean {
        // If a view is being expanded or collapsed, oldHolder must be
        // equal to newHolder, and the heightChange must not be zero.
        if (oldHolder != newHolder)
            return super.animateChange(oldHolder, newHolder, preInfo, postInfo)
        val startHeight = preInfo.bottom - preInfo.top
        val endHeight = postInfo.bottom - postInfo.top
        if (endHeight == startHeight)
            return super.animateChange(oldHolder, newHolder, preInfo, postInfo)

        val view = newHolder.itemView
        if (view !is ExpandableRecyclerViewItem) throw IllegalStateException(
            "The item views used with ExpandableItemAnimator must " +
            "implement ExpandableItemAnimator.ExpandableRecyclerViewItem.")

        view.setHeight(startHeight)
        val expanding = endHeight > startHeight
        intValueAnimator(view::setHeight, startHeight, endHeight, animatorConfig).apply {
            doOnStart { dispatchChangeStarting(newHolder, true)
                        if (expanding) onItemExpandStartedListener?.invoke(newHolder)
                        else           onItemCollapseStartedListener?.invoke(newHolder) }
            doOnEnd { dispatchChangeFinished(newHolder, true)
                      if (expanding) onItemExpandEndedListener?.invoke(newHolder)
                      else           onItemCollapseEndedListener?.invoke(newHolder) }
            pendingAnimators.add(this)
        }

        val topChange = postInfo.top - preInfo.top + view.translationY
        if (topChange != 0f) {
            view.translationY -= topChange
            pendingViewPropAnimators.add(view.animate().translationY(0f)
                                            .applyConfig(animatorConfig))
        }
        changingViews.add(view)
        return true
    }

    override fun runPendingAnimations() {
        super.runPendingAnimations()
        pendingAnimators.forEach { it.start() }
        pendingViewPropAnimators.forEach { it.start() }
        changingViews.forEach { it.runPendingAnimations() }
        pendingAnimators.clear()
        pendingViewPropAnimators.clear()
        changingViews.clear()
    }

    /**
     * An interface for views that are used to represent expandable recycler view
     * items to describe what should change internally when expanded or collapsed.
     *
     * Implementing views should perform any necessary changes to child views
     * with a setExpanded implementation. Animations for these changes should
     * be prepared and stored if @param animate == true, and then later played
     * in an implementation of runPendingAnimations.
     */
    interface ExpandableRecyclerViewItem {
        fun expand() = setExpanded(true)
        fun collapse() = setExpanded(false)
        fun setExpanded(expanding: Boolean, animate: Boolean = true)
        fun runPendingAnimations()
    }
}