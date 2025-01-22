package org.component;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultEndpoint;
import org.component.EmailCsvProcessorProducer;

public class EmailCsvProcessorEndpoint extends DefaultEndpoint {

    public EmailCsvProcessorEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        // Create and return the custom Producer
        return new EmailCsvProcessorProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumers are not supported in this component.");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}