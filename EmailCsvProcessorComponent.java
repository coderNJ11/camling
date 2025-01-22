package org.component;
import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.component.EmailCsvProcessorEndpoint;

import java.util.Map;

public class EmailCsvProcessorComponent extends DefaultComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailCsvProcessorComponent.class);

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LOGGER.info("Creating EmailCsvProcessorEndpoint for URI: {}", uri);

        // Create and configure the custom endpoint
        EmailCsvProcessorEndpoint endpoint = new EmailCsvProcessorEndpoint(uri, this);
        setProperties(endpoint, parameters); // Apply user-defined properties

        return endpoint;
    }
}