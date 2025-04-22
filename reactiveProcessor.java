package org.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class reactiveProcessor {

    public static void main(String[] args) {
        Map<String, Object> submission = Map.of(
                "form", "123123123131dafwefw21e1eac",
                "data", Map.of(
                        "requester_name", "John Doe",
                        "requester_email", "oU2d3@example.com",
                        "requesterCompany", "Acme Inc.",
                        "leave_details", List.of(
                                Map.of(
                                        "manager", "Muffi",
                                        "start_date", "2023-07-01T00:00:00",
                                        "end_date", "2023-07-05T00:00:00"
                                ),
                                Map.of(
                                        "manager", "Alice",
                                        "start_date", "2023-07-06T00:00:00",
                                        "end_date", "2023-07-10T00:00:00"
                                )
                        )
                ),
                "metadata", Map.of(
                        "timezone", "UTC",
                        "browserName", "Chrome"
                )
        );


        List<Map<String, Object>> flattenedSubmissions = flatenSubmissionDataList(submission, "leave_details");

        System.out.println(flattenedSubmissions);
    }
    private static List<Map<String, Object>> flatenSubmissionDataList(final Map<String, Object> submissionMap, final String listKeyName) {
        // Result list to hold the new submissions.
        List<Map<String, Object>> resultSubmissions = new ArrayList<>();

        // Retrieve the 'data' map from the submission.
        Map<String, Object> data = (Map<String, Object>) submissionMap.get("data");
        if (data == null || !data.containsKey(listKeyName)) {
            // If the 'data' map or listKeyName is missing, return the original submission in a list.
            resultSubmissions.add(submissionMap);
            return resultSubmissions;
        }

        // Retrieve the list to be flattened from the 'data' map.
        Object listObject = data.get(listKeyName);

        // Ensure the listObject is indeed a List before proceeding.
        if (!(listObject instanceof List)) {
            // If it's not a list, return the original submission in a list.
            resultSubmissions.add(submissionMap);
            return resultSubmissions;
        }

        List<Map<String, Object>> listToFlatten = (List<Map<String, Object>>) listObject;

        // Iterate through each object in the list and create a new submission map for each.
        for (Map<String, Object> listItem : listToFlatten) {
            // Create a deep copy of the original submission map.
            Map<String, Object> newSubmissionMap = new HashMap<>(submissionMap);

            // Create a deep copy of the original 'data' map.
            Map<String, Object> newData = new HashMap<>(data);

            // Replace the list with the single listItem in the 'data' map.
            newData.put(listKeyName, listItem);

            // Update the 'data' field in the new submission map.
            newSubmissionMap.put("data", newData);

            // Add the new submission map to the result list.
            resultSubmissions.add(newSubmissionMap);
        }

        return resultSubmissions;
    }
}
