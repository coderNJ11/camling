package org.component;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.support.DefaultProducer;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class EmailHtmlWithAttachProducer extends DefaultProducer {

    private static final String DEFAULT_HTML_TEMPLATE = "<html><body><p>This is a blank email template.</p></body></html>";

    private final EmailHtmlWithAttachEndpoint endpoint;

    public EmailHtmlWithAttachProducer(EmailHtmlWithAttachEndpoint endpoint, String destination, Map<String, Object> parameters) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String from = endpoint.getFrom();
        validateMandatoryField(from, "The 'from' field is mandatory and missing.");

        String to = endpoint.getTo();
        validateMandatoryField(to, "The 'to' field is mandatory and missing.");

        String subject = endpoint.getSubject();
        validateMandatoryField(subject, "The 'subject' field is mandatory and missing.");

        String attachmentPath = endpoint.getAttachmentPath();
        validateMandatoryField(attachmentPath, "The 'attachmentPath' field is mandatory and missing.");
        if (!Files.exists(Paths.get(attachmentPath))) {
            throw new IllegalArgumentException("The file does not exist at the provided 'attachmentPath': " + attachmentPath);
        }

        String cc = endpoint.getCc();
        String bcc = endpoint.getBcc();

        String htmlBody = exchange.getMessage().getBody(String.class);
        if (htmlBody == null || htmlBody.trim().isEmpty()) {
            htmlBody = DEFAULT_HTML_TEMPLATE;
        }

        exchange.getMessage().setHeader("from", from);
        exchange.getMessage().setHeader("to", to);
        exchange.getMessage().setHeader("subject", subject);
        if (cc != null) {
            exchange.getMessage().setHeader("cc", cc);
        }
        if (bcc != null) {
            exchange.getMessage().setHeader("bcc", bcc);
        }
        exchange.getMessage().setHeader("Content-Type", "text/html");
        exchange.getMessage().setBody(htmlBody);

        AttachmentMessage attachmentMessage = exchange.getMessage(AttachmentMessage.class);
        FileDataSource fileDataSource = new FileDataSource(attachmentPath);
        DataHandler dataHandler = new DataHandler(fileDataSource);

        attachmentMessage.addAttachment(fileDataSource.getName(), dataHandler);

        log.info("Email prepared with subject='{}', to='{}', from='{}', cc='{}', bcc='{}', and attachment='{}'",
                subject, to, from, cc, bcc, fileDataSource.getName());
    }

    private void validateMandatoryField(String fieldValue, String errorMessage) {
        if (fieldValue == null || fieldValue.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}