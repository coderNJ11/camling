package org.component;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import java.util.Map;

@Component("email-html-with-attachment") // Register the component
public class EmailHtmlWithAttachComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Create a custom endpoint and pass required configurations
        return new EmailHtmlWithAttachEndpoint(uri, this, remaining, parameters);
    }
}