package com.example.prismdiff

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import com.example.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrismDiffRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `studio home displays core actions presets and navigates across tabs`() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_pick_photos_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_open_folder_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_direct_canvas_button").assertIsDisplayed()

        // Open Direct Canvas -> Compact Dock is shown by default
        composeTestRule.onNodeWithTag("home_direct_canvas_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reset_viewport_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_sync_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Expand dock -> switch to Curtain Wipe -> verify wiper moves & does not snap back to 50%
        composeTestRule.onNodeWithTag("dock_expand_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mode_chip_curtain_wipe").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("curtain_handle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curtain_handle").assert(hasAnyDescendant(hasText("50%")))
        composeTestRule.onNodeWithTag("curtain_handle").performTouchInput { swipeRight(startX = centerX, endX = centerX + 120f) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("curtain_handle").assert(!hasAnyDescendant(hasText("50%")))

        // Reset view returns wipe cleanly back to 50%, then test Horizontal 90° swipe
        composeTestRule.onNodeWithTag("reset_viewport_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("curtain_handle").assert(hasAnyDescendant(hasText("50%")))

        // Collapse -> Hide -> Show dock round-trip, plus Horizontal 90° wipe while compact
        composeTestRule.onNodeWithTag("dock_collapse_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("curtain_angle_90").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("curtain_handle").performTouchInput { swipeDown(startY = centerY, endY = centerY + 100f) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("curtain_handle").assert(!hasAnyDescendant(hasText("50%")))

        composeTestRule.onNodeWithTag("dock_hide_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dock_show_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reset_viewport_button").assertIsDisplayed()

        // Add 3rd slot and test placement switching (Columns / Rows / Grid)
        composeTestRule.onNodeWithTag("dock_expand_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mode_chip_side_by_side").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_slot_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("layout_chip_columns").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("layout_chip_rows").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("layout_chip_grid").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Navigate to Config & Shader Lab tab
        composeTestRule.onNodeWithTag("nav_tab_config").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("launch_screen_chip_collection_browser").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("settings_lazy_list")
            .performScrollToNode(hasTestTag("shader_formula_input"))
        composeTestRule.onNodeWithTag("shader_formula_input").performTextReplacement("bad_token()")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Unknown function 'bad_token'").assertIsDisplayed()

        // Restore valid expression and test in Studio
        composeTestRule.onNodeWithTag("shader_formula_input").performTextReplacement("abs(lA - lB) * p1")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_lazy_list")
            .performScrollToNode(hasTestTag("test_shader_in_studio_button"))
        composeTestRule.onNodeWithTag("test_shader_in_studio_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Library tab and verify search & sorting UI
        composeTestRule.onNodeWithTag("nav_tab_library").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("browser_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_invert_button").performClick()
        composeTestRule.waitForIdle()
    }
}
