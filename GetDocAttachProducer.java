package org.component;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class GetDocAttachProducer extends DefaultProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDocAttachProducer.class);

    public GetDocAttachProducer(Endpoint endpoint) {
        super(endpoint);
    }


    @Override
    public void process(Exchange exchange) throws Exception {
        // Get the endpoint configuration
        GetDocAttachEndpoint endpoint = (GetDocAttachEndpoint) getEndpoint();

        // Build the API URL
        String apiUrl = String.format(
                "https://%s/integrations/api/v1/service/%s/%s/submission/%s/exportDocuments",
                endpoint.getApiHost(), endpoint.getTenantName(), endpoint.getFormName(), endpoint.getSubmissionId()
        );

        // Log API call info
        LOGGER.info("Calling API: {}", apiUrl);

        // Create an HttpClient instance
        HttpClient httpClient = HttpClient.newHttpClient();

        // Build the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("subAppID", endpoint.getSubAppID())
                .header("clientAppID", endpoint.getClientAppID())
                .header("requesterID", endpoint.getRequesterID())
                .header("jwtToken", endpoint.getJwtToken())
                .header("A3token", endpoint.getA3token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        // Execute the request and get the response
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        // Check the API response status code
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download document. HTTP response code: " + response.statusCode());
        }

        // Read the response stream
        InputStream inputStream = response.body();
        FileOutputStream fileOutputStream = null;

        try {
            // Generate a new folder name for saving the document
            String randomFolderName = "folder_" + System.currentTimeMillis();
            File saveFolder = new File(endpoint.getDocSavePath(), randomFolderName);

            // Create the folder
            if (!saveFolder.exists() && !saveFolder.mkdirs()) {
                throw new RuntimeException("Failed to create save folder: " + saveFolder.getAbsolutePath());
            }

            // Specify file path including the new folder
            String fileName = "exportedDocument_" + System.currentTimeMillis() + ".pdf"; // Customize file name as needed
            File outputFile = new File(saveFolder, fileName);
            fileOutputStream = new FileOutputStream(outputFile);

            // Write the input stream (document content) to a file
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }

            LOGGER.info("Document downloaded successfully and saved to: {}", outputFile.getAbsolutePath());

            // Add the document file path to the exchange in a Map
            Map<String, String> resultMap = new HashMap<>();
            resultMap.put("docFilePath", outputFile.getAbsolutePath());
            exchange.getIn().setBody(resultMap);

        } catch (Exception ex) {
            LOGGER.error("Error occurred while downloading and saving the document.", ex);
            throw ex;
        } finally {
            // Close all resources
            if (inputStream != null) {
                inputStream.close();
            }
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }
}