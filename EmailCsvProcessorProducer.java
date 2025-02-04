package org.component;


import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.support.DefaultProducer;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import jakarta.activation.DataHandler;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMultipart;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

public class EmailCsvProcessProducer extends DefaultProducer {

    public EmailCsvProcessProducer(EmailCsvProcessorEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            // Get the email message from the Exchange
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

            // Extract additional email metadata
            String subject = (String) mailMessage.getHeader("Subject");
            Date receivedDate = (Date) mailMessage.getHeader("CamelMailMessageReceivedDate");

            if (senderEmail == null || subject == null || receivedDate == null) {
                setExchangeError(exchange, "Required email metadata (sender, subject, or received date) is missing!");
                return;
            }

            // Parse sender's name and company name
            String senderName = parseSenderName(senderEmail);
            String companyName = parseCompanyName(subject);

            // Format receivedDate as "yyyy-MM-dd HH:mm:ss" for the "createOn" field
            String createOn = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(receivedDate);

            // Collect CSV data into `leaveDetails`
            List<Map<String, Object>> leaveDetails = new ArrayList<>();
            boolean csvFileFound = false;

            // Check if the email is an AttachmentMessage
            if (mailMessage instanceof AttachmentMessage) {
                AttachmentMessage attachmentMessage = (AttachmentMessage) mailMessage;
                Map<String, DataHandler> attachments = attachmentMessage.getAttachments();

                // Process attachments
                for (Map.Entry<String, DataHandler> attachmentEntry : attachments.entrySet()) {
                    DataHandler dataHandler = attachmentEntry.getValue();
                    String fileName = dataHandler.getName();

                    if (fileName.toLowerCase().endsWith(".csv")) {
                        csvFileFound = true;
                        try (InputStreamReader reader = new InputStreamReader(dataHandler.getInputStream())) {
                            leaveDetails.addAll(parseCsvToLeaveDetails(reader));
                        } catch (RuntimeException e) {
                            setExchangeError(exchange, "Please attach a valid CSV file: " + e.getMessage());
                            return;
                        }
                    }
                }
            }
            // Otherwise, check if it's a MimeMultipart message
            else if (mailMessage.getBody() instanceof MimeMultipart) {
                MimeMultipart multipart = (MimeMultipart) mailMessage.getBody();

                for (int i = 0; i < multipart.getCount(); i++) {
                    Part part = multipart.getBodyPart(i);

                    // Check if the part is a CSV attachment
                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) && part.getFileName().toLowerCase().endsWith(".csv")) {
                        csvFileFound = true;
                        try (InputStream inputStream = part.getInputStream();
                             InputStreamReader reader = new InputStreamReader(inputStream)) {
                            leaveDetails.addAll(parseCsvToLeaveDetails(reader));
                        } catch (RuntimeException e) {
                            setExchangeError(exchange, "Please attach a valid CSV file: " + e.getMessage());
                            return;
                        }
                    }
                }
            }

            // Check for missing or empty CSV data
            if (!csvFileFound) {
                setExchangeError(exchange, "Please attach a CSV file.");
                return;
            }

            if (leaveDetails.isEmpty()) {
                setExchangeError(exchange, "CSV file format is correct, but values are missing.");
                return;
            }

            // Build and set the complete JSON response as the output
            Map<String, Object> jsonResponse = buildJsonResponse(senderName, senderEmail, companyName, createOn, leaveDetails);
            exchange.getIn().setBody(jsonResponse);
        } catch (Exception e) {
            // Capture unexpected exceptions
            setExchangeError(exchange, "An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Helper method to set the error details in the Exchange.
     */
    private void setExchangeError(Exchange exchange, String errorMessage) {
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, errorMessage);
        exchange.getIn().setHeader("ErrorReason", errorMessage);
        exchange.getIn().setBody(null); // Clear the body since an error occurred
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
                    throw new RuntimeException("CSV row data is missing values.");
                }

                for (String value : row) {
                    if (value == null || value.trim().isEmpty()) {
                        throw new RuntimeException("CSV file format is correct, but values are missing.");
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
     * Extract the sender's name from the "From" email header.
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
     */
    private Map<String, Object> buildJsonResponse(String senderName, String senderEmail, String companyName, String createOn, List<Map<String, Object>> leaveDetails) {
        Map<String, Object> data = new HashMap<>();
        data.put("requester_name", senderName);
        data.put("requester_email", senderEmail);
        data.put("requesterCompany", companyName);
        data.put("createOn", createOn);
        data.put("leave_details", leaveDetails);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timezone", "UTC");
        metadata.put("browserName", "Chrome");

        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("data", data);
        jsonResponse.put("metadata", metadata);
        jsonResponse.put("state", "submitted");

        return jsonResponse;
    }
}
