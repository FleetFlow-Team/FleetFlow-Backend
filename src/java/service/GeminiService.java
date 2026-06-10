package service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GeminiService {

    // TODO: chuyển sang config sau
    private static final String API_KEY =
            "";

    private static final String MODEL =
            "gemini-2.5-flash";

    public String askGemini(String prompt) throws Exception {

        String endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL
                + ":generateContent?key="
                + API_KEY;

        URL url = new URL(endpoint);

        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty(
                "Content-Type",
                "application/json");

        conn.setDoOutput(true);

        String jsonBody =
                "{"
                + "\"contents\":["
                + "{"
                + "\"parts\":["
                + "{"
                + "\"text\":\""
                + prompt.replace("\"", "\\\"")
                + "\""
                + "}"
                + "]"
                + "}"
                + "]"
                + "}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
        }

        BufferedReader br =
                new BufferedReader(
                        new InputStreamReader(
                                conn.getInputStream(),
                                "UTF-8"));

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line = br.readLine()) != null) {
            response.append(line);
        }

        return response.toString();
    }
}