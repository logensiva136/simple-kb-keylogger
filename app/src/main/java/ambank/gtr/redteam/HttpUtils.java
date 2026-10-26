package ambank.gtr.redteam;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HttpUtils {
    private static final String TAG = "HttpUtils";

    // Define the JSON Media Type for OkHttp
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Single thread-safe client instance shared across the Android app context
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    // Callback interface to receive status updates back on your UI/Activity layers
    public interface ApiCallback {
        void onSuccess(String responseBody);
        void onFailure(String errorMessage);
    }

    private HttpUtils() {} // Prevent instantiation

    /**
     * Sends a JSONObject to a backend API server asynchronously (Safe for Android Main Thread).
     */
    public static void postJsonLog(String serverUrl, JSONObject jsonPayload, final ApiCallback callback) {
        if (serverUrl == null || jsonPayload == null) {
            if (callback != null) callback.onFailure("URL or payload is null.");
            return;
        }

        // Build the request body with your dynamic JSONObject string
        RequestBody body = RequestBody.create(jsonPayload.toString(), JSON);

        Request request = new Request.Builder()
                .url(serverUrl)
                .post(body)
                .header("Accept", "application/json")
                .build();

        // enqueue executes the network call on a background worker thread automatically
        HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            private final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override
            public void onFailure(Call call, final IOException e) {
                Log.e(TAG, "Network delivery failed: " + e.getMessage());
                if (callback != null) {
                    // Safe switch back to Android main thread to update UI or throw Toasts safely
                    mainHandler.post(() -> callback.onFailure(e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                try (Response res = response) { // Auto-closes socket stream securely
                    final String responseData = res.body() != null ? res.body().string() : "";

                    if (res.isSuccessful()) {
                        Log.d(TAG, "Log successfully uploaded. Status: " + res.code());
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess(responseData));
                        }
                    } else {
                        Log.e(TAG, "Server rejected payload. Status: " + res.code());
                        if (callback != null) {
                            mainHandler.post(() -> callback.onFailure("Server error code: " + res.code()));
                        }
                    }
                }
            }
        });
    }
}