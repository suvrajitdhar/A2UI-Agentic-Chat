package com.sdz.smpl.a2ui.ui.a2ui

import androidx.a2ui.compose.ui.testing.A2uiTestController
import androidx.a2ui.compose.ui.testing.A2uiTestSurface
import androidx.a2ui.model.protocol.A2uiComponentPayload
import androidx.a2ui.model.protocol.A2uiEventAction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the app's A2UI catalog ([createAppA2uiCatalog]) with the `compose-ui-testing` library,
 * using component payloads shaped like the agent server's trip demo.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppA2uiCatalogTest {

    private val catalog = createAppA2uiCatalog(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun button_dispatchesActionWithResolvedContext() = runComposeUiTest {
        val controller = A2uiTestController(
            catalog = catalog,
            initialComponents = listOf(
                A2uiComponentPayload(
                    id = "root",
                    type = "Button",
                    properties = mapOf(
                        "child" to "label",
                        "variant" to "primary",
                        "action" to mapOf(
                            "event" to mapOf(
                                "name" to "search_stays",
                                "context" to mapOf("destination" to mapOf("path" to "/destination")),
                            ),
                        ),
                    ),
                ),
                A2uiComponentPayload(id = "label", type = "Text", properties = mapOf("text" to "Find stays")),
            ),
            initialData = mapOf("destination" to "Lisbon"),
        )
        val surface = controller.start()
        setContent { A2uiTestSurface(surface) }

        onNodeWithText("Find stays").performClick()
        waitForIdle()
        controller.waitForIdle()

        // dispatchedActions holds the raw action (context still {"path": …}); bindings are resolved
        // when the engine builds the outbound A2uiClientEventMessage – the message the app sends.
        val raw = controller.dispatchedActions.single() as A2uiEventAction
        assertEquals("search_stays", raw.eventName)

        val sent = controller.outboundEvents.single()
        assertEquals("search_stays", sent.type)
        assertEquals("root", sent.componentId)
        assertEquals("Lisbon", sent.context["destination"])
    }

    @Test
    fun textField_writesUserInputAndReflectsAgentUpdates() = runComposeUiTest {
        val controller = A2uiTestController(
            catalog = catalog,
            initialComponents = listOf(
                A2uiComponentPayload(
                    id = "root",
                    type = "TextField",
                    properties = mapOf("label" to "Guests", "value" to mapOf("path" to "/guests")),
                ),
            ),
            initialData = mapOf("guests" to "2"),
        )
        val surface = controller.start()
        setContent { A2uiTestSurface(surface) }

        onNodeWithText("2").performTextReplacement("4")
        waitForIdle()
        assertEquals("4", controller.getRawData("/guests"))

        controller.updateData("/guests", "6")
        controller.waitForIdle()
        onNodeWithText("6").assertIsDisplayed()
    }
}
