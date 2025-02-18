package org.component;


import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.support.DefaultProducer;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import jakarta.activation.DataHandler;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class EmailCsvGroupProcessProducer extends DefaultProducer {

    public EmailCsvGroupProcessProducer(EmailCsvProcessorEndpoint endpoint) {
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
            String createOn = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(receivedDate);

            // Collect all CSV and XLSX data into leave_details
            List<Map<String, Object>> leaveDetails = new ArrayList<>();
            boolean validFileFound = false;

            if (mailMessage instanceof AttachmentMessage) {
                AttachmentMessage attachmentMessage = (AttachmentMessage) mailMessage;
                Map<String, DataHandler> attachments = attachmentMessage.getAttachments();

                // Process attachments
                for (Map.Entry<String, DataHandler> attachmentEntry : attachments.entrySet()) {
                    DataHandler dh = attachmentEntry.getValue();
                    String fileName = dh.getName();

                    if (fileName.toLowerCase().endsWith(".csv")) {
                        validFileFound = true;
                        try (InputStreamReader reader = new InputStreamReader(dh.getInputStream())) {
                            leaveDetails.addAll(parseCsvToLeaveDetails(reader));
                        } catch (RuntimeException e) {
                            setExchangeError(exchange, "Invalid CSV file: " + e.getMessage());
                            return;
                        }
                    } else if (fileName.toLowerCase().endsWith(".xlsx")) {
                        validFileFound = true;
                        try (InputStream inputStream = dh.getInputStream()) {
                            leaveDetails.addAll(parseXlsxToLeaveDetails(inputStream));
                        } catch (RuntimeException e) {
                            setExchangeError(exchange, "Invalid XLSX file: " + e.getMessage());
                            return;
                        }
                    }
                }
            }

            if (!validFileFound) {
                setExchangeError(exchange, "Please attach a CSV or XLSX file.");
                return;
            }

            if (leaveDetails.isEmpty()) {
                setExchangeError(exchange, "File format is correct but values are missing.");
                return;
            }

            // Group leave details by manager
            Map<String, List<Map<String, Object>>> groupedByManager = groupLeaveDetailsByManager(leaveDetails);

            // Build a list of JSON responses, one for each manager
            List<Map<String, Object>> jsonResponses = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByManager.entrySet()) {
                String managerName = entry.getKey();
                List<Map<String, Object>> managerLeaveDetails = entry.getValue();

                // Create a JSON response for the manager
                Map<String, Object> jsonResponse = buildJsonResponse(
                        senderName, senderEmail, companyName, createOn, managerLeaveDetails
                );
                jsonResponse.put("manager_name", managerName); // Add manager name to the JSON response

                jsonResponses.add(jsonResponse);
            }

            // Set the list of JSON responses as the Exchange body
            exchange.getIn().setBody(jsonResponses);
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

            // Trim headers to handle extra spaces
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim();
            }

            // Expected headers
            String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours"};
            if (!Arrays.equals(headers, expectedHeaders)) {
                throw new RuntimeException("Invalid CSV header format!");
            }

            // Parse rows
            String[] row;
            while ((row = csvReader.readNext()) != null) {
                for (int i = 0; i < row.length; i++) {
                    row[i] = row[i].trim().replace("\"", ""); // Trim and remove quotes
                }

                if (row.length != headers.length) {
                    throw new RuntimeException("CSV file format is correct but row data is incomplete.");
                }

                Map<String, Object> leaveDetail = new HashMap<>();
                leaveDetail.put("_id", Integer.parseInt(row[0]));
                leaveDetail.put("manager", row[2]);
                leaveDetail.put("start_date", row[3] + "T00:00:00");
                leaveDetail.put("end_date", row[4] + "T00:00:00");
                leaveDetail.put("display_name", row[1]);
                leaveDetail.put("first_name", getFirstName(row[1]));
                leaveDetail.put("last_name", getLastName(row[1]));
                leaveDetail.put("name", row[1]);
                leaveDetail.put("email", "example@example.com");
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
     * Parse XLSX file into leave_details entries.
     */
    private List<Map<String, Object>> parseXlsxToLeaveDetails(InputStream inputStream) throws Exception {
        List<Map<String, Object>> leaveDetails = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        // Iterate rows
        Iterator<Row> rowIterator = sheet.rowIterator();
        Row headerRow = rowIterator.next(); // First row is header

        // Validate headers
        String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours"};
        for (int i = 0; i < expectedHeaders.length; i++) {
            String headerCell = headerRow.getCell(i).getStringCellValue().trim();
            if (!headerCell.equalsIgnoreCase(expectedHeaders[i])) {
                throw new RuntimeException("Invalid XLSX header format!");
            }
        }

        // Parse rows
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            Map<String, Object> leaveDetail = new HashMap<>();
            leaveDetail.put("_id", (int) row.getCell(0).getNumericCellValue());
            leaveDetail.put("manager", row.getCell(2).getStringCellValue().trim());
            leaveDetail.put("start_date", row.getCell(3).getStringCellValue().trim() + "T00:00:00");
            leaveDetail.put("end_date", row.getCell(4).getStringCellValue().trim() + "T00:00:00");
            leaveDetail.put("display_name", row.getCell(1).getStringCellValue().trim());
            leaveDetail.put("first_name", getFirstName(row.getCell(1).getStringCellValue()));
            leaveDetail.put("last_name", getLastName(row.getCell(1).getStringCellValue()));
            leaveDetail.put("name", row.getCell(1).getStringCellValue());
            leaveDetail.put("email", "example@example.com");
            leaveDetail.put("no_of_hours", (int) row.getCell(5).getNumericCellValue());

            leaveDetails.add(leaveDetail);
        }

        workbook.close();
        return leaveDetails;
    }

    /**
     * Group leave details by manager's name.
     */
    private Map<String, List<Map<String, Object>>> groupLeaveDetailsByManager(List<Map<String, Object>> leaveDetails) {
        Map<String, List<Map<String, Object>>> groupedByManager = new HashMap<>();
        for (Map<String, Object> leaveDetail : leaveDetails) {
            String managerName = (String) leaveDetail.get("manager");
            groupedByManager.computeIfAbsent(managerName, k -> new ArrayList<>()).add(leaveDetail);
        }
        return groupedByManager;
    }

    /**
     * Build a JSON response with leave details.
     */
    private Map<String, Object> buildJsonResponse(String senderName, String senderEmail, String companyName, String createOn, List<Map<String, Object>> leaveDetails) {
        Map<String, Object> data = new HashMap<>();
        data.put("requester_name", senderName);
        data.put("requester_email", senderEmail);
        data.put("requesterCompany", companyName);
        data.put("createOn", createOn);
        data.put("leave_details", leaveDetails);

        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("data", data);
        jsonResponse.put("state", "submitted");
        jsonResponse.put("action", "CREATE");
        return jsonResponse;
    }

    private String getFirstName(String fullName) {
        return fullName.split(" ")[0];
    }

    private String getLastName(String fullName) {
        String[] names = fullName.split(" ");
        return names.length > 1 ? names[names.length - 1] : "";
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return email != null && email.matches(emailRegex);
    }

    private String parseSenderName(String senderEmail) {
        if (senderEmail.contains("<")) {
            return senderEmail.substring(0, senderEmail.indexOf("<")).trim();
        }
        return senderEmail.split("@")[0];
    }

    private String parseCompanyName(String subject) {
        return subject;
    }
}
