//package org.component;
//
//import org.apache.camel.*;
//import org.apache.camel.builder.RouteBuilder;
//import org.apache.camel.impl.engine.AbstractCamelContext;
//import org.apache.camel.impl.engine.DefaultExchangeFactoryManager;
//import org.apache.camel.spi.ExchangeFactory;
//import org.apache.camel.spi.ExchangeFactoryManager;
//import org.apache.camel.spi.UriEndpoint;
//import org.apache.camel.spi.UriParam;
//import org.apache.camel.support.DefaultComponent;
//import org.apache.camel.support.DefaultConsumer;
//import org.apache.camel.support.DefaultEndpoint;
//import org.apache.camel.support.DefaultProducer;
//import org.apache.http.HttpEntity;
//import org.apache.http.HttpResponse;
//import org.apache.http.client.HttpClient;
//import org.apache.http.client.methods.HttpPost;
//import org.apache.http.entity.StringEntity;
//import org.apache.http.impl.client.HttpClientBuilder;
//
//import java.io.IOException;
//import java.net.URI;
//import java.util.HashMap;
//import java.util.Map;
//
//@UriEndpoint(scheme = "token-generator", title = "Token Generator", syntax = "token-generator:tokenEndpoint", label = "token-generator")
//public class TokenGeneratorComponent extends DefaultComponent {
//
//    @UriParam
//    private String tokenEndpoint;
//
//    @Override
//    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
//        return new TokenGeneratorEndpoint(uri, this, tokenEndpoint);
//    }
//
//    public static class TokenGeneratorEndpoint extends DefaultEndpoint {
//
//        private final String tokenEndpoint;
//
//        public TokenGeneratorEndpoint(String uri, Component component, String tokenEndpoint) {
//            super(uri, component);
//            this.tokenEndpoint = tokenEndpoint;
//        }
//
//        @Override
//        public Producer createProducer() throws Exception {
//            return new TokenGeneratorProducer(this);
//        }
//
//        @Override
//        public Consumer createConsumer(Processor processor) throws Exception {
//            return new TokenGeneratorConsumer(this, processor);
//        }
//
//        @Override
//        public boolean isSingleton() {
//            return true;
//        }
//    }
//
//    public static class TokenGeneratorProducer extends DefaultProducer {
//
//        public TokenGeneratorProducer(TokenGeneratorEndpoint endpoint) {
//            super(endpoint);
//        }
//
//        @Override
//        public void process(Exchange exchange) throws Exception {
//            // Get the input parameters from the exchange
//            String appName = exchange.getIn().getHeader("appName", String.class);
//            String emailId = exchange.getIn().getHeader("emailId", String.class);
//            String groups = exchange.getIn().getHeader("groups", String.class);
//            int timeToLive = exchange.getIn().getHeader("timeToLive", Integer.class);
//            String userId = exchange.getIn().getHeader("userId", String.class);
//
//            // Create a JSON payload for the token request
//            Map<String, Object> payload = new HashMap<>();
//            payload.put("appName", appName);
//            payload.put("emailId", emailId);
//            payload.put("groups", groups);
//            payload.put("timeToLive", timeToLive);
//            payload.put("userId", userId);
//
//            // Make the REST API call to generate the token
//            HttpClient client = HttpClientBuilder.create().build();
//            HttpPost request = new HttpPost(getEndpoint().getEndpointUri() + "/token");
//            request.setURI(URI.create(getEndpoint().getEndpointUri() + "/token"));
//            request.setEntity(new StringEntity(payload.toString()));
//            HttpResponse response = client.execute(request);
//
//            // Get the token from the response
//            HttpEntity entity = response.getEntity();
//            String token = new String(entity.getContent().readAllBytes());
//
//            // Set the token as the output of the exchange
//            exchange.getOut().setBody(token);
//        }
//    }
//
//    public static class TokenGeneratorConsumer extends DefaultConsumer {
//
//        public TokenGeneratorConsumer(TokenGeneratorEndpoint endpoint, Processor processor) {
//            super(endpoint, processor);
//        }
//
//        @Override
//        protected void doStart() throws Exception {
//            super.doStart();
//        }
//
//        @Override
//        protected void doStop() throws Exception {
//            super.doStop();
//        }
//    }
//
//    public String getTokenEndpoint() {
//        return tokenEndpoint;
//    }
//
//    public void setTokenEndpoint(String tokenEndpoint) {
//        this.tokenEndpoint = tokenEndpoint;
//    }
//}