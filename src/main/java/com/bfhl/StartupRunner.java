package com.bfhl;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;

@Component
public class StartupRunner implements CommandLineRunner {

    private static final String NAME    = "Shubh Sharma";
    private static final String REG_NO  = "REG12348";   // last 2 digits = 47 (ODD) -> Q1
    private static final String EMAIL   = "shubh@example.com";

    // Q1: Find the highest salary NOT paid on the 1st of any month,
    // along with employee name (first + last), age, and department name.
    private static final String FINAL_QUERY =
        "SELECT p.AMOUNT AS SALARY, " +
        "CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME, " +
        "TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE, " +
        "d.DEPARTMENT_NAME " +
        "FROM PAYMENTS p " +
        "JOIN EMPLOYEE e ON p.EMP_ID = e.EMP_ID " +
        "JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID " +
        "WHERE DAY(p.PAYMENT_TIME) != 1 " +
        "ORDER BY p.AMOUNT DESC " +
        "LIMIT 1";

    @Override
    public void run(String... args) throws Exception {
        RestTemplate restTemplate = new RestTemplate();

        // Step 1 — generate webhook
        // String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";
        String generateUrl = "http://localhost:8888/hiring/generateWebhook/JAVA";

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("name",   NAME);
        requestBody.put("regNo",  REG_NO);
        requestBody.put("email",  EMAIL);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(generateUrl, entity, Map.class);

        if (response.getBody() == null) {
            System.err.println("[BFHL] ERROR: empty response from generateWebhook");
            return;
        }

        String accessToken = (String) response.getBody().get("accessToken");
        String webhook     = (String) response.getBody().get("webhook");

        System.out.println("[BFHL] webhook  : " + webhook);
        System.out.println("[BFHL] token    : " + accessToken);
        System.out.println("[BFHL] query    : " + FINAL_QUERY);

        // Step 2 — submit answer with retry (up to 4 attempts)
        HttpHeaders finalHeaders = new HttpHeaders();
        finalHeaders.setContentType(MediaType.APPLICATION_JSON);
        finalHeaders.setBearerAuth(accessToken);

        Map<String, String> sqlBody = new HashMap<>();
        sqlBody.put("finalQuery", FINAL_QUERY);

        HttpEntity<Map<String, String>> finalEntity = new HttpEntity<>(sqlBody, finalHeaders);

        int maxAttempts = 4;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<String> result =
                    restTemplate.postForEntity(webhook, finalEntity, String.class);
                System.out.println("[BFHL] Submission response (" + attempt + "): "
                    + result.getStatusCode() + " — " + result.getBody());
                break;
            } catch (Exception ex) {
                System.err.println("[BFHL] Attempt " + attempt + " failed: " + ex.getMessage());
                if (attempt < maxAttempts) {
                    Thread.sleep(2000L * attempt);
                } else {
                    System.err.println("[BFHL] All attempts exhausted.");
                }
            }
        }
    }
}