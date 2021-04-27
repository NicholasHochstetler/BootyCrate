/* Copyright 2020 Nicholas Hochstetler
 * You may not use this file except in compliance with the Apache License
 * Version 2.0, obtainable at http://www.apache.org/licenses/LICENSE-2.0
 * or in the file LICENSE in the project's root directory. */

package com.cliffracertech.bootycrate

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cliffracertech.bootycrate.activity.GradientStyledMainActivity
import com.cliffracertech.bootycrate.utils.resolveIntAttribute
import org.hamcrest.core.IsNot.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityFragmentSwitchingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule var activityRule: ActivityScenarioRule<GradientStyledMainActivity>
        = ActivityScenarioRule(GradientStyledMainActivity::class.java)

    @Test fun mainActivity_fragmentSwitching() {
        onView(withId(R.id.shoppingListFragmentView)).check(matches(isDisplayed()))
        onView(withId(R.id.inventoryFragmentView)).check(matches(not(isDisplayed())))

        onView(withId(R.id.inventory_button)).perform(click())
        onView(withId(R.id.shoppingListFragmentView)).check(matches(not(isDisplayed())))
        onView(withId(R.id.inventoryFragmentView)).check(matches(isDisplayed()))

        onView(withId(R.id.shopping_list_button)).perform(click())
        onView(withId(R.id.shoppingListFragmentView)).check(matches(isDisplayed()))
        onView(withId(R.id.inventoryFragmentView)).check(matches(not(isDisplayed())))

        onView(withId(R.id.menuButton)).perform(click())
        onView(withText(R.string.settings_description)).inRoot(isPlatformPopup()).perform(click())
        onView(withId(R.id.settings_menu_item)).check(matches(isDisplayed()))

        pressBack()
        onView(withId(R.id.shoppingListFragmentView)).check(matches(isDisplayed()))
        onView(withId(R.id.inventoryFragmentView)).check(matches(not(isDisplayed())))
        onView(withId(R.id.settings_menu_item)).check(matches(not(isDisplayed())))

        onView(withId(R.id.menuButton)).perform(click())
        onView(withText(R.string.settings_description)).inRoot(isPlatformPopup()).perform(click())
        onView(withId(R.id.backButton)).perform(click())
        onView(withId(R.id.shoppingListFragmentView)).check(matches(isDisplayed()))
        onView(withId(R.id.inventoryFragmentView)).check(matches(not(isDisplayed())))
        onView(withId(R.id.settings_menu_item)).check(matches(not(isDisplayed())))

        activityRule.scenario.close()
    }

    @Test fun mainActivity_changingTheme() {
        val sysDarkThemeIsActive = Configuration.UI_MODE_NIGHT_YES ==
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        val lightTheme = R.style.LightTheme
        val darkTheme = R.style.DarkTheme
        var expectedTheme = if (sysDarkThemeIsActive) darkTheme else lightTheme
        var resetTheme = false
        activityRule.scenario.onActivity { activity ->
            if (!resetTheme) {
                val key = activity.getString(R.string.pref_light_dark_mode_key)
                val editor = PreferenceManager.getDefaultSharedPreferences(activity).edit().remove(key)
                editor.commit()
                return@onActivity
            }

            val expectedThemeContext = ContextThemeWrapper(context, expectedTheme)
            val expectedBgColor = expectedThemeContext.theme.resolveIntAttribute(android.R.attr.colorBackground)
            val actualBgColor = activity.theme.resolveIntAttribute(android.R.attr.colorBackground)
            if (actualBgColor != expectedBgColor) throw IllegalStateException(
                "The current theme's background color does not match the expected " +
                "one (expected: $expectedBgColor, actual: $actualBgColor")
        }
        if (!resetTheme) {
            resetTheme = true
            activityRule.scenario.recreate()
        }

        onView(withId(R.id.menuButton)).perform(click())
        onView(withText(R.string.settings_description)).inRoot(isPlatformPopup()).perform(click())
        onView(withText(R.string.pref_light_dark_mode_title)).perform(click())
        expectedTheme = R.style.LightTheme
        onView(withText(R.string.pref_theme_light_theme_title)).perform(click())

        onView(withText(R.string.pref_light_dark_mode_title)).perform(click())
        expectedTheme = R.style.DarkTheme
        onView(withText(R.string.pref_theme_dark_theme_title)).perform(click())

        onView(withText(R.string.pref_light_dark_mode_title)).perform(click())
        expectedTheme = if (sysDarkThemeIsActive) darkTheme else lightTheme
        onView(withText(R.string.pref_theme_sys_default_title)).perform(click())
    }
}