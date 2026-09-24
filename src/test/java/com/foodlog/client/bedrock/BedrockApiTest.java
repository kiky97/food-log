package com.foodlog.client.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodlog.config.AppProperties;
import com.foodlog.exception.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

class BedrockApiTest {

    private static final Document SCHEMA = Document.mapBuilder().putString("type", "object").build();

    private BedrockRuntimeClient client;
    private BedrockApi api;

    @BeforeEach
    void setUp() {
        client = mock(BedrockRuntimeClient.class);
        AppProperties.Bedrock cfg = new AppProperties.Bedrock("us-east-1", "test-model", 3, Duration.ofMillis(1));
        api = new BedrockApi(client, cfg);
    }

    private static ConverseResponse responseWithToolInput(Document input) {
        ToolUseBlock toolUse = ToolUseBlock.builder().toolUseId("t1").name("log_food_items").input(input).build();
        Message message = Message.builder().role(ConversationRole.ASSISTANT)
                .content(ContentBlock.fromToolUse(toolUse)).build();
        return ConverseResponse.builder().output(ConverseOutput.fromMessage(message)).build();
    }

    @Test
    void returnsTheToolInputDocument() {
        Document expected = Document.mapBuilder().putString("items", "[]").build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(responseWithToolInput(expected));

        Document result = api.invokeTool("system", "two eggs", "log_food_items", SCHEMA);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void failsWhenTheModelDoesNotCallTheTool() {
        Message message = Message.builder().role(ConversationRole.ASSISTANT)
                .content(ContentBlock.fromText("no tool for you")).build();
        when(client.converse(any(ConverseRequest.class)))
                .thenReturn(ConverseResponse.builder().output(ConverseOutput.fromMessage(message)).build());

        assertThatThrownBy(() -> api.invokeTool("system", "text", "log_food_items", SCHEMA))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(502));
    }

    @Test
    void retriesOnThrottlingThenSucceeds() {
        Document expected = Document.mapBuilder().putString("ok", "yes").build();
        when(client.converse(any(ConverseRequest.class)))
                .thenThrow(BedrockRuntimeException.builder().statusCode(429).message("slow down").build())
                .thenReturn(responseWithToolInput(expected));

        Document result = api.invokeTool("system", "text", "log_food_items", SCHEMA);

        assertThat(result).isEqualTo(expected);
        verify(client, times(2)).converse(any(ConverseRequest.class));
    }

    @Test
    void doesNotRetryClientErrors() {
        when(client.converse(any(ConverseRequest.class)))
                .thenThrow(BedrockRuntimeException.builder().statusCode(400).message("bad request").build());

        assertThatThrownBy(() -> api.invokeTool("system", "text", "log_food_items", SCHEMA))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(502));
        verify(client, times(1)).converse(any(ConverseRequest.class));
    }

    @Test
    void mapsNetworkFailuresToBadGateway() {
        when(client.converse(any(ConverseRequest.class))).thenThrow(SdkClientException.create("no route to host"));

        assertThatThrownBy(() -> api.invokeTool("system", "text", "log_food_items", SCHEMA))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(502));
    }
}
