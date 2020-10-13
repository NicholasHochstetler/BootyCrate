/* Copyright 2020 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */
package com.cliffracermerchant.bootycrate

import android.animation.*
import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.activity_main.*

/** The primary activity for BootyCrate
 *
 *  Instead of switching between activities, nearly everything in BootyCrate is
 *  accomplished in the ShoppingListFragment, InventoryFragment, or the Preferences-
 *  Fragment. Instances of these fragments are instantiated on app startup, and
 *  hidden/shown by the fragment manager as appropriate. The active fragment can be
 *  determined via the boolean members showingInventory and showingPreferences as
 *  follows:
 *  Active fragment = if (showingPreferences)    PreferencesFragment
 *                    else if (showingInventory) InventoryFragment
 *                    else                       ShoppingListFragment
 *  If showingPreferences is true, the value of showingInventory determines the
 *  fragment "under" the preferences (i.e. the one that will be returned to on a
 *  back button press or a navigate up).
 *
 *  Both ShoppingListFragment and InventoryFragment are expected to have an
 *  enable() and a disable() function to be called when they are shown or hidden,
 *  respectively. These functions should prepare the main activity's UI for that
 *  fragment, e.g. by setting the floating action button's on click listener or
 *  icon.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var shoppingListFragment: ShoppingListFragment
    private lateinit var inventoryFragment: InventoryFragment
    private lateinit var imm: InputMethodManager
    private var showingInventory = false
    private var showingPreferences = false

    private var checkoutButtonIsVisible = true
    private var shoppingListSize = -1
    private var shoppingListNumNewItems = 0
    private var pendingBabAnim: Animator? = null

    lateinit var inventoryViewModel: InventoryViewModel
    lateinit var shoppingListViewModel: ShoppingListViewModel
    lateinit var fab: FloatingActionButton
    lateinit var checkoutBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getDefaultSharedPreferences(this)
     /* The activity's ViewModelStore will by default retain instances of the
        app's view models across activity restarts. In case this is not desired
        (e.g. when the database was replaced with an external one, and the view-
        models therefore need to be reset), setting the shared preference whose
        key is equal to the value of R.string.pref_viewmodels_need_cleared to
        true will cause MainActivity to call viewModelStore.clear() */
        var prefKey = getString(R.string.pref_viewmodels_need_cleared)
        if (prefs.getBoolean(prefKey, false)) {
            viewModelStore.clear()
            val editor = prefs.edit()
            editor.putBoolean(prefKey, false)
            editor.apply()
        }
        inventoryViewModel = ViewModelProvider(this).get(InventoryViewModel::class.java)
        shoppingListViewModel = ViewModelProvider(this).get(ShoppingListViewModel::class.java)

        prefKey = getString(R.string.pref_dark_theme_active)
        setTheme(if (prefs.getBoolean(prefKey, false)) R.style.DarkTheme
                 else                                  R.style.LightTheme)

        setContentView(R.layout.activity_main)
        setSupportActionBar(topActionBar)
        fab = floatingActionButton
        checkoutBtn = checkoutButton
        imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        cradleLayout.layoutTransition.doOnStart { _, _, _, _ ->
            pendingBabAnim?.start()
            pendingBabAnim = null
        }

        bottomNavigationBar.setOnNavigationItemSelectedListener { item ->
            if (item.isChecked) false // Selected item was already selected
            else {
                item.isChecked = true
                if (item.itemId == R.id.inventory_button) switchToInventory()
                else                                      switchToShoppingList()
                true
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                showBottomAppBar()
                showingPreferences = false
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            }
        }
        initFragments(savedInstanceState)

        if (showingInventory) showCheckoutButton(showing = false, animate = false)
        bottomAppBar.prepareCradleLayout(cradleLayout)

        shoppingListViewModel.items.observe(this) { newList ->
            updateShoppingListBadge(newList)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_bar_menu, menu)
        // Setting the SearchView icon color manually is a temporary work-
        // around because setting it in the theme/style did not work.
        val searchView = menu.findItem(R.id.app_bar_search)?.actionView as SearchView?
        (searchView?.findViewById(androidx.appcompat.R.id.search_close_btn) as ImageView).
            setColorFilter(ContextCompat.getColor(this, android.R.color.black))
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("showingInventory", showingInventory)
        supportFragmentManager.putFragment(outState, "shoppingListFragment", shoppingListFragment)
        supportFragmentManager.putFragment(outState, "inventoryFragment",    inventoryFragment)

        outState.putBoolean("showingPreferences", showingPreferences)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.settings_menu_item) {
            showPreferencesFragment()
            return true
        }
        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (showingPreferences) {
            supportFragmentManager.popBackStack()
            true
        } else false
    }

    override fun onBackPressed() {
        if (showingPreferences) supportFragmentManager.popBackStack()
        else                    super.onBackPressed()
    }

    private fun showPreferencesFragment(animate: Boolean = true) {
        showingPreferences = true
        imm.hideSoftInputFromWindow(bottomAppBar.windowToken, 0)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        showBottomAppBar(false)

        val enterAnimResId = if (animate) R.animator.fragment_close_enter else 0

        supportFragmentManager.beginTransaction().
            setCustomAnimations(enterAnimResId, R.animator.fragment_close_exit,
                                enterAnimResId, R.animator.fragment_close_exit).
            hide(if (showingInventory) inventoryFragment else shoppingListFragment).
            add(R.id.fragmentContainer, PreferencesFragment()).
            addToBackStack(null).commit()
    }

    private fun showBottomAppBar(show: Boolean = true) {
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        val views = arrayOf<View>(bottomAppBar, fab, checkoutBtn)

        // The views' heights might be zero here if they haven't been laid out.
        // In this case we wait until the first layout is finished before setting
        // the translationY.
        if (!show && bottomAppBar.height == 0) {
            bottomAppBar.doOnNextLayout {
                val translationAmount = screenHeight - cradleLayout.top
                for (view in views) view.translationY = translationAmount
            }
            return
        }

        val translationAmount = screenHeight - cradleLayout.top
        val translationStart = if (show) translationAmount else 0f
        val translationEnd =   if (show) 0f else translationAmount
        for (view in views) {
            view.translationY = translationStart
            view.animate().withLayer().translationY(translationEnd).start()
        }
    }

    private fun switchToInventory() = toggleMainFragments(switchingToInventory = true)
    private fun switchToShoppingList() = toggleMainFragments(switchingToInventory = false)
    private fun toggleMainFragments(switchingToInventory: Boolean) {
        if (showingPreferences) return

        showingInventory = switchingToInventory
        showCheckoutButton(showing = !showingInventory)
        imm.hideSoftInputFromWindow(bottomAppBar.windowToken, 0)

        val newFragment: RecyclerViewFragment<*>
        val oldFragment: RecyclerViewFragment<*>
        val newFragmentTranslationStart: Float
        val fragmentTranslationAmount: Float
        if (showingInventory) {
            newFragment = inventoryFragment
            oldFragment = shoppingListFragment
            newFragmentTranslationStart = fragmentContainer.width.toFloat()
            fragmentTranslationAmount = -fragmentContainer.width.toFloat()
        } else {
            newFragment = shoppingListFragment
            oldFragment = inventoryFragment
            newFragmentTranslationStart = -fragmentContainer.width.toFloat()
            fragmentTranslationAmount = fragmentContainer.width.toFloat()
        }

        val newFragmentView = newFragment.view
        val newFragmentAnim = newFragmentView?.animate()?.
            translationX(0f)?.setDuration(300)?.
            withLayer()?.withStartAction { newFragmentView.translationX = newFragmentTranslationStart }

        val oldFragmentView = oldFragment.view
        val oldFragmentAnim = oldFragmentView?.animate()?.
            translationXBy(fragmentTranslationAmount)?.
            withLayer()?.setDuration(300)?.
            withStartAction { oldFragment.onAboutToBeHidden() }?.
            withEndAction { supportFragmentManager.beginTransaction().hide(oldFragment).commit() }

        supportFragmentManager.beginTransaction().show(newFragment).runOnCommit {
            oldFragmentAnim?.start()
            newFragmentAnim?.start()
        }.commit()
    }

    private fun showCheckoutButton(showing: Boolean, animate: Boolean = true) {
        if (checkoutButtonIsVisible == showing) return

        checkoutBtn.visibility = if (showing) View.VISIBLE else View.GONE

        val wrapContentSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        cradleLayout.measure(wrapContentSpec, wrapContentSpec)
        fab.measure(wrapContentSpec, wrapContentSpec)
        val cradleEndWidth = if (showing) cradleLayout.measuredWidth else fab.measuredWidth

        if (animate) {
            // Settings the checkout button's clip bounds prevents the
            // right corners of the checkout button from sticking out
            // underneath the FAB during the show / hide animation.
            val checkoutBtnClipBounds = Rect(0, 0, 0, checkoutBtn.background.intrinsicHeight)
            val anim = ObjectAnimator.ofInt(bottomAppBar, "cradleWidth", cradleEndWidth)
            anim.interpolator = cradleLayout.layoutTransition.getInterpolator(LayoutTransition.CHANGE_APPEARING)
            anim.duration = cradleLayout.layoutTransition.getDuration(LayoutTransition.CHANGE_APPEARING)
            anim.addUpdateListener {
                checkoutBtnClipBounds.right = bottomAppBar.cradleWidth - fab.measuredWidth / 2
                checkoutBtn.clipBounds = checkoutBtnClipBounds
            }
            // The anim is stored here and started in the cradle layout's
            // layoutTransition's transition listener's transitionStart override
            // so that the animation is synced with the layout transition.
            pendingBabAnim = anim
        }
        else bottomAppBar.cradleWidth = cradleEndWidth

        checkoutButtonIsVisible = showing
    }

    private fun updateShoppingListBadge(newShoppingList: List<ShoppingListItem>) {
        if (shoppingListSize == -1) {
            if (newShoppingList.isNotEmpty())
                shoppingListSize = newShoppingList.size
        } else {
            val sizeChange = newShoppingList.size - shoppingListSize
            if (showingPreferences && showingInventory && sizeChange > 0) {
                shoppingListNumNewItems += sizeChange
                shoppingListBadge.text = getString(R.string.shopping_list_badge_text,
                                                   shoppingListNumNewItems)
                shoppingListBadge.clearAnimation()
                shoppingListBadge.alpha = 1f
                shoppingListBadge.animate().alpha(0f).setDuration(1000).setStartDelay(1500).
                withLayer().withEndAction { shoppingListNumNewItems = 0 }.start()
            }
            shoppingListSize = newShoppingList.size
        }
    }

    private fun initFragments(savedInstanceState: Bundle?) {
        showingInventory = savedInstanceState?.getBoolean("showingInventory") ?: false
        showingPreferences = savedInstanceState?.getBoolean("showingPreferences") ?: false

        if (savedInstanceState != null) {
            shoppingListFragment = supportFragmentManager.getFragment(
                savedInstanceState, "shoppingListFragment") as ShoppingListFragment
            inventoryFragment = supportFragmentManager.getFragment(
                savedInstanceState, "inventoryFragment") as InventoryFragment
            if (showingPreferences) {
                showBottomAppBar(false)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
            }
        } else {
            shoppingListFragment = ShoppingListFragment()
            inventoryFragment = InventoryFragment()
            val shownFragment = if (showingInventory) inventoryFragment
                                else                  shoppingListFragment
            val hiddenFragment = if (showingInventory) shoppingListFragment
                                 else                  inventoryFragment
            hiddenFragment.onAboutToBeHidden()

            supportFragmentManager.beginTransaction().
                add(R.id.fragmentContainer, inventoryFragment, "inventory").
                add(R.id.fragmentContainer, shoppingListFragment, "shoppingList").
                hide(if (showingInventory) shoppingListFragment
                     else                  inventoryFragment).
                runOnCommit { shownFragment.onHiddenChanged(false) }.
                commit()
        }
    }
}