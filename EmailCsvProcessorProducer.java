package org.component;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.support.DefaultProducer;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import javax.activation.DataHandler;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;

public class EmailCsvProcessorProducer extends DefaultProducer {

    public EmailCsvProcessorProducer(EmailCsvProcessorEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            // Get email details
            Message mailMessage = exchange.getIn();
            if (mailMessage == null) {
                setExchangeError(exchange, "No email found in the exchange!");
                return;
            }

            // Validate email address
            String senderEmail = (String) mailMessage.getHeader("From");
            if (!isValidEmail(senderEmail)) {
                setExchangeError(exchange, "Invalid or missing email address!");
                return;
            }

            // Extract additional email data
            String subject = (String) mailMessage.getHeader("Subject");
            Date receivedDate = (Date) mailMessage.getHeader("CamelMailMessageReceivedDate");

            if (senderEmail == null || subject == null || receivedDate == null) {
                setExchangeError(exchange, "Required email metadata (sender, subject, or received date) is missing!");
                return;
            }

            // Parse sender's name and company name
            String senderName = parseSenderName(senderEmail);
            String companyName = parseCompanyName(subject);

            // Format receivedDate as "yyyy-MM-dd HH:mm:ss" for "createOn" field
            String createOn = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(receivedDate);

            // Collect all CSV data into leave_details
            List<Map<String, Object>> leaveDetails = new ArrayList<>();
            boolean csvFileFound = false;

            if (mailMessage instanceof AttachmentMessage) {
                AttachmentMessage attachmentMessage = (AttachmentMessage) mailMessage;
                Map<String, DataHandler> attachments = attachmentMessage.getAttachments();

                // Process attachments
                for (Map.Entry<String, DataHandler> attachmentEntry : attachments.entrySet()) {
                    DataHandler dh = attachmentEntry.getValue();
                    String fileName = dh.getName();

                    if (fileName.toLowerCase().endsWith(".csv")) {
                        csvFileFound = true;
                        try (InputStreamReader reader = new InputStreamReader(dh.getInputStream())) {
                            leaveDetails.addAll(parseCsvToLeaveDetails(reader));
                        } catch (RuntimeException e) {
                            setExchangeError(exchange, "Please attach a valid csv file: " + e.getMessage());
                            return;
                        }
                    }
                }
            }

            if (!csvFileFound) {
                setExchangeError(exchange, "Please attach a csv file");
                return;
            }

            if (leaveDetails.isEmpty()) {
                setExchangeError(exchange, "CSV file format is correct but values are missing");
                return;
            }

            // Build the complete JSON response
            Map<String, Object> jsonResponse = buildJsonResponse(senderName, senderEmail, companyName, createOn, leaveDetails);

            // Set the JSON object as the Exchange body
            exchange.getIn().setBody(jsonResponse);
        } catch (Exception e) {
            // Capture any unexpected exceptions
            setExchangeError(exchange, "An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Helper method to set the error message in the Exchange.
     */
    private void setExchangeError(Exchange exchange, String errorMessage) {
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, errorMessage);
        exchange.getIn().setHeader("ErrorReason", errorMessage);
        exchange.getIn().setBody(null); // Clear the body since there is an error
    }

    /**
     * Parse the CSV file and convert it into a list of leave_details entries with validations.
     */
    private List<Map<String, Object>> parseCsvToLeaveDetails(InputStreamReader reader) throws Exception {
        CSVReader csvReader = new CSVReader(reader);
        List<Map<String, Object>> leaveDetails = new ArrayList<>();

        try {
            String[] headers = csvReader.readNext();

            // Validate CSV headers
            String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours"};
            if (!Arrays.equals(headers, expectedHeaders)) {
                throw new RuntimeException("Invalid CSV header format!");
            }

            // Parse rows and validate data
            String[] row;
            while ((row = csvReader.readNext()) != null) {
                if (row.length != headers.length) {
                    throw new RuntimeException("CSV file format is correct but values are missing");
                }

                for (String value : row) {
                    if (value == null || value.trim().isEmpty()) {
                        throw new RuntimeException("CSV file format is correct but values are missing");
                    }
                }

                // Create leave_details entry
                Map<String, Object> leaveDetail = new HashMap<>();
                leaveDetail.put("_id", Integer.parseInt(row[0])); // employee_id
                leaveDetail.put("manager", row[2]);
                leaveDetail.put("start_date", row[3] + "T00:00:00");
                leaveDetail.put("end_date", row[4] + "T00:00:00");
                leaveDetail.put("display_name", row[1]);
                leaveDetail.put("first_name", getFirstName(row[1]));
                leaveDetail.put("last_name", getLastName(row[1]));
                leaveDetail.put("name", row[1]);
                leaveDetail.put("email", "example@example.com"); // Placeholder; modify as needed
                leaveDetail.put("no_of_hours", Integer.parseInt(row[5]));

                leaveDetails.add(leaveDetail);
            }
        } catch (CsvValidationException e) {
            throw new RuntimeException("Error processing CSV file!", e);
        } finally {
            csvReader.close();
        }
        return leaveDetails;
    }

    /**
     * Validate email address format.
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }

        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        Pattern pattern = Pattern.compile(emailRegex);
        return pattern.matcher(email).matches();
    }

    /**
     * Parse the sender's name from the "From" email header.
     */
    private String parseSenderName(String senderEmail) {
        if (senderEmail.contains("<")) {
            return senderEmail.substring(0, senderEmail.indexOf("<")).trim();
        }
        return senderEmail.split("@")[0];
    }

    /**
     * Parse the company name from the email subject.
     */
    private String parseCompanyName(String subject) {
        return subject; // Use the subject directly as the company name
    }

    /**
     * Extract the first name from the full name.
     */
    private String getFirstName(String fullName) {
        return fullName.split(" ")[0];
    }

    /**
     * Extract the last name from the full name.
     */
    private String getLastName(String fullName) {
        String[] names = fullName.split(" ");
        return names.length > 1 ? names[names.length - 1] : "";
    }
    /**
     * Build the JSON response according to the desired structure.
     *
     * @param senderName   The name of the email sender (from email metadata)
     * @param senderEmail  The email of the sender (from email metadata)
     * @param companyName  Extracted company name from the email subject
     * @param createOn     The formatted creation date (email received date)
     * @param leaveDetails The list of leave details parsed from the CSV
     * @return A map representing the JSON response
     */
    private Map<String, Object> buildJsonResponse(String senderName, String senderEmail, String companyName, String createOn, List<Map<String, Object>> leaveDetails) {
        // Build the `data` node for the JSON
        Map<String, Object> data = new HashMap<>();
        data.put("requester_name", senderName);              // Fill in the sender's name
        data.put("requester_email", senderEmail);            // Fill in the sender's email
        data.put("requesterCompany", companyName);           // Fill in the company from the email subject
        data.put("createOn", createOn);                      // Fill in the creation date
        data.put("leave_details", leaveDetails);             // Populate leave details parsed from CSV
        data.put("_formio_formId", "123123123131dafwefw21e1eac"); // Example form ID (can be dynamic if needed)
        data.put("_formio_submissionId", "");                // Keep submission ID empty for now
        data.put("submit", true);                            // Indicate that the form is marked as submitted

        // Build the `metadata` node for the JSON
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timezone", "UTC");                     // Set timezone
        metadata.put("browserName", "Chrome");               // Set default browser for reference, if applicable
        metadata.put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/237.84.2.178 Safari/537.36");

        // Top-level JSON structure
        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("data", data);                      // Add data node
        jsonResponse.put("metadata", metadata);              // Add metadata node
        jsonResponse.put("state", "submitted");              // Mark the state as submitted
        jsonResponse.put("_vnote", "");                      // Keep vnote empty unless required
        jsonResponse.put("action", "CREATE");                // Use CREATE as default action for record creation
        jsonResponse.put("uploadedFiles", Collections.emptyList()); // No uploaded files in this use case

        return jsonResponse;                                 // Return the complete JSON structure map
    }
}