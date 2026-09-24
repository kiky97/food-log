package com.foodlog.client.bedrock;

import com.foodlog.config.AppProperties;
import com.foodlog.exception.ApiException;
import com.foodlog.util.Retry;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

/**
 * Thin wrapper over the Bedrock Runtime {@code Converse} API, used with a forced tool call to get
 * structured JSON output (the Bedrock equivalent of Gemini's {@code responseSchema}). Credentials
 * come from the standard AWS provider chain (env vars, {@code ~/.aws/credentials}, or an IAM role)
 * and are never read or logged by this class.
 */
@Component
public class BedrockApi {

    private static final Logger log = LoggerFactory.getLogger(BedrockApi.class);

    private final BedrockRuntimeClient client;
    private final AppProperties.Bedrock cfg;

    @Autowired
    public BedrockApi(AppProperties props) {
        this(BedrockRuntimeClient.builder().region(Region.of(props.bedrock().region())).build(), props.bedrock());
    }

    BedrockApi(BedrockRuntimeClient client, AppProperties.Bedrock cfg) {
        this.client = client;
        this.cfg = cfg;
    }

    /** Forces the model to call {@code toolName} once and returns its input document. */
    public Document invokeTool(String systemPrompt, String userText, String toolName, Document inputSchema) {
        ConverseRequest request = ConverseRequest.builder()
                .modelId(cfg.model())
                .system(SystemContentBlock.builder().text(systemPrompt).build())
                .messages(Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText(userText)).build())
                .toolConfig(ToolConfiguration.builder()
                        .tools(Tool.builder()
                                .toolSpec(ToolSpecification.builder()
                                        .name(toolName)
                                        .inputSchema(ToolInputSchema.builder().json(inputSchema).build())
                                        .build())
                                .build())
                        .toolChoice(ToolChoice.builder().tool(SpecificToolChoice.builder().name(toolName).build()).build())
                        .build())
                .build();

        ConverseResponse response;
        try {
            response = Retry.execute(cfg.maxAttempts(), cfg.retryBackoff(), () -> client.converse(request),
                    BedrockApi::isRetryable);
        } catch (BedrockRuntimeException e) {
            log.warn("Bedrock Converse call failed: {}", e.getMessage());
            throw ApiException.badGateway("Bedrock API returned an error: " + e.getMessage());
        } catch (SdkClientException e) {
            log.warn("Bedrock Converse call failed: {}", e.getMessage());
            throw ApiException.badGateway("Bedrock API is unreachable (timeout, network error, or missing AWS credentials)");
        }

        List<ContentBlock> content = response.output().message().content();
        return content.stream()
                .map(ContentBlock::toolUse)
                .filter(Objects::nonNull)
                .findFirst()
                .map(toolUse -> toolUse.input())
                .orElseThrow(() -> ApiException.badGateway("Bedrock did not call the " + toolName + " tool"));
    }

    private static boolean isRetryable(RuntimeException e) {
        if (e instanceof SdkClientException) {
            return true;
        }
        if (e instanceof BedrockRuntimeException r) {
            return r.statusCode() == 429 || r.statusCode() >= 500;
        }
        return false;
    }
}
