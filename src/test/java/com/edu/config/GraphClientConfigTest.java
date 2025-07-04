package com.edu.config;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class GraphClientConfigTest {

    private GraphClientConfig graphClientConfig;

    // We'll use ReflectionTestUtils to inject @Value properties for testing
    private final String TEST_CLIENT_ID = "test-client-id";
    private final String TEST_CLIENT_SECRET = "test-client-secret";
    private final String TEST_TENANT_ID = "test-tenant-id";

    @BeforeEach
    void setUp() {
        graphClientConfig = new GraphClientConfig();
        // Inject the @Value fields using ReflectionTestUtils for testing purposes
        ReflectionTestUtils.setField(graphClientConfig, "clientId", TEST_CLIENT_ID);
        ReflectionTestUtils.setField(graphClientConfig, "clientSecret", TEST_CLIENT_SECRET);
        ReflectionTestUtils.setField(graphClientConfig, "tenantId", TEST_TENANT_ID);
    }

    /**
     * Tests that the graphServiceClient bean is successfully created and is not null.
     * This test primarily verifies the configuration setup, assuming the Azure Identity
     * and Microsoft Graph SDK classes (like ClientSecretCredentialBuilder and GraphServiceClient)
     * work as expected. We don't mock the external SDK classes themselves, but ensure
     * our configuration correctly instantiates them.
     */
    @Test
    void graphServiceClient_shouldBeCreated() {
        // When
        GraphServiceClient client = graphClientConfig.graphServiceClient();

        // Then
        assertNotNull(client, "GraphServiceClient should not be null");

        // Further assertions could involve trying to access a mock service if we had one
        // for deeper integration testing, but for a unit test of the config,
        // ensuring instantiation with correct properties is sufficient.
    }
}