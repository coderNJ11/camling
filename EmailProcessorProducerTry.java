package org.component;


import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;


public class EmailProcessorProducerTry extends DefaultProducer {

    public EmailProcessorProducerTry(org.apache.camel.Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String subject = exchange.getIn().getHeader("Subject", String.class);
        String body = exchange.getIn().getBody(String.class);

        String replyId = extractReplyID(subject);


        String comments = extractComments(body);


        Map<String, String> result = new HashMap<>();
        result.put("replyId", replyId);
        result.put("actionType", extractActionType(subject));
        result.put("comments", comments);

        ObjectMapper mapper = new ObjectMapper();
        String jsonResult = mapper.writeValueAsString(result);

        exchange.getIn().setBody(jsonResult);
    }

    private String extractReplyID(String subject) {
        if (subject == null) {
            return null;
        }


        Pattern pattern = Pattern.compile("^(APPROVE|REJECT)-([a-zA-Z0-9]+)$");
        Matcher matcher = pattern.matcher(subject);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }


    private String extractActionType(String subject) {
        if (subject == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("^(APPROVE|REJECT)-");
        Matcher matcher = pattern.matcher(subject);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }


    private String extractComments(String body) {
        if (body == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("Comments:\\[\\[(.*?)]]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}