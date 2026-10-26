package ambank.gtr.testteam;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/** A custom-drawn keyboard: gradient themes, ripples, key pop-ups, caps lock. */
public class FancyKeyboardView extends View {

    private static final String TAG = "FancyKeyboard";

    public interface Listener {
        void onText(String text);
        void onDelete();
        void onEnter();
    }

    // ---- themes ---------------------------------------------------------
    private static class Theme {
        final String name;
        final int bgTop, bgBottom, key, special, text, accent;
        Theme(String n, int bt, int bb, int k, int s, int t, int a) {
            name = n; bgTop = bt; bgBottom = bb; key = k; special = s; text = t; accent = a;
        }
    }

    private static final Theme[] THEMES = {
        new Theme("Aurora", 0xFF1B1035, 0xFF2D1B69, 0xFF3B2F7A, 0xFF2A2058, 0xFFF3EEFF, 0xFFB388FF),
        new Theme("Sunset", 0xFF2B1220, 0xFF7A2E3A, 0xFFA04A55, 0xFF6B2A36, 0xFFFFF3E8, 0xFFFFB74D),
        new Theme("Mint",   0xFFE8F5F1, 0xFFC9E8DF, 0xFFFFFFFF, 0xFFB5D9CE, 0xFF1F3B36, 0xFF1DE9B6),
    };

    // ---- layouts ("~" = half-width spacer) -------------------------------
    private static final String[] LETTERS = {
        "q w e r t y u i o p",
        "~ a s d f g h j k l ~",
        "⇧ z x c v b n m ⌫",
        "?123 🎨 space . ⏎"
    };
    private static final String[] SYMBOLS = {
        "1 2 3 4 5 6 7 8 9 0",
        "@ # $ % & - + ( ) /",
        "* \" ' : ; ! ? ⌫",
        "ABC 🎨 space , ⏎"
    };

    private static class Key {
        final String label;
        final float weight;
        final RectF r = new RectF();
        Key(String l) { label = l; weight = weightOf(l); }

        boolean spacer() { return "~".equals(label); }

        boolean special() {
            switch (label) {
                case "⇧": case "⌫": case "?123": case "ABC": case "🎨": case "⏎": return true;
                default: return false;
            }
        }

        private static float weightOf(String l) {
            switch (l) {
                case "~": return 0.5f;
                case "space": return 4.5f;
                case "⇧": case "⌫": case "?123": case "ABC": case "⏎": return 1.5f;
                default: return 1f;
            }
        }
    }

    // ---- state ----------------------------------------------------------
    private final List<List<Key>> rows = new ArrayList<>();
    private Listener listener;
    private int themeIdx = 0;
    private boolean symbols, shift, caps;
    private long lastShiftTap;

    private Key pressed;
    private float rx, ry, progress = 1f;
    private boolean repeated;

    private final Paint bg = new Paint(), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            stroke = new Paint(Paint.ANTI_ALIAS_FLAG), ripple = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);

    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            if (pressed != null && "⌫".equals(pressed.label)) {
                repeated = true;
                Log.d(TAG, "Key pressed: ⌫ (repeat)");
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) listener.onDelete();
                handler.postDelayed(this, 45);
            }
        }
    };

    public FancyKeyboardView(Context ctx) {
        super(ctx);
        text.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x22FFFFFF);
        anim.setDuration(260);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
        buildRows(LETTERS);
    }

    public void setListener(Listener l) { listener = l; }

    /** Called by the service after each edit so we capitalise like the field wants. */
    public void setAutoShift(boolean on) {
        if (!caps && !symbols && shift != on) { shift = on; invalidate(); }
    }

    // ---- layout ---------------------------------------------------------
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float previewH() { return dp(56); }

    private void buildRows(String[] layout) {
        rows.clear();
        for (String line : layout) {
            List<Key> row = new ArrayList<>();
            for (String tok : line.split(" ")) row.add(new Key(tok));
            rows.add(row);
        }
        layoutKeys();
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (previewH() + 4 * dp(54)));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutKeys();
        rebuildShader();
    }

    private void rebuildShader() {
        Theme t = THEMES[themeIdx];
        bg.setShader(new LinearGradient(0, 0, 0, Math.max(1, getHeight()),
                t.bgTop, t.bgBottom, Shader.TileMode.CLAMP));
    }

    private void layoutKeys() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float gap = dp(5), rowH = (h - previewH()) / rows.size();
        for (int i = 0; i < rows.size(); i++) {
            List<Key> row = rows.get(i);
            float total = 0;
            for (Key k : row) total += k.weight;
            float unit = (w - 2 * gap - gap * (row.size() - 1)) / total;
            float x = gap, y = previewH() + i * rowH;
            for (Key k : row) {
                k.r.set(x, y + gap / 2, x + k.weight * unit, y + rowH - gap / 2);
                x += k.weight * unit + gap;
            }
        }
    }

    // ---- drawing --------------------------------------------------------
    private String labelFor(Key k) {
        if (k.label.equals("⇧")) return caps ? "⇪" : "⇧";
        if (k.label.equals("space")) return THEMES[themeIdx].name;
        if (k.label.length() == 1 && Character.isLetter(k.label.charAt(0)) && (shift || caps))
            return k.label.toUpperCase();
        return k.label;
    }

    private boolean previewable(Key k) {
        return !k.special() && !k.label.equals("space") && !k.spacer();
    }

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        Theme t = THEMES[themeIdx];
        for (List<Key> row : rows)
            for (Key k : row) if (!k.spacer()) drawKey(c, k, t);
        if (pressed != null && previewable(pressed)) drawPreview(c, pressed, t);
    }

    private void drawKey(Canvas c, Key k, Theme t) {
        float rad = dp(9);
        boolean down = k == pressed;
        boolean on = k.label.equals("⇧") && (shift || caps);
        float in = down ? dp(2) : 0;
        rect.set(k.r.left + in, k.r.top + in, k.r.right - in, k.r.bottom - in);

        fill.setColor(0x40000000);                       // soft drop shadow
        c.drawRoundRect(rect.left, rect.top + dp(2), rect.right, rect.bottom + dp(2), rad, rad, fill);
        fill.setColor(down || on ? t.accent : k.special() ? t.special : t.key);
        c.drawRoundRect(rect, rad, rad, fill);
        c.drawRoundRect(rect, rad, rad, stroke);

        if (down) {                                      // ripple clipped to the key
            c.save();
            path.reset();
            path.addRoundRect(rect, rad, rad, Path.Direction.CW);
            c.clipPath(path);
            ripple.setColor(Color.WHITE);
            ripple.setAlpha((int) (120 * (1 - progress)));
            c.drawCircle(rx, ry, progress * rect.width(), ripple);
            c.restore();
        }

        String s = labelFor(k);
        boolean space = k.label.equals("space");
        text.setColor(down || on ? 0xFF111111 : t.text);
        if (space) text.setAlpha(140);
        text.setTextSize(space ? dp(12) : s.codePointCount(0, s.length()) == 1 ? dp(21) : dp(14));
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2, text);
    }

    private void drawPreview(Canvas c, Key k, Theme t) {
        float bw = k.r.width() * 1.35f, bh = dp(46);
        float cx = Math.max(bw / 2, Math.min(getWidth() - bw / 2, k.r.centerX()));
        float bottom = k.r.top - dp(4);
        float s = 0.7f + 0.3f * progress;                // pop-in
        rect.set(cx - bw / 2, bottom - bh, cx + bw / 2, bottom);
        c.save();
        c.scale(s, s, cx, bottom);
        fill.setColor(t.accent);
        c.drawRoundRect(rect, dp(12), dp(12), fill);
        text.setColor(0xFF111111);
        text.setTextSize(dp(28));
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(labelFor(k), cx, rect.centerY() - (fm.ascent + fm.descent) / 2, text);
        c.restore();
    }

    // ---- touch ----------------------------------------------------------
    private Key findKey(float x, float y) {
        float slop = dp(2.5f);
        for (List<Key> row : rows)
            for (Key k : row) {
                if (k.spacer()) continue;
                if (x >= k.r.left - slop && x <= k.r.right + slop
                        && y >= k.r.top - slop && y <= k.r.bottom + slop) return k;
            }
        return null;
    }

    private void press(Key k, float x, float y) {
        handler.removeCallbacks(repeatDelete);
        pressed = k;
        repeated = false;
        if (k != null) {
            rx = x; ry = y;
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            anim.cancel();
            anim.start();
            if (k.label.equals("⌫")) handler.postDelayed(repeatDelete, 400);
        }
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                press(findKey(e.getX(), e.getY()), e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_MOVE: {
                Key k = findKey(e.getX(), e.getY());
                if (k != pressed) press(k, e.getX(), e.getY());
                return true;
            }
            case MotionEvent.ACTION_UP: {
                Key k = pressed;
                boolean wasRepeating = repeated;
                handler.removeCallbacks(repeatDelete);
                pressed = null;
                invalidate();
                if (k != null && !wasRepeating) fire(k);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(repeatDelete);
                pressed = null;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void fire(Key k) {
        switch (k.label) {
            case "⇧": {
                long now = System.currentTimeMillis();
                if (caps) { caps = false; shift = false; }
                else if (shift && now - lastShiftTap < 350) caps = true;   // double-tap = caps lock
                else shift = !shift;
                lastShiftTap = now;
                invalidate();
                break;
            }
            case "⌫": if (listener != null) listener.onDelete(); break;
            case "⏎": if (listener != null) listener.onEnter(); break;
            case "?123": symbols = true;  shift = caps = false; buildRows(SYMBOLS); break;
            case "ABC":  symbols = false; buildRows(LETTERS); break;
            case "🎨":
                themeIdx = (themeIdx + 1) % THEMES.length;
                rebuildShader();
                invalidate();
                break;
            case "space": if (listener != null) listener.onText(" "); break;
            default: {
                String out = labelFor(k);
                if (shift && !caps) shift = false;      // unshift first so the service can re-evaluate
                if (listener != null) listener.onText(out);
                invalidate();
            }
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        anim.cancel();
        handler.removeCallbacksAndMessages(null);
    }
}
