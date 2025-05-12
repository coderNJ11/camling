package org.component;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.activation.DataHandler;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static org.mockito.Mockito.*;

class EmailHtmlWithAttachProducerTest {

    private EmailHtmlWithAttachEndpoint endpoint;
    private EmailHtmlWithAttachProducer producer;
    private Exchange exchange;
    private AttachmentMessage message;

    @BeforeEach
    void setUp() {
        endpoint = mock(EmailHtmlWithAttachEndpoint.class);
        exchange = mock(Exchange.class);
        message = mock(AttachmentMessage.class);

        when(exchange.getMessage()).thenReturn(message);

        // Dummy endpoint parameters (if needed for constructor)
        Map<String, Object> parameters = Map.of();
        producer = new EmailHtmlWithAttachProducer(endpoint, "destination", parameters);
    }

    @Test
    void testValidCaseWithAttachment() throws Exception {
        // Create a temporary file to act as the attachment
        File tempFile = Files.createTempFile("attachment", ".txt").toFile();
        tempFile.deleteOnExit();

        // Mock endpoint values
        when(endpoint.getFrom()).thenReturn("sender@example.com");
        when(endpoint.getTo()).thenReturn("recipient@example.com");
        when(endpoint.getSubject()).thenReturn("Test Subject");
        when(endpoint.getAttachmentPath()).thenReturn(tempFile.getAbsolutePath());
        when(endpoint.getCc()).thenReturn("cc@example.com");
        when(endpoint.getBcc()).thenReturn("bcc@example.com");
        when(message.getBody(String.class)).thenReturn("<html><body><p>Test HTML Body</p></body></html>");

        // Execute the producer's process method
        producer.process(exchange);

        // Verify setHeader and setBody methods were called with correct arguments
        verify(message).setHeader("from", "sender@example.com");
        verify(message).setHeader("to", "recipient@example.com");
        verify(message).setHeader("subject", "Test Subject");
        verify(message).setHeader("cc", "cc@example.com");
        verify(message).setHeader("bcc", "bcc@example.com");
        verify(message).setHeader("Content-Type", "text/html");
        verify(message).setBody("<html><body><p>Test HTML Body</p></body></html>");

        // Verify attachment is added
        verify(message).addAttachment(eq(tempFile.getName()), any(DataHandler.class));
    }
}