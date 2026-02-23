package tech.yesboss.llm;

import tech.yesboss.domain.message.UnifiedMessage;

/**
 * Vendor SDK adapter interface for LLM provider abstraction.
 *
 * <p>This interface defines the contract for converting between the system's
 * internal {@link UnifiedMessage} format and any external vendor SDK types.
 * It enables the system to support multiple LLM providers (Claude, GPT, etc.)
 * without coupling the core logic to any specific vendor implementation.</p>
 *
 * <p>The generic types allow for asymmetric request/response types commonly
 * found in LLM SDKs (e.g., different types for requests vs responses).</p>
 *
 * @param <Req> The vendor SDK's request type (e.g., Claude's MessageParam)
 * @param <Res> The vendor SDK's response type (e.g., Claude's Message)
 */
public interface VendorSdkAdapter<Req, Res> {

    /**
     * Get the supported format identifier for this adapter.
     *
     * <p>This identifier is stored in the database (chat_message.payload_format)
     * to enable proper deserialization during context resume operations.</p>
     *
     * @return The format identifier (e.g., "ANTHROPIC_V3", "OPENAI_V1")
     */
    String getSupportedFormat();

    /**
     * Convert a UnifiedMessage to the vendor SDK's request format.
     *
     * <p>This is the "outbound" conversion - taking our internal format
     * and producing the vendor-specific request object.</p>
     *
     * @param unifiedMessage The internal unified message
     * @return The vendor SDK request object
     */
    Req toSdkRequest(UnifiedMessage unifiedMessage);

    /**
     * Convert a vendor SDK response to a UnifiedMessage.
     *
     * <p>This is the "inbound" conversion - taking the vendor's response
     * and producing our internal format.</p>
     *
     * @param sdkResponse The vendor SDK response object
     * @return A UnifiedMessage representing the response
     */
    UnifiedMessage toUnifiedMessage(Res sdkResponse);

    /**
     * Extract raw JSON content from a vendor SDK response.
     *
     * <p>This is used for persistence - the raw JSON is stored in the database
     * so that the exact context can be perfectly reconstructed during resume operations.</p>
     *
     * @param sdkResponse The vendor SDK response object
     * @return The raw JSON string representation of the content blocks
     */
    String extractRawContentJson(Res sdkResponse);

    /**
     * Deserialize JSON content back to a vendor SDK request.
     *
     * <p>This is the "resume" operation - after the system has been suspended
     * and then resumed, this method reconstructs the exact SDK request object
     * from the persisted JSON.</p>
     *
     * @param jsonContent The raw JSON content from the database
     * @param msgRole The message role (user, assistant, system, tool)
     * @return The reconstructed vendor SDK request object
     */
    Req deserializeToRequest(String jsonContent, String msgRole);
}
