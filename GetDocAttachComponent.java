package org.component;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A custom Camel Component for downloading documents by API and attaching them to emails
 * The component is annotated with @Component to define it as a registered component
 * with the scheme "email-csv-processor".
 */
@org.apache.camel.spi.annotations.Component("get-doc-attach")
public class GetDocAttachComponent extends DefaultComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDocAttachComponent.class);

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        return new GetDocAttachEndpoint(uri, this);
    }
}
