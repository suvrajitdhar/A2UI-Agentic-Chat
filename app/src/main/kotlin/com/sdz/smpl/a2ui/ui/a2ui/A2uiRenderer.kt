package com.sdz.smpl.a2ui.ui.a2ui

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientCapabilities
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import androidx.a2ui.model.protocol.A2uiDeleteSurfaceMessage
import com.sdz.smpl.a2ui.domain.model.ClientMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Thin adapter around the Compose A2UI renderer (`A2uiMessageParser` + `A2uiMessageProcessor`).
 *
 * It keeps the A2UI library types out of the rest of the presentation layer: messages come in as
 * raw JSON strings, and outbound events go out as domain [ClientMessage]s.
 *
 * Lifecycle: create one per conversation screen (in a ViewModel) and keep [run] running in a
 * coroutine tied to that ViewModel; messages passed to [render] are queued until then.
 *
 * @param catalog the component catalog the agent may use (see [createAppA2uiCatalog]).
 */
class A2uiRenderer(private val catalog: A2uiCatalog) {

    private val parser = A2uiMessageParser()
    private val processor = A2uiMessageProcessor(catalogs = listOf(catalog))

    /** Surfaces currently alive, in creation order. Render each with `A2uiSurface`. */
    val surfaces: StateFlow<List<A2uiSurfaceModel>> = processor.activeSurfaces

    /**
     * Request metadata advertising which catalogs this client renders:
     * `{"a2uiClientCapabilities": {"v0.9": {"supportedCatalogIds": [...]}}}`.
     * The agent may only create surfaces with these catalog ids.
     */
    val capabilitiesMetadata: Map<String, Any?> =
        A2uiClientCapabilities(supportedCatalogIds = listOf(catalog.id)).toPayloadMap()

    /**
     * Messages the renderer wants to send to the agent: user actions (e.g. button taps) and errors
     * (e.g. a component that failed schema validation).
     *
     * This is a hot flow without replay: start collecting before rendering anything (for example
     * with `CoroutineStart.UNDISPATCHED`) so no event is missed.
     */
    val outboundMessages: Flow<ClientMessage> = processor.outboundEvents.map { it.toClientMessage() }

    /**
     * Runs the processing loop. Suspends until the calling coroutine is cancelled.
     * Call it once, off the main thread (e.g. `Dispatchers.Default`).
     */
    suspend fun run() = processor.collectMessages()

    /**
     * Parses and queues one A2UI server-to-client message.
     *
     * Invalid JSON or schema violations don't throw: the library turns them into an error message
     * on [outboundMessages], so the agent can correct itself.
     *
     * @param json one message, e.g. `{"version":"v0.9","updateComponents":{…}}`.
     */
    fun render(json: String) = processor.processInput(parser, json)

    /** Removes every live surface, e.g. when starting a new conversation. */
    fun clear() {
        surfaces.value.forEach { surface -> processor.processMessage(A2uiDeleteSurfaceMessage(surface.id)) }
    }

    /** Maps the library's outbound message types to the domain model. */
    private fun A2uiClientToServerMessage.toClientMessage(): ClientMessage = when (this) {
        is A2uiClientEventMessage -> ClientMessage.UserAction(
            name = type,
            surfaceId = surfaceId,
            sourceComponentId = componentId,
            timestampMillis = timestamp,
            context = context,
            dataModelMetadata = clientDataModel?.toPayloadMap(),
        )

        is A2uiClientErrorMessage -> ClientMessage.ClientError(
            code = code,
            surfaceId = surfaceId,
            message = message,
            context = context,
        )
    }
}
