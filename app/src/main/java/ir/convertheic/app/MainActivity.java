package ir.convertheic.app;

import com.bumptech.glide.integration.heif.HeifBitmapFactory;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {

    private static final int REQ_PICK_IMAGES = 100;
    private static final int REQ_SAVE_ZIP = 101;
    private static final int REQ_PICK_FOLDER = 102;

    private static final int TARGET_BYTES = 480 * 1024;
    private static final int MIN_SAFE_QUALITY = 90;

    private final ArrayList<Uri> selectedUris = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView selectedText;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button pickButton;
    private Button zipButton;
    private Button folderButton;
    private RadioButton maxQualityRadio;
    private RadioButton target480Radio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(246, 247, 251));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 247, 251));
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(26));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("HEIC  →  JPG");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("تبدیل سریع و آفلاین • چند عکس با هم • خروجی ZIP");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(107, 114, 128));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        subtitleParams.bottomMargin = dp(20);
        root.addView(subtitle, subtitleParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(Color.WHITE, 22));
        card.setElevation(dp(2));
        root.addView(card, matchWrap());

        card.addView(label("۱) انتخاب عکس‌های HEIC / HEIF"), matchWrap());

        pickButton = primaryButton("انتخاب عکس‌ها");
        LinearLayout.LayoutParams pickParams = matchWrap();
        pickParams.topMargin = dp(12);
        card.addView(pickButton, pickParams);
        pickButton.setOnClickListener(v -> openPicker());

        selectedText = normalText("هنوز عکسی انتخاب نشده است.");
        selectedText.setPadding(0, dp(12), 0, 0);
        card.addView(selectedText, matchWrap());

        TextView modeLabel = label("۲) کیفیت خروجی");
        LinearLayout.LayoutParams modeLabelParams = matchWrap();
        modeLabelParams.topMargin = dp(20);
        card.addView(modeLabel, modeLabelParams);

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        group.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams groupParams = matchWrap();
        groupParams.topMargin = dp(6);
        card.addView(group, groupParams);

        maxQualityRadio = radio("کیفیت حداکثری — رزولوشن اصلی، JPEG 100");
        maxQualityRadio.setChecked(true);
        group.addView(maxQualityRadio, matchWrap());

        target480Radio = radio("هدف حدود ۴۸۰KB — رزولوشن اصلی، اولویت با کیفیت");
        group.addView(target480Radio, matchWrap());

        TextView note = normalText("در حالت ۴۸۰KB ابعاد عکس کم نمی‌شود. اگر رسیدن به ۴۸۰KB نیازمند افت زیاد کیفیت باشد، برنامه کیفیت را حفظ می‌کند و فایل می‌تواند کمی بزرگ‌تر بماند.");
        note.setTextSize(12.5f);
        note.setLineSpacing(0, 1.25f);
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note, matchWrap());

        TextView outputLabel = label("۳) نوع خروجی");
        LinearLayout.LayoutParams outputLabelParams = matchWrap();
        outputLabelParams.topMargin = dp(20);
        card.addView(outputLabel, outputLabelParams);

        zipButton = primaryButton("ساخت یک فایل ZIP از همه عکس‌ها");
        LinearLayout.LayoutParams zipParams = matchWrap();
        zipParams.topMargin = dp(12);
        card.addView(zipButton, zipParams);
        zipButton.setOnClickListener(v -> chooseZipDestination());

        folderButton = secondaryButton("ذخیره JPGها در یک پوشه");
        LinearLayout.LayoutParams folderParams = matchWrap();
        folderParams.topMargin = dp(10);
        card.addView(folderButton, folderParams);
        folderButton.setOnClickListener(v -> chooseFolder());

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.rgb(79, 70, 229)));
        LinearLayout.LayoutParams progressParams = matchWrap();
        progressParams.topMargin = dp(18);
        card.addView(progressBar, progressParams);

        statusText = normalText("آماده");
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(8);
        card.addView(statusText, statusParams);

        TextView privacy = normalText("🔒 تمام پردازش روی گوشی انجام می‌شود و عکس‌ها به اینترنت ارسال نمی‌شوند.");
        privacy.setTextSize(12.5f);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyParams = matchWrap();
        privacyParams.topMargin = dp(16);
        root.addView(privacy, privacyParams);

        return scroll;
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence", "image/*"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_IMAGES);
    }

    private void chooseZipDestination() {
        if (!hasSelection()) return;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "HEIC_to_JPG_" + timestamp() + ".zip");
        startActivityForResult(intent, REQ_SAVE_ZIP);
    }

    private void chooseFolder() {
        if (!hasSelection()) return;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_FOLDER);
    }

    private boolean hasSelection() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "ابتدا حداقل یک عکس انتخاب کنید.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_PICK_IMAGES) {
            receiveSelection(data);
            return;
        }

        if (requestCode == REQ_SAVE_ZIP) {
            Uri destination = data.getData();
            if (destination != null) convertToZip(destination);
            return;
        }

        if (requestCode == REQ_PICK_FOLDER) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                convertToFolder(treeUri);
            }
        }
    }

    private void receiveSelection(Intent data) {
        selectedUris.clear();

        if (data.getData() != null) {
            addUri(data.getData(), data.getFlags());
        }

        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                addUri(clip.getItemAt(i).getUri(), data.getFlags());
            }
        }

        if (selectedUris.isEmpty()) {
            selectedText.setText("عکسی انتخاب نشد.");
        } else {
            selectedText.setText("تعداد عکس انتخاب‌شده: " + selectedUris.size());
        }
    }

    private void addUri(Uri uri, int flags) {
        if (uri == null || selectedUris.contains(uri)) return;
        selectedUris.add(uri);

        try {
            int takeFlags = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ignored) {
        }
    }

    private void convertToZip(Uri outputUri) {
        final List<Uri> inputs = new ArrayList<>(selectedUris);
        final boolean targetMode = target480Radio.isChecked();
        setBusy(true, "در حال تبدیل و ساخت ZIP…");

        executor.execute(() -> {
            int success = 0;
            int failed = 0;
            int qualityProtected = 0;

            try (OutputStream raw = getContentResolver().openOutputStream(outputUri, "w")) {
                if (raw == null) throw new IOException("فایل خروجی باز نشد");

                try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                    for (int i = 0; i < inputs.size(); i++) {
                        updateProgress(i, inputs.size(), "در حال تبدیل " + (i + 1) + " از " + inputs.size());
                        Uri input = inputs.get(i);

                        try {
                            EncodedJpeg jpeg = convertOne(input, targetMode);
                            String name = String.format(Locale.US, "%03d_%s", i + 1, jpgName(input));

                            ZipEntry entry = new ZipEntry(name);
                            zip.putNextEntry(entry);
                            zip.write(jpeg.bytes);
                            zip.closeEntry();

                            success++;
                            if (targetMode && jpeg.bytes.length > TARGET_BYTES) qualityProtected++;
                        } catch (Exception ignored) {
                            failed++;
                        }
                    }
                }
            } catch (Exception e) {
                finishBusy("خطا در ساخت ZIP: " + safeMessage(e), true);
                return;
            }

            String message = "ZIP آماده شد • موفق: " + success + " • ناموفق: " + failed;
            if (qualityProtected > 0) {
                message += " • " + qualityProtected + " فایل برای حفظ کیفیت بالاتر از ۴۸۰KB ماند";
            }
            finishBusy(message, false);
        });
    }

    private void convertToFolder(Uri treeUri) {
        final List<Uri> inputs = new ArrayList<>(selectedUris);
        final boolean targetMode = target480Radio.isChecked();
        setBusy(true, "در حال تبدیل و ذخیره JPGها…");

        executor.execute(() -> {
            int success = 0;
            int failed = 0;
            int qualityProtected = 0;

            ContentResolver resolver = getContentResolver();
            Uri parent;
            try {
                String treeId = DocumentsContract.getTreeDocumentId(treeUri);
                parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId);
            } catch (Exception e) {
                finishBusy("پوشه انتخاب‌شده قابل استفاده نیست.", true);
                return;
            }

            for (int i = 0; i < inputs.size(); i++) {
                updateProgress(i, inputs.size(), "در حال ذخیره " + (i + 1) + " از " + inputs.size());

                try {
                    Uri input = inputs.get(i);
                    EncodedJpeg jpeg = convertOne(input, targetMode);
                    String name = String.format(Locale.US, "%03d_%s", i + 1, jpgName(input));

                    Uri output = DocumentsContract.createDocument(resolver, parent, "image/jpeg", name);
                    if (output == null) throw new IOException("فایل JPG ساخته نشد");

                    try (OutputStream out = resolver.openOutputStream(output, "w")) {
                        if (out == null) throw new IOException("فایل JPG باز نشد");
                        out.write(jpeg.bytes);
                        out.flush();
                    }

                    success++;
                    if (targetMode && jpeg.bytes.length > TARGET_BYTES) qualityProtected++;
                } catch (Exception ignored) {
                    failed++;
                }
            }

            String message = "ذخیره انجام شد • موفق: " + success + " • ناموفق: " + failed;
            if (qualityProtected > 0) {
                message += " • " + qualityProtected + " فایل برای حفظ کیفیت بالاتر از ۴۸۰KB ماند";
            }
            finishBusy(message, false);
        });
    }

    private EncodedJpeg convertOne(Uri uri, boolean targetMode) throws IOException {
        Bitmap bitmap = decodeBitmap(uri);

        try {
            if (!targetMode) {
                return new EncodedJpeg(compress(bitmap, 100), 100);
            }

            byte[] q100 = compress(bitmap, 100);
            if (q100.length <= TARGET_BYTES) {
                return new EncodedJpeg(q100, 100);
            }

            byte[] safe = compress(bitmap, MIN_SAFE_QUALITY);
            if (safe.length > TARGET_BYTES) {
                return new EncodedJpeg(safe, MIN_SAFE_QUALITY);
            }

            int low = MIN_SAFE_QUALITY;
            int high = 99;
            int bestQuality = MIN_SAFE_QUALITY;
            byte[] best = safe;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                byte[] candidate = compress(bitmap, mid);

                if (candidate.length <= TARGET_BYTES) {
                    best = candidate;
                    bestQuality = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            return new EncodedJpeg(best, bestQuality);
        } finally {
            bitmap.recycle();
        }
    }

    private Bitmap decodeBitmap(Uri uri) throws IOException {
        Throwable lastError = null;

        // مسیر اول: دیکودر استاندارد اندروید
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap != null) return bitmap;
            }
        } catch (Throwable e) {
            lastError = e;
        }

        // مسیر دوم: ImageDecoder در اندروید 9 به بالا
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
                });
                if (bitmap != null) return bitmap;
            } catch (Throwable e) {
                lastError = e;
            }
        }

        // مسیر سوم و مستقل: libheif؛ برای HEICهایی که دیکودر خود گوشی پشتیبانی نمی‌کند.
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                Bitmap bitmap = HeifBitmapFactory.decodeStream(in);
                if (bitmap != null) return bitmap;
            }
        } catch (Throwable e) {
            lastError = e;
        }

        String detail = lastError == null ? "" : " (" + lastError.getClass().getSimpleName() + ")";
        throw new IOException("فایل HEIC/HEIF قابل تبدیل نبود" + detail);
    }

    private byte[] compress(Bitmap bitmap, int quality) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            throw new IOException("تبدیل JPEG ناموفق بود");
        }

        byte[] bytes = out.toByteArray();
        if (!isRealJpeg(bytes)) {
            throw new IOException("خروجی ساخته‌شده JPEG معتبر نیست");
        }
        return bytes;
    }

    private boolean isRealJpeg(byte[] bytes) {
        return bytes != null
                && bytes.length >= 4
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[bytes.length - 2] & 0xFF) == 0xFF
                && (bytes[bytes.length - 1] & 0xFF) == 0xD9;
    }

    private String jpgName(Uri uri) {
        String original = displayName(uri);
        String clean = original.replace('\\', '_').replace('/', '_').replace(':', '_');
        int dot = clean.lastIndexOf('.');
        if (dot > 0) clean = clean.substring(0, dot);
        if (clean.trim().isEmpty()) clean = "image";
        return clean + ".jpg";
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value;
                }
            }
        } catch (Exception ignored) {
        }
        return "image.heic";
    }

    private void updateProgress(int done, int total, String text) {
        int percent = total == 0 ? 0 : Math.round(done * 100f / total);
        runOnUiThread(() -> {
            progressBar.setProgress(percent);
            statusText.setText(text);
        });
    }

    private void setBusy(boolean busy, String text) {
        pickButton.setEnabled(!busy);
        zipButton.setEnabled(!busy);
        folderButton.setEnabled(!busy);
        maxQualityRadio.setEnabled(!busy);
        target480Radio.setEnabled(!busy);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        progressBar.setProgress(0);
        statusText.setText(text);
    }

    private void finishBusy(String message, boolean error) {
        runOnUiThread(() -> {
            setBusy(false, message);
            progressBar.setProgress(error ? 0 : 100);
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(Color.rgb(17, 24, 39));
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.START);
        return view;
    }

    private TextView normalText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(75, 85, 99));
        view.setGravity(Gravity.START);
        return view;
    }

    private RadioButton radio(String text) {
        RadioButton button = new RadioButton(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(Color.rgb(31, 41, 55));
        button.setButtonTintList(ColorStateList.valueOf(Color.rgb(79, 70, 229)));
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(0, dp(4), 0, dp(4));
        return button;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundRect(Color.rgb(79, 70, 229), 16));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.rgb(55, 48, 163));
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundRect(Color.rgb(238, 242, 255), 16));
        return button;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class EncodedJpeg {
        final byte[] bytes;
        final int quality;

        EncodedJpeg(byte[] bytes, int quality) {
            this.bytes = bytes;
            this.quality = quality;
        }
    }
}
