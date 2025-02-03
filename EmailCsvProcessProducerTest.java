package org.component;

import com.opencsv.exceptions.CsvValidationException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.CamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import java.io.File;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class EmailCsvProcessProducerTest {

    private EmailCsvProcessorProducer emailCsvProcessorProducer;
    private Exchange exchange;
    private Message message;

    @BeforeEach
    void setUp() {
        // Set up a mock CamelContext (not used, but required by Exchange)
        CamelContext mockCamelContext = Mockito.mock(CamelContext.class);

        // Initialize the producer (passing null for the endpoint since it's not used here)
        emailCsvProcessorProducer = new EmailCsvProcessorProducer(null);

        // Create a mock Exchange and Message
        exchange = new DefaultExchange(mockCamelContext);
        message = mock(Message.class);
        exchange.setIn(message);
    }

    @Test
    void testValidCsvAttachment() throws Exception {
        // Mock message headers
        when(message.getHeader("From")).thenReturn("John Doe <john.doe@example.com>");
        when(message.getHeader("Subject")).thenReturn("Company X");
        when(message.getHeader("CamelMailMessageReceivedDate")).thenReturn(new Date());

        // Mock a valid CSV file attachment
        AttachmentMessage attachmentMessage = mock(AttachmentMessage.class); // Mock AttachmentMessage directly
        File validCsv = new File("src/test/resources/test-data.csv"); // Ensure this test CSV exists
        DataHandler dataHandler = new DataHandler(new FileDataSource(validCsv));
        Map<String, DataHandler> attachments = Map.of("file.csv", dataHandler);
        when(attachmentMessage.getAttachments()).thenReturn(attachments);

        // Replace the existing message with the mocked AttachmentMessage
        exchange.setIn(attachmentMessage);

        // Process the exchange
        emailCsvProcessorProducer.process(exchange);

        // Validate the JSON response that is produced
        Map<String, Object> jsonResponse = (Map<String, Object>) exchange.getIn().getBody();
        assertNotNull(jsonResponse, "The JSON response should not be null");

        // Validate expected data in the JSON response
        assertEquals("John Doe", ((Map<String, Object>) jsonResponse.get("data")).get("requester_name"));
        assertEquals("john.doe@example.com", ((Map<String, Object>) jsonResponse.get("data")).get("requester_email"));
        assertTrue(((List<?>) ((Map<String, Object>) jsonResponse.get("data")).get("leave_details")).size() > 0);
    }

    @Test
    void testMissingCsvAttachment() throws Exception {
        // Mock message headers
        when(message.getHeader("From")).thenReturn("John Doe <john.doe@example.com>");
        when(message.getHeader("Subject")).thenReturn("Company X");
        when(message.getHeader("CamelMailMessageReceivedDate")).thenReturn(new Date());

        // Do not mock any attachments (simulate no CSV attachment)
        AttachmentMessage attachmentMessage = mock(AttachmentMessage.class); // Mock AttachmentMessage directly
        when(attachmentMessage.getAttachments()).thenReturn(Collections.emptyMap());
        exchange.setIn(attachmentMessage);

        // Process the exchange
        emailCsvProcessorProducer.process(exchange);

        // Verify that the exchange is set with the correct error
        String errorReason = exchange.getIn().getHeader("ErrorReason", String.class);
        assertEquals("Please attach a csv file", errorReason);
    }

    @Test
    void testInvalidEmailAddress() throws Exception {
        // Mock invalid email header
        when(message.getHeader("From")).thenReturn("Invalid Email");
        when(message.getHeader("Subject")).thenReturn("Company X");
        when(message.getHeader("CamelMailMessageReceivedDate")).thenReturn(new Date());

        // Process the exchange
        emailCsvProcessorProducer.process(exchange);

        // Verify that the exchange is set with the correct error
        String errorReason = exchange.getIn().getHeader("ErrorReason", String.class);
        assertEquals("Invalid or missing email address!", errorReason);
    }

    @Test
    void testInvalidCsvFileFormat() throws Exception {
        // Mock message headers
        when(message.getHeader("From")).thenReturn("John Doe <john.doe@example.com>");
        when(message.getHeader("Subject")).thenReturn("Company X");
        when(message.getHeader("CamelMailMessageReceivedDate")).thenReturn(new Date());

        // Mock an invalid CSV file
        File invalidCsv = new File("src/test/resources/invalid-data.csv"); // Ensure this test file exists
        AttachmentMessage attachmentMessage = mock(AttachmentMessage.class);
        DataHandler dataHandler = new DataHandler(new FileDataSource(invalidCsv));
        Map<String, DataHandler> attachments = Map.of("file.csv", dataHandler);
        when(attachmentMessage.getAttachments()).thenReturn(attachments);
        exchange.setIn(attachmentMessage);

        // Process the exchange
        emailCsvProcessorProducer.process(exchange);

        // Verify that the exchange is set with the correct error
        String errorReason = exchange.getIn().getHeader("ErrorReason", String.class);
        assertTrue(errorReason.contains("Invalid CSV header format!"), "Expected error about invalid CSV format");
    }
}