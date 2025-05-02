package org.component;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class HashingUtils {

    // AES symmetric key (must be 16, 24, or 32 bytes for AES encryption)
    private static final String SECRET_KEY = "ApacheCommonsKey"; // Use a more securely stored key in production


    public static String generateReplyId(String routeId, String taskId) throws Exception {
        // Concatenate the RouteID and TaskID using a separator (e.g., '-')
        String concatenated = routeId + "-" + taskId;

        // Perform AES encryption
        Cipher cipher = Cipher.getInstance("AES");
        SecretKey secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(concatenated.getBytes());

        // Encode the encrypted bytes using Apache Commons Base64
        return Base64.encodeBase64URLSafeString(encrypted);
    }


    public static String[] decodeReplyId(String replyId) throws Exception {
        // Decode the Base64-encoded replyId using Apache Commons Codec
        byte[] encrypted = Base64.decodeBase64(replyId);

        // Perform AES decryption
        Cipher cipher = Cipher.getInstance("AES");
        SecretKey secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decrypted = cipher.doFinal(encrypted);

        // Get the decrypted string and split it into RouteID and TaskID using the separator
        String decryptedString = new String(decrypted);
        return decryptedString.split("-");
    }


    public static String generateSha256Hash(String input) {
        return DigestUtils.sha256Hex(input);
    }


    public static String generateShortReplyId(String input) {
        // Hash the input string (e.g., SHA-256) and encode it in Base64
        byte[] sha256Bytes = DigestUtils.sha256(input);
        return Base64.encodeBase64URLSafeString(sha256Bytes).substring(0, 16); // Take first 16 chars
    }

    public class Main {

        public static void main(String[] args) throws Exception {

            String routeId = "12345";
            String taskId = "67890";

            String replyId = HashingUtils.generateReplyId(routeId, taskId);
            System.out.println("Generated Reply ID: " + replyId);

            String[] decodedValues = HashingUtils.decodeReplyId(replyId);
            System.out.println("Decoded RouteID: " + decodedValues[0]);
            System.out.println("Decoded TaskID: " + decodedValues[1]);

            String sha256Hash = HashingUtils.generateSha256Hash(routeId + "-" + taskId);
            System.out.println("Generated SHA-256 Hash: " + sha256Hash);

            String shortReplyId = HashingUtils.generateShortReplyId(routeId + "-" + taskId);
            System.out.println("Shortened Reply ID: " + shortReplyId);
        }
    }
}