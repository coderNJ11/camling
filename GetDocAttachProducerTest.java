package org.component;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GetDocAttachProducerTest {

    @Mock
    private Endpoint mockEndpoint;

    @Mock
    private GetDocAttachEndpoint mockGetDocAttachEndpoint;

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<InputStream> mockHttpResponse;

    private GetDocAttachProducer producer;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        producer = new GetDocAttachProducer(mockEndpoint);

        // Set up the mock endpoint (GetDocAttachEndpoint) configuration
        when(mockEndpoint.getEndpointUri()).thenReturn("mock:get-doc-attach");
        when(mockEndpoint.getComponent()).thenReturn(null);
        when(mockEndpoint.isSingleton()).thenReturn(true);

        when(mockEndpoint.getCamelContext()).thenReturn(null);
        when(mockEndpoint.createProducer()).thenReturn(producer);
    }

    @Test
    public void testProcess_SuccessfulDownload() throws Exception {
        // Arrange
        String apiHost = "api.example.com";
        String tenantName = "tenant1";
        String formName = "form123";
        String submissionId = "submission456";
        String subAppID = "sub123";
        String clientAppID = "client456";
        String requesterID = "req789";
        String jwtToken = "mock-jwt";
        String a3Token = "mock-a3token";
        String docSavePath = "target/test-outputs";

        // Mock endpoint configuration
        when(mockGetDocAttachEndpoint.getApiHost()).thenReturn(apiHost);
        when(mockGetDocAttachEndpoint.getTenantName()).thenReturn(tenantName);
        when(mockGetDocAttachEndpoint.getFormName()).thenReturn(formName);
        when(mockGetDocAttachEndpoint.getSubmissionId()).thenReturn(submissionId);
        when(mockGetDocAttachEndpoint.getSubAppID()).thenReturn(subAppID);
        when(mockGetDocAttachEndpoint.getClientAppID()).thenReturn(clientAppID);
        when(mockGetDocAttachEndpoint.getRequesterID()).thenReturn(requesterID);
        when(mockGetDocAttachEndpoint.getJwtToken()).thenReturn(jwtToken);
        when(mockGetDocAttachEndpoint.getA3token()).thenReturn(a3Token);
        when(mockGetDocAttachEndpoint.getDocSavePath()).thenReturn(docSavePath);

        // Mock API response
        String mockFileContent = "Mock PDF content for testing";
        ByteArrayInputStream mockInputStream = new ByteArrayInputStream(mockFileContent.getBytes());

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockHttpResponse);
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(mockInputStream);

        // Set up the exchange with a mock message
        Exchange exchange = new DefaultExchange(mockEndpoint.getCamelContext());
        Message inMessage = new DefaultMessage(mockEndpoint.getCamelContext());
        exchange.setIn(inMessage);

        // Act
        producer.process(exchange);

        // Assert
        assertNotNull(exchange.getIn().getBody());
        assertTrue(exchange.getIn().getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, String> resultMap = (Map<String, String>) exchange.getIn().getBody();
        assertTrue(resultMap.containsKey("docFilePath"));

        String filePath = resultMap.get("docFilePath");
        assertNotNull(filePath);
        assertTrue(filePath.startsWith(docSavePath));
        assertTrue(filePath.contains("folder_"));
        assertTrue(filePath.endsWith(".pdf"));

        // Clean up test output files (optional but recommended)
        new File(filePath).delete();
        new File(filePath).getParentFile().delete();
    }
}