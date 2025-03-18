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
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class EmailCsvGroupProcessProducer extends DefaultProducer {
    private static final int MAX_EMAILS_PER_MONTH = 4; // Maximum emails per sender per month
    private static final int HOURS_PER_DAY = 8; // 1 Day = 8 Hours

    // Map to track email counts per sender
    // Key: sender email + monthly key ("yyyy-MM"), Value: email count for the month
    private final Map<String, Integer> emailCountsPerSender = new HashMap<>();

    public EmailCsvGroupProcessProducer(EmailCsvProcessorEndpoint endpoint) {
        super(endpoint);
    }

    @Override
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

            LocalDate currentDate = LocalDate.now();
            int dayOfMonth = currentDate.getDayOfMonth();
            if (dayOfMonth <= 20) {
                setExchangeError(exchange, "Emails can only be sent in the last 10 days of the calendar month.");
                return;
            }

            // Validate and update sender's monthly email count
            if (!isSenderAllowedToSendEmail(senderEmail, currentDate)) {
                setExchangeError(exchange, "Sender is only allowed to send 4 emails per month.");
                return;
            }

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
                            InputStream normalizedInputStream = normalizeEOLAndEncoding(attachmentInputStream);
                            try (InputStreamReader reader = new InputStreamReader(normalizedInputStream, StandardCharsets.UTF_8)) {
                                leaveDetails.addAll(parseCsvToLeaveDetails(reader, currentDate));
                            }
                        } catch (Exception e) {
                            setExchangeError(exchange, "Invalid CSV file: " + e.getMessage());
                            return;
                        }
                    }
                }
            }

            if (!validFileFound) {
                setExchangeError(exchange, "Please attach a valid CSV file.");
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

    private InputStream normalizeEOLAndEncoding(InputStream inputStream) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        content = content.replace("\r\n", "\n").replace("\r", "\n").replaceAll("\n+", "\n");
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private List<Map<String, Object>> parseCsvToLeaveDetails(InputStreamReader reader, LocalDate currentDate) throws Exception {
        List<Map<String, Object>> leaveDetails = new ArrayList<>();
        CSVReader csvReader = new CSVReaderBuilder(new BufferedReader(reader)).build();

        String[] headers = csvReader.readNext();
        if (headers == null) {
            throw new RuntimeException("CSV file is empty.");
        }

        String[] expectedHeaders = {"employee_id", "employee_name", "manager", "start_date", "end_date", "no_of_hours", "type"};
        if (!Arrays.asList(headers).containsAll(Arrays.asList(expectedHeaders))) {
            throw new RuntimeException("Invalid CSV format.");
        }

        String[] row;
        while ((row = csvReader.readNext()) != null) {
            row = Arrays.stream(row).map(String::trim).toArray(String[]::new);

            if (Arrays.stream(row).allMatch(String::isBlank)) {
                continue;
            }

            Map<String, Object> detail = validateAndMapRow(row, currentDate);
            leaveDetails.add(detail);
        }

        return leaveDetails;
    }

    private Map<String, Object> validateAndMapRow(String[] row, LocalDate currentDate) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        LocalDate startDate = LocalDate.parse(row[3], dateFormatter);
        LocalDate endDate = LocalDate.parse(row[4], dateFormatter);

        if (startDate.getMonthValue() != currentDate.getMonthValue() || startDate.getYear() != currentDate.getYear() ||
                endDate.getMonthValue() != currentDate.getMonthValue() || endDate.getYear() != currentDate.getYear()) {
            throw new IllegalArgumentException("start_date and end_date must be in the current calendar month.");
        }

        if (!endDate.isAfter(startDate) && !endDate.isEqual(startDate)) {
            throw new IllegalArgumentException("end_date must be after or equal to start_date.");
        }

        String noOfHours = row[5];
        int totalHours = parseHours(noOfHours);

        if (totalHours < HOURS_PER_DAY && !endDate.isEqual(startDate)) {
            throw new IllegalArgumentException("For less than 8 hours, start_date and end_date must be the same.");
        }

        if (totalHours > HOURS_PER_DAY) {
            long dayDifference = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (dayDifference < 1) {
                throw new IllegalArgumentException("For more than 8 hours, start_date and end_date must span at least 1 day.");
            }
        }

        Map<String, Object> leaveDetail = new HashMap<>();
        leaveDetail.put("employee_id", Long.parseLong(row[0]));
        leaveDetail.put("employee_name", row[1]);
        leaveDetail.put("manager", row[2]);
        leaveDetail.put("start_date", startDate.format(dateFormatter));
        leaveDetail.put("end_date", endDate.format(dateFormatter));
        leaveDetail.put("no_of_hours", formatHours(totalHours));
        leaveDetail.put("type", row[6]);

        return leaveDetail;
    }

    private String getMonthlyKey(String senderEmail, LocalDate currentDate) {
        return senderEmail + "-" + currentDate.getYear() + "-" + currentDate.getMonthValue();
    }

    private boolean isSenderAllowedToSendEmail(String senderEmail, LocalDate currentDate) {
        String monthlyKey = getMonthlyKey(senderEmail, currentDate);
        emailCountsPerSender.putIfAbsent(monthlyKey, 0); // Reset count if this is the first email of the month
        int emailCount = emailCountsPerSender.get(monthlyKey);

        if (emailCount >= MAX_EMAILS_PER_MONTH) {
            return false; // Sender exceeded the limit for the month
        }

        // Increment the count and persist it
        emailCountsPerSender.put(monthlyKey, emailCount + 1);
        return true;
    }

    private int parseHours(String noOfHours) {
        int totalHours = 0;

        if (noOfHours.contains("D") || noOfHours.contains("H")) {
            String[] parts = noOfHours.split("D|H");
            if (noOfHours.contains("D")) {
                totalHours += Integer.parseInt(parts[0]) * HOURS_PER_DAY;
            }
            if (noOfHours.contains("H")) {
                totalHours += Integer.parseInt(parts[1]);
            }
        }

        return totalHours;
    }

    private String formatHours(int totalHours) {
        int days = totalHours / HOURS_PER_DAY;
        int hours = totalHours % HOURS_PER_DAY;
        return (days > 0 ? days + "D" : "") + (hours > 0 ? hours + "H" : "");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@");
    }

    private void setExchangeError(Exchange exchange, String errorMessage) {
        exchange.setException(new Exception(errorMessage));
        System.err.println(errorMessage);
    }
}
