package org.component;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.support.DefaultProducer;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import jakarta.activation.DataHandler;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class EmailCsvGroupProcessProducer extends DefaultProducer {

    public EmailCsvGroupProcessProducer(EmailCsvProcessorEndpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) {
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
            String createOn = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(receivedDate);

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
                        try (InputStream attachmentInputStream = dh.getInputStream()) {
                            // Normalize EOL and encoding in one step
                            InputStream normalizedInputStream = normalizeEOLAndEncoding(attachmentInputStream);

                            try (InputStreamReader reader = new InputStreamReader(normalizedInputStream, StandardCharsets.UTF_8)) {
                                leaveDetails.addAll(parseCsvToLeaveDetails(reader));
                            }
                        } catch (Exception e) {
                            setExchangeError(exchange, "Invalid CSV file: " + e.getMessage());
                            return;
                        }
                    } else if (fileName.toLowerCase().endsWith(".xlsx")) {
                        validFileFound = true;
                        try (InputStream attachmentInputStream = dh.getInputStream()) {
                            leaveDetails.addAll(parseXlsxToLeaveDetails(attachmentInputStream));
                        } catch (Exception e) {
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

            System.out.println("Successfully processed the file!");
        } catch (Exception e) {
            setExchangeError(exchange, "An unexpected error occurred: " + e.getMessage());
        }
    }

    // Normalizes EOL and fixes encoding, combining two steps
    private InputStream normalizeEOLAndEncoding(InputStream inputStream) throws IOException {
        // Read the entire input into a String
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        // Normalize line endings:
        // - Convert CRLF (\r\n) to LF (\n)
        content = content.replace("\r\n", "\n");

        // - Convert CR (\r) to LF (\n)
        content = content.replace("\r", "\n");

        // Remove redundant blank lines (e.g., \n\n -> \n)
        content = content.replaceAll("\n+", "\n");

        // Return normalized InputStream
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    // Parses CSV file content into leave details
    private List<Map<String, Object>> parseCsvToLeaveDetails(InputStreamReader reader) throws Exception {
        List<Map<String, Object>> leaveDetails = new ArrayList<>();

        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            CSVReader csvReader = new CSVReaderBuilder(bufferedReader).build();

            // Read header row
            String[] headers = csvReader.readNext();
            if (headers == null || headers.length == 0) {
                throw new RuntimeException("CSV file is empty or has no headers.");
            }

            // Trim and normalize headers
            headers = Arrays.stream(headers)
                    .map(String::trim)
                    .filter(header -> header != null && !header.isEmpty()) // Ignore unnamed or empty columns
                    .toArray(String[]::new);

            // Validate headers against expected format
            String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours"};
            if (headers.length < expectedHeaders.length || !Arrays.asList(headers).containsAll(Arrays.asList(expectedHeaders))) {
                throw new RuntimeException("Invalid or unexpected CSV header format: " + Arrays.toString(headers));
            }

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                // Trim each row and skip blank rows
                row = Arrays.stream(row).map(String::trim).toArray(String[]::new);

                if (Arrays.stream(row).allMatch(String::isBlank)) {
                    continue; // Skip empty rows
                }

                // Map valid row data to the expected structure
                Map<String, Object> leaveDetail = mapRowToLeaveDetails(row);
                leaveDetails.add(leaveDetail);
            }
        }

        return leaveDetails;
    }

    // Parses XLSX file content into leave details
    private List<Map<String, Object>> parseXlsxToLeaveDetails(InputStream inputStream) throws Exception {
        List<Map<String, Object>> leaveDetails = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            String[] headers = null;
            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                headers = Arrays.stream(parseXlsxRow(headerRow))
                        .filter(header -> header != null && !header.isEmpty()) // Skip empty headers
                        .toArray(String[]::new);
            }

            if (headers == null || headers.length == 0) {
                throw new RuntimeException("XLSX file is empty or has no headers.");
            }

            String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours"};
            if (headers.length < expectedHeaders.length || !Arrays.asList(headers).containsAll(Arrays.asList(expectedHeaders))) {
                throw new RuntimeException("Invalid XLSX header format: " + Arrays.toString(headers));
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                String[] rowData = parseXlsxRow(row);
                if (rowData != null && rowData.length >= expectedHeaders.length) {
                    Map<String, Object> leaveDetail = mapRowToLeaveDetails(rowData);
                    leaveDetails.add(leaveDetail);
                }
            }
        }

        return leaveDetails;
    }

    private Map<String, Object> mapRowToLeaveDetails(String[] row) {
        Map<String, Object> leaveDetail = new HashMap<>();
        leaveDetail.put("employee_id", Long.parseLong(row[0]));
        leaveDetail.put("employee_name", row[1]);
        leaveDetail.put("manager", row[2]);
        leaveDetail.put("start_date", row[3]);
        leaveDetail.put("end_date", row[4]);
        leaveDetail.put("no_of_hours", Integer.parseInt(row[5]));
        return leaveDetail;
    }

    private String[] parseXlsxRow(Row row) {
        List<String> values = new ArrayList<>();
        for (Cell cell : row) {
            switch (cell.getCellType()) {
                case STRING:
                    values.add(cell.getStringCellValue());
                    break;
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        values.add(cell.getDateCellValue().toString());
                    } else {
                        values.add(String.valueOf((long) cell.getNumericCellValue()));
                    }
                    break;
                case BLANK:
                    values.add("");
                    break;
                default:
                    values.add(cell.toString());
            }
        }
        return values.toArray(new String[0]);
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@");
    }

    private String parseSenderName(String email) {
        return email.split("@")[0];
    }

    private String parseCompanyName(String subject) {
        return subject;
    }

    private void setExchangeError(Exchange exchange, String errorMessage) {
        System.err.println(errorMessage);
    }
}
