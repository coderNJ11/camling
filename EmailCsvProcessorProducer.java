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

import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

public class EmailCsvProcessorProducer extends DefaultProducer {

    public EmailCsvProcessorProducer(EmailCsvProcessorEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            Message mailMessage = exchange.getIn();
            if (mailMessage == null) {
                setExchangeError(exchange, "No email found in the exchange!");
                return;
            }

            String senderEmail = (String) mailMessage.getHeader("From");
            if (!isValidEmail(senderEmail)) {
                setExchangeError(exchange, "Invalid or missing email address!");
                return;
            }

            String subject = (String) mailMessage.getHeader("Subject");
            Date receivedDate = (Date) mailMessage.getHeader("CamelMailMessageReceivedDate");

            if (senderEmail == null || subject == null || receivedDate == null) {
                setExchangeError(exchange, "Required email metadata (sender, subject, or received date) is missing!");
                return;
            }

            String senderName = parseSenderName(senderEmail);
            String companyName = parseCompanyName(subject);

            String createOn = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(receivedDate);

            List<Map<String, Object>> leaveDetails = new ArrayList<>();
            boolean validFileFound = false;

            if (mailMessage instanceof AttachmentMessage) {
                AttachmentMessage attachmentMessage = (AttachmentMessage) mailMessage;
                Map<String, DataHandler> attachments = attachmentMessage.getAttachments();

                for (Map.Entry<String, DataHandler> attachmentEntry : attachments.entrySet()) {
                    DataHandler dh = attachmentEntry.getValue();
                    String fileName = dh.getName();

                    if (fileName.toLowerCase().endsWith(".csv")) {
                        validFileFound = true;
                        try (InputStreamReader reader = new InputStreamReader(dh.getInputStream())) {
                            leaveDetails.addAll(parseCsvToLeaveDetails(reader));
                        } catch (RuntimeException e) {
                            setExchangeError(exchange, "Please attach a valid CSV file: " + e.getMessage());
                            return;
                        }
                    } else if (fileName.toLowerCase().endsWith(".xlsx")) {
                        validFileFound = true;
                        try (InputStream inputStream = dh.getInputStream()) {
                            leaveDetails.addAll(parseXlsxToLeaveDetails(inputStream));
                        } catch (RuntimeException e) {
                            setExchangeError(exchange, "Please attach a valid XLSX file: " + e.getMessage());
                            return;
                        }
                    }
                }
            }

            if (!validFileFound) {
                setExchangeError(exchange, "Please attach a CSV or XLSX file");
                return;
            }

            if (leaveDetails.isEmpty()) {
                setExchangeError(exchange, "File format is correct but values are missing");
                return;
            }

            Map<String, Object> jsonResponse = buildJsonResponse(senderName, senderEmail, companyName, createOn, leaveDetails);

            exchange.getIn().setBody(jsonResponse);
        } catch (Exception e) {
            setExchangeError(exchange, "An unexpected error occurred: " + e.getMessage());
        }
    }

    private void setExchangeError(Exchange exchange, String errorMessage) {
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, errorMessage);
        exchange.getIn().setHeader("ErrorReason", errorMessage);
        exchange.getIn().setBody(null);
    }

    private List<Map<String, Object>> parseCsvToLeaveDetails(InputStreamReader reader) throws Exception {
        CSVReader csvReader = new CSVReader(reader);
        List<Map<String, Object>> leaveDetails = new ArrayList<>();

        try {
            String[] headers = csvReader.readNext();
            String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours"};
            if (!Arrays.equals(headers, expectedHeaders)) {
                throw new RuntimeException("Invalid CSV header format!");
            }

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

                leaveDetails.add(createLeaveDetail(row));
            }
        } catch (CsvValidationException e) {
            throw new RuntimeException("Error processing CSV file!", e);
        } finally {
            csvReader.close();
        }
        return leaveDetails;
    }

    private List<Map<String, Object>> parseXlsxToLeaveDetails(InputStream inputStream) throws Exception {
        List<Map<String, Object>> leaveDetails = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            if (!rowIterator.hasNext()) {
                throw new RuntimeException("Invalid XLSX header format!");
            }

            Row headerRow = rowIterator.next();
            String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours"};
            String[] headers = new String[expectedHeaders.length];
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headerRow.getCell(i).getStringCellValue();
            }
            if (!Arrays.equals(headers, expectedHeaders)) {
                throw new RuntimeException("Invalid XLSX header format!");
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                String[] rowData = new String[expectedHeaders.length];
                for (int i = 0; i < rowData.length; i++) {
                    Cell cell = row.getCell(i);
                    if (cell == null || cell.getCellType() == CellType.BLANK) {
                        throw new RuntimeException("XLSX file format is correct but values are missing");
                    }
                    rowData[i] = cell.toString();
                }
                leaveDetails.add(createLeaveDetail(rowData));
            }
        }
        return leaveDetails;
    }

    private Map<String, Object> createLeaveDetail(String[] row) {
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
        return leaveDetail;
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }

        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        Pattern pattern = Pattern.compile(emailRegex);
        return pattern.matcher(email).matches();
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

    private String getFirstName(String fullName) {
        return fullName.split(" ")[0];
    }

    private String getLastName(String fullName) {
        String[] names = fullName.split(" ");
        return names.length > 1 ? names[names.length - 1] : "";
    }

    private Map<String, Object> buildJsonResponse(String senderName, String senderEmail, String companyName, String createOn, List<Map<String, Object>> leaveDetails) {
        Map<String, Object> data = new HashMap<>();
        data.put("requester_name", senderName);
        data.put("requester_email", senderEmail);
        data.put("requesterCompany", companyName);
        data.put("createOn", createOn);
        data.put("leave_details", leaveDetails);
        data.put("_formio_formId", "123123123131dafwefw21e1eac");
        data.put("_formio_submissionId", "");
        data.put("submit", true);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timezone", "UTC");
        metadata.put("browserName", "Chrome");
        metadata.put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/237.84.2.178 Safari/537.36");

        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("data", data);
        jsonResponse.put("metadata", metadata);
        jsonResponse.put("state", "submitted");
        jsonResponse.put("_vnote", "");
        jsonResponse.put("action", "CREATE");
        jsonResponse.put("uploadedFiles", Collections.emptyList());

        return jsonResponse;
    }
}
