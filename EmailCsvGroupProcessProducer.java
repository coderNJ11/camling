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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class EmailCsvGroupProcessProducer extends DefaultProducer {

    public EmailCsvGroupProcessProducer(EmailCsvProcessorEndpoint endpoint) {
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
                        try (InputStream normalizedInputStream = normalizeEOL(dh.getInputStream());
                             InputStreamReader reader = fixEncoding(new InputStreamReader(normalizedInputStream, detectEncoding(normalizedInputStream)))) {
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

            Map<String, List<Map<String, Object>>> groupedByManager = groupLeaveDetailsByManager(leaveDetails);
            List<Map<String, Object>> jsonResponses = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByManager.entrySet()) {
                String managerName = entry.getKey();
                List<Map<String, Object>> managerLeaveDetails = entry.getValue();

                Map<String, Object> jsonResponse = buildJsonResponse(
                        senderName, senderEmail, companyName, createOn, managerLeaveDetails
                );
                jsonResponse.put("manager_name", managerName);

                jsonResponses.add(jsonResponse);
            }

            exchange.getIn().setBody(jsonResponses);
        } catch (Exception e) {
            setExchangeError(exchange, "An unexpected error occurred: " + e.getMessage());
        }
    }

    // Method to normalize EOL characters (handles CR, LF, CRLF)
    private InputStream normalizeEOL(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write("\n"); // Normalize all EOL to LF
            }
        }
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    private Charset detectEncoding(InputStream inputStream) {
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
            bufferedInputStream.mark(4);
            byte[] bom = new byte[4];
            int read = bufferedInputStream.read(bom, 0, bom.length);
            bufferedInputStream.reset();

            if (read == 4) {
                if ((bom[0] == (byte) 0xEF) && (bom[1] == (byte) 0xBB) && (bom[2] == (byte) 0xBF)) {
                    return StandardCharsets.UTF_8;
                } else if ((bom[0] == (byte) 0xFE) && (bom[1] == (byte) 0xFF)) {
                    return StandardCharsets.UTF_16BE;
                } else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)) {
                    return StandardCharsets.UTF_16LE;
                }
            }
        } catch (IOException ignored) {}
        return StandardCharsets.UTF_8;
    }

    private InputStreamReader fixEncoding(InputStreamReader reader) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(reader);
        String firstLine = bufferedReader.readLine();
        if (firstLine.charAt(0) == 0xFEFF) {
            return new InputStreamReader(new ByteArrayInputStream(firstLine.substring(1).getBytes(StandardCharsets.UTF_8)));
        }
        return reader;
    }

    private List<Map<String, Object>> parseCsvToLeaveDetails(InputStreamReader reader) throws Exception {
        List<Map<String, Object>> leaveDetails = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            CSVReader csvReader = new CSVReaderBuilder(bufferedReader)
                    .withSkipLines(0)
                    .build();

            String[] headers = csvReader.readNext();
            if (headers == null) {
                throw new RuntimeException("CSV file is empty!");
            }

            headers = Arrays.stream(headers).map(String::trim).toArray(String[]::new);
            String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours"};
            if (!Arrays.equals(headers, expectedHeaders)) {
                throw new RuntimeException("Invalid CSV header format!");
            }

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                row = Arrays.stream(row).map(String::trim).toArray(String[]::new);
                if (row.length != headers.length) {
                    throw new RuntimeException("CSV row data is incomplete.");
                }

                Map<String, Object> leaveDetail = new HashMap<>();
                leaveDetail.put("_id", Integer.parseInt(row[0]));
                leaveDetail.put("manager", row[2]);
                leaveDetail.put("start_date", row[3]);
                leaveDetail.put("end_date", row[4]);
                leaveDetail.put("display_name", row[1]);
                leaveDetail.put("first_name", getFirstName(row[1]));
                leaveDetail.put("last_name", getLastName(row[1]));
                leaveDetail.put("name", row[1]);
                leaveDetail.put("email", "example@example.com");
                leaveDetail.put("no_of_hours", Integer.parseInt(row[5]));

                leaveDetails.add(leaveDetail);
            }
        }
        return leaveDetails;
    }


    private Map<String, List<Map<String, Object>>> groupLeaveDetailsByManager(List<Map<String, Object>> leaveDetails) {
        Map<String, List<Map<String, Object>>> groupedByManager = new HashMap<>();
        for (Map<String, Object> leaveDetail : leaveDetails) {
            String managerName = (String) leaveDetail.get("manager");
            groupedByManager.computeIfAbsent(managerName, k -> new ArrayList<>()).add(leaveDetail);
        }
        return groupedByManager;
    }

    private void setExchangeError(Exchange exchange, String errorMessage) {
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, errorMessage);
        exchange.getIn().setHeader("ErrorReason", errorMessage);
        exchange.getIn().setBody(null);
    }

    private List<Map<String, Object>> parseXlsxToLeaveDetails(InputStream inputStream) throws Exception {
        List<Map<String, Object>> leaveDetails = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        Iterator<Row> rowIterator = sheet.rowIterator();
        Row headerRow = rowIterator.next();

        String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours"};
        for (int i = 0; i < expectedHeaders.length; i++) {
            String headerCell = headerRow.getCell(i).getStringCellValue().trim();
            if (!headerCell.equalsIgnoreCase(expectedHeaders[i])) {
                throw new RuntimeException("Invalid XLSX header format!");
            }
        }

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
