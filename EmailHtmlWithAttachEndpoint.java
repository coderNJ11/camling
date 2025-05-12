package org.component;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultEndpoint;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import java.util.Map;

public class EmailHtmlWithAttachEndpoint extends DefaultEndpoint {

    private final String destination;
    private final Map<String, Object> parameters;

    public EmailHtmlWithAttachEndpoint(String uri, EmailHtmlWithAttachComponent component, String destination, Map<String, Object> parameters) {
        super(uri, component);
        this.destination = destination;
        this.parameters = parameters;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new EmailHtmlWithAttachProducer(this, destination, parameters);
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}