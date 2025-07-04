package com.edu.config;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphClientConfig {

    // Inject Azure AD application properties from application.yml
    @Value("${azure.activedirectory.client-id}")
    private String clientId;

    @Value("${azure.activedirectory.client-secret}")
    private String clientSecret;

    @Value("${azure.activedirectory.tenant-id}")
    private String tenantId;

    /**
     * Creates and configures the GraphServiceClient bean.
     * This client will be used to interact with the Microsoft Graph API.
     *
     * @return A configured GraphServiceClient instance.
     */
    @Bean
    public GraphServiceClient graphServiceClient() {
        // Build the ClientSecretCredential using the Azure AD application details.
        // This credential is used to obtain an access token from Azure AD.
        TokenCredential credential = new ClientSecretCredentialBuilder() // Use TokenCredential
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .build();

        // In newer SDK versions, you instantiate GraphServiceClient directly with the credential.
        return new GraphServiceClient(credential); // Direct instantiation
    }
}
