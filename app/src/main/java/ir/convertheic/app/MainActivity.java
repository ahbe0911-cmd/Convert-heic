package ir.convertheic.app;

import com.bumptech.glide.integration.heif.HeifBitmapFactory;
import androidx.core.content.FileProvider;

import android.app.Activity;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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

    private static final int MAX_OUTPUT_BYTES = 490 * 1024;
        private static final int MIN_TARGET_QUALITY = 84;
    private static final int LAST_RESORT_QUALITY = 78;

    private static final int BG = Color.rgb(247, 248, 253);
    private static final int TEXT = Color.rgb(20, 28, 54);
    private static final int MUTED = Color.rgb(119, 126, 150);
    private static final int BLUE = Color.rgb(39, 118, 255);
    private static final int PURPLE = Color.rgb(112, 42, 245);
    private static final int GREEN = Color.rgb(26, 188, 128);
    private static final int SOFT = Color.rgb(244, 246, 252);

    private final ArrayList<Uri> selectedUris = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2);
    private final ArrayList<TextView> progressStatuses = new ArrayList<>();

    private static final int OUTPUT_BALE = 0;
    private static final int OUTPUT_ZIP = 1;
    private static final int OUTPUT_FOLDER = 2;

    private boolean target490Mode = false;
    private int outputMode = OUTPUT_BALE;
    private int lastResultMode = OUTPUT_BALE;
    private volatile boolean cancelRequested = false;
    private int screenMode = 0; // 0 home, 1 progress, 2 success
    private Typeface vazir;

    private LinearLayout selectedThumbs;
    private TextView selectedSummary;
    private LinearLayout qualityMaxCard;
    private LinearLayout quality490Card;
    private LinearLayout outputBaleCard;
    private LinearLayout outputZipCard;
    private LinearLayout outputFolderCard;
    private TextView convertActionButton;

    private RingProgress ringProgress;

    private Uri lastOutputUri;
    private boolean lastWasZip;
    private String lastOutputName = "";
    private long lastOutputBytes = 0L;
    private final ArrayList<Uri> lastShareUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        try {
            vazir = getResources().getFont(R.font.vazirmatn);
        } catch (Throwable ignored) {
            vazir = Typeface.DEFAULT;
        }
        showHome(false);
    }

    private void showHome(boolean animate) {
        screenMode = 0;
        cancelRequested = false;
        View view = buildHome();
        showScreen(view, false);
        refreshSelection();
        refreshOptionCards();
        animateHome(view);
    }

    private View buildHome() {
        final boolean compact = getResources().getConfiguration().screenHeightDp < 700;

        LinearLayout root = baseRoot();
        root.setPadding(dp(12), dp(compact ? 7 : 10), dp(12), dp(compact ? 7 : 10));
        root.setBackgroundColor(BG);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 82 : 90));
        root.addView(buildBrandHeader(), headerParams);

        LinearLayout pickCard = whiteCard();
        pickCard.setPadding(dp(9), dp(9), dp(9), dp(7));
        LinearLayout.LayoutParams pickCardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 118 : 128));
        pickCardParams.topMargin = dp(7);
        root.addView(pickCard, pickCardParams);

        LinearLayout selector = new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        selector.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable selectorBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(248, 251, 255), Color.rgb(247, 242, 255)});
        selectorBg.setCornerRadius(dp(18));
        selectorBg.setStroke(dp(2), Color.rgb(179, 190, 255), dp(7), dp(5));
        selector.setBackground(selectorBg);
        selector.setOnClickListener(v -> {
            pulse(selector);
            openPicker();
        });
        pickCard.addView(selector, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 61 : 66)));

        TextView add = new TextView(this);
        add.setText("＋");
        add.setTextColor(Color.WHITE);
        add.setTextSize(compact ? 22 : 25);
        add.setGravity(Gravity.CENTER);
        add.setTypeface(null, Typeface.BOLD);
        add.setBackground(gradient(BLUE, PURPLE, 15));
        LinearLayout.LayoutParams addP = new LinearLayout.LayoutParams(dp(44), dp(44));
        addP.leftMargin = dp(10);
        selector.addView(add, addP);

        LinearLayout selectText = new LinearLayout(this);
        selectText.setOrientation(LinearLayout.VERTICAL);
        selectText.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        selector.addView(selectText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView selectTitle = new TextView(this);
        selectTitle.setText("انتخاب HEIC / HEIF");
        selectTitle.setTextColor(TEXT);
        selectTitle.setTextSize(compact ? 13.3f : 14.3f);
        selectTitle.setGravity(Gravity.END);
        selectTitle.setTypeface(null, Typeface.BOLD);
        selectText.addView(selectTitle, fullWidth());

        TextView selectSub = new TextView(this);
        selectSub.setText("چند عکس را همزمان انتخاب کنید");
        selectSub.setTextColor(MUTED);
        selectSub.setTextSize(compact ? 9.8f : 10.8f);
        selectSub.setGravity(Gravity.END);
        selectText.addView(selectSub, fullWidth());

        LinearLayout selectedRow = new LinearLayout(this);
        selectedRow.setOrientation(LinearLayout.HORIZONTAL);
        selectedRow.setGravity(Gravity.CENTER_VERTICAL);
        selectedRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        selectedRow.setPadding(dp(3), dp(4), dp(3), 0);
        pickCard.addView(selectedRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 42 : 46)));

        selectedThumbs = new LinearLayout(this);
        selectedThumbs.setOrientation(LinearLayout.HORIZONTAL);
        selectedThumbs.setGravity(Gravity.CENTER_VERTICAL);
        selectedRow.addView(selectedThumbs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        selectedSummary = new TextView(this);
        selectedSummary.setTextColor(TEXT);
        selectedSummary.setTextSize(compact ? 10.5f : 11.5f);
        selectedSummary.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        selectedSummary.setTypeface(null, Typeface.BOLD);
        selectedSummary.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        selectedRow.addView(selectedSummary, new LinearLayout.LayoutParams(dp(compact ? 118 : 132), ViewGroup.LayoutParams.MATCH_PARENT));

        addSectionTitle(root, "کیفیت خروجی");
        LinearLayout qualityRow = optionRow();
        root.addView(qualityRow, fullWidth());
        quality490Card = optionCard("۴۹۰", "Smart 490", "سقف ۴۹۰KB • کیفیت بالا", GREEN, false,
                v -> { target490Mode = true; refreshOptionCards(); pulse(quality490Card); });
        qualityMaxCard = optionCard("HQ", "کیفیت حداکثری", "رزولوشن اصلی", PURPLE, true,
                v -> { target490Mode = false; refreshOptionCards(); pulse(qualityMaxCard); });
        qualityRow.addView(quality490Card, weightedCard(true));
        qualityRow.addView(qualityMaxCard, weightedCard(false));

        addSectionTitle(root, "ارسال و ذخیره");
        LinearLayout outputRow = optionRow();
        root.addView(outputRow, fullWidth());

        outputBaleCard = optionCard("بله", "اشتراک بله", "پیش‌فرض", Color.rgb(23, 160, 210), true,
                v -> { outputMode = OUTPUT_BALE; refreshOptionCards(); pulse(outputBaleCard); });
        outputZipCard = optionCard("ZIP", "فایل ZIP", "همه در یک فایل", BLUE, false,
                v -> { outputMode = OUTPUT_ZIP; refreshOptionCards(); pulse(outputZipCard); });
        outputFolderCard = optionCard("▰", "پوشه", "JPG جداگانه", Color.rgb(255, 157, 55), false,
                v -> { outputMode = OUTPUT_FOLDER; refreshOptionCards(); pulse(outputFolderCard); });

        outputRow.addView(outputBaleCard, tripleCard(0));
        outputRow.addView(outputZipCard, tripleCard(1));
        outputRow.addView(outputFolderCard, tripleCard(2));

        LinearLayout smartInfo = new LinearLayout(this);
        smartInfo.setOrientation(LinearLayout.HORIZONTAL);
        smartInfo.setGravity(Gravity.CENTER_VERTICAL);
        smartInfo.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        smartInfo.setPadding(dp(12), dp(8), dp(12), dp(8));
        smartInfo.setBackground(gradient(Color.rgb(239, 248, 255), Color.rgb(246, 241, 255), 18));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        infoLp.topMargin = dp(8);
        infoLp.bottomMargin = dp(8);
        root.addView(smartInfo, infoLp);

        TextView infoBadge = new TextView(this);
        infoBadge.setText("SMART");
        infoBadge.setTextColor(Color.WHITE);
        infoBadge.setTextSize(9.5f);
        infoBadge.setTypeface(null, Typeface.BOLD);
        infoBadge.setGravity(Gravity.CENTER);
        infoBadge.setBackground(gradient(GREEN, BLUE, 12));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(58), dp(34));
        badgeLp.leftMargin = dp(10);
        smartInfo.addView(infoBadge, badgeLp);

        TextView info = new TextView(this);
        info.setText("فشرده‌سازی هوشمند: ابتدا کیفیت JPEG بهینه می‌شود؛ فقط در صورت نیاز ابعاد به‌صورت تدریجی کاهش می‌یابد.");
        info.setTextColor(Color.rgb(63, 72, 103));
        info.setTextSize(compact ? 9.4f : 10.3f);
        info.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        info.setMaxLines(3);
        smartInfo.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        convertActionButton = primaryAction("تبدیل و ارسال مستقیم به بله   ←");
        LinearLayout.LayoutParams convertParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 50 : 54));
        root.addView(convertActionButton, convertParams);
        convertActionButton.setOnClickListener(v -> {
            pulse(convertActionButton);
            startConversionFlow();
        });

        TextView privacy = smallText("🔒 آفلاین • بدون آپلود • فایل اصلی دست‌نخورده می‌ماند");
        privacy.setGravity(Gravity.CENTER);
        privacy.setTextSize(compact ? 9.1f : 10f);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 22 : 25));
        privacyParams.topMargin = dp(3);
        root.addView(privacy, privacyParams);

        return root;
    }

    private View buildBrandHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(13), dp(7), dp(13), dp(7));
        header.setBackground(gradient(BLUE, PURPLE, 24));
        header.setElevation(dp(6));
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        header.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView brand = new TextView(this);
        brand.setText("ZipPix");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(24);
        brand.setGravity(Gravity.END);
        brand.setTypeface(null, Typeface.BOLD);
        textBox.addView(brand, fullWidth());

        TextView subtitle = new TextView(this);
        subtitle.setText("HEIC → JPG  •  تبدیل هوشمند و آفلاین");
        subtitle.setTextColor(Color.argb(225, 255, 255, 255));
        subtitle.setTextSize(11);
        subtitle.setGravity(Gravity.END);
        subtitle.setSingleLine(true);
        LinearLayout.LayoutParams sp = fullWidth();
        sp.topMargin = dp(1);
        textBox.addView(subtitle, sp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.zippix_icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(roundRect(Color.WHITE, 17));
        icon.setClipToOutline(true);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(58), dp(58));
        ip.leftMargin = dp(12);
        header.addView(icon, ip);

        return header;
    }

    private void addSectionTitle(LinearLayout root, String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(15.5f);
        t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.END);
        LinearLayout.LayoutParams p = fullWidth();
        p.topMargin = dp(7);
        p.bottomMargin = dp(4);
        root.addView(t, p);
    }

    private LinearLayout optionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        return row;
    }

    private LinearLayout.LayoutParams weightedCard(boolean left) {
        boolean compact = getResources().getConfiguration().screenHeightDp < 700;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(compact ? 78 : 86), 1f);
        if (left) p.rightMargin = dp(5); else p.leftMargin = dp(5);
        return p;
    }

    private LinearLayout.LayoutParams tripleCard(int index) {
        boolean compact = getResources().getConfiguration().screenHeightDp < 700;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(compact ? 77 : 84), 1f);
        if (index == 0) p.rightMargin = dp(4);
        if (index == 1) { p.leftMargin = dp(2); p.rightMargin = dp(2); }
        if (index == 2) p.leftMargin = dp(4);
        return p;
    }

    private LinearLayout optionCard(String icon, String title, String sub, int tint, boolean selected, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(5), dp(4), dp(4));
        box.setOnClickListener(click);

        TextView i = new TextView(this);
        i.setText(icon);
        i.setTextSize(icon.length() > 2 ? 10.5f : 16.5f);
        i.setGravity(Gravity.CENTER);
        i.setTextColor(Color.WHITE);
        i.setTypeface(null, Typeface.BOLD);
        i.setBackground(roundRect(tint, 13));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(30));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(i, ip);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(11.6f);
        t.setTextColor(TEXT);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = fullWidth();
        tp.topMargin = dp(4);
        box.addView(t, tp);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(8.9f);
        s.setTextColor(MUTED);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = fullWidth();
        sp.topMargin = dp(1);
        box.addView(s, sp);

        box.setTag(new OptionStyle(i, tint));
        applyOptionStyle(box, selected);
        return box;
    }

    private void refreshOptionCards() {
        if (qualityMaxCard == null) return;
        applyOptionStyle(qualityMaxCard, !target490Mode);
        applyOptionStyle(quality490Card, target490Mode);
        applyOptionStyle(outputBaleCard, outputMode == OUTPUT_BALE);
        applyOptionStyle(outputZipCard, outputMode == OUTPUT_ZIP);
        applyOptionStyle(outputFolderCard, outputMode == OUTPUT_FOLDER);

        if (convertActionButton != null) {
            if (outputMode == OUTPUT_BALE) {
                convertActionButton.setText("تبدیل و ارسال مستقیم به بله   ←");
            } else if (outputMode == OUTPUT_ZIP) {
                convertActionButton.setText("تبدیل و ساخت ZIP   ←");
            } else {
                convertActionButton.setText("تبدیل و ذخیره در پوشه   ←");
            }
        }
    }

    private void applyOptionStyle(LinearLayout card, boolean selected) {
        Object tag = card.getTag();
        int tint = PURPLE;
        if (tag instanceof OptionStyle) tint = ((OptionStyle) tag).tint;
        card.setBackground(optionBackground(selected, tint));
        card.setElevation(selected ? dp(4) : dp(1));
        card.setScaleX(selected ? 1.0f : 0.985f);
        card.setScaleY(selected ? 1.0f : 0.985f);
    }

    private void refreshSelection() {
        if (selectedSummary == null || selectedThumbs == null) return;
        selectedThumbs.removeAllViews();

        if (selectedUris.isEmpty()) {
            selectedSummary.setText("هنوز فایلی انتخاب نشده");
            TextView empty = new TextView(this);
            empty.setText("＋");
            empty.setTextSize(20);
            empty.setTextColor(Color.rgb(170, 175, 194));
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(roundRect(Color.rgb(239, 242, 251), 14));
            selectedThumbs.addView(empty, new LinearLayout.LayoutParams(dp(40), dp(40)));
            return;
        }

        long total = 0;
        for (Uri uri : selectedUris) total += querySize(uri);
        selectedSummary.setText(toPersianDigits(String.valueOf(selectedUris.size())) + " فایل انتخاب شده\n" + formatBytes(total));

        int previewCount = Math.min(4, selectedUris.size());
        for (int i = 0; i < previewCount; i++) {
            Uri uri = selectedUris.get(i);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(roundRect(Color.rgb(231, 235, 248), 13));
            iv.setClipToOutline(true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(40), dp(40));
            p.rightMargin = dp(5);
            selectedThumbs.addView(iv, p);
            thumbnailExecutor.execute(() -> {
                Bitmap b = loadThumb(uri);
                if (b != null) runOnUiThread(() -> iv.setImageBitmap(b));
            });
        }

        if (selectedUris.size() > 4) {
            TextView more = new TextView(this);
            more.setText("+" + toPersianDigits(String.valueOf(selectedUris.size() - 4)));
            more.setTextColor(PURPLE);
            more.setTextSize(13);
            more.setGravity(Gravity.CENTER);
            more.setTypeface(null, Typeface.BOLD);
            more.setBackground(roundRect(Color.rgb(239, 235, 255), 13));
            selectedThumbs.addView(more, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
    }

    private Bitmap loadThumb(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return getContentResolver().loadThumbnail(uri, new Size(160, 160), null);
            }
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 8;
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void startConversionFlow() {
        if (selectedUris.isEmpty()) {
            toast("ابتدا حداقل یک عکس HEIC/HEIF انتخاب کنید.");
            return;
        }

        if (outputMode == OUTPUT_BALE) {
            startBaleConversion();
        } else if (outputMode == OUTPUT_ZIP) {
            chooseZipDestination();
        } else {
            chooseFolder();
        }
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
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "ZipPix_" + timestamp() + ".zip");
        startActivityForResult(intent, REQ_SAVE_ZIP);
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_PICK_IMAGES) {
            receiveSelection(data);
            refreshSelection();
            return;
        }

        if (requestCode == REQ_SAVE_ZIP) {
            Uri destination = data.getData();
            if (destination != null) startZipConversion(destination);
            return;
        }

        if (requestCode == REQ_PICK_FOLDER) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {}
                startFolderConversion(treeUri);
            }
        }
    }

    private void receiveSelection(Intent data) {
        selectedUris.clear();
        if (data.getData() != null) addUri(data.getData(), data.getFlags());
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) addUri(clip.getItemAt(i).getUri(), data.getFlags());
        }
    }

    private void addUri(Uri uri, int flags) {
        if (uri == null || selectedUris.contains(uri)) return;
        selectedUris.add(uri);
        try {
            int takeFlags = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ignored) {}
    }

    private void startBaleConversion() {
        lastResultMode = OUTPUT_BALE;
        lastOutputUri = null;
        lastOutputName = "اشتراک مستقیم در بله";
        lastOutputBytes = 0L;
        lastShareUris.clear();
        cancelRequested = false;
        showProgressScreen();

        final List<Uri> inputs = new ArrayList<>(selectedUris);
        final boolean targetMode = target490Mode;

        executor.execute(() -> {
            int success = 0;
            int failed = 0;
            long bytes = 0L;

            File shareDir = new File(getCacheDir(), "share");
            deleteRecursively(shareDir);
            if (!shareDir.exists() && !shareDir.mkdirs()) {
                runOnUiThread(() -> {
                    toast("امکان آماده‌سازی پوشه اشتراک وجود ندارد.");
                    showHome(true);
                });
                return;
            }

            ArrayList<Uri> shareUris = new ArrayList<>();

            for (int i = 0; i < inputs.size(); i++) {
                if (cancelRequested) {
                    runOnUiThread(() -> {
                        toast("تبدیل لغو شد.");
                        showHome(true);
                    });
                    return;
                }

                final int index = i;
                setProgressState(index, "در حال تبدیل…", BLUE);
                try {
                    EncodedJpeg jpeg = convertOne(inputs.get(i), targetMode);
                    String safeName = String.format(Locale.US, "%03d_%s", i + 1, jpgName(inputs.get(i)));
                    File outFile = new File(shareDir, safeName);
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        out.write(jpeg.bytes);
                        out.flush();
                    }

                    Uri contentUri = FileProvider.getUriForFile(
                            this, getPackageName() + ".fileprovider", outFile);
                    shareUris.add(contentUri);
                    bytes += jpeg.bytes.length;
                    success++;
                    setProgressState(index, "آماده ارسال ✓", GREEN);
                } catch (Exception e) {
                    failed++;
                    setProgressState(index, "خطا", Color.rgb(225, 72, 93));
                }
                updateRing(i + 1, inputs.size());
            }

            lastShareUris.clear();
            lastShareUris.addAll(shareUris);
            lastOutputBytes = bytes;

            final int ok = success;
            final int bad = failed;
            runOnUiThread(() -> {
                showSuccess(ok, bad);
                if (!lastShareUris.isEmpty()) {
                    shareToBale(new ArrayList<>(lastShareUris));
                }
            });
        });
    }

    private void shareToBale(ArrayList<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            toast("فایلی برای اشتراک آماده نیست.");
            return;
        }

        Intent intent = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        intent.setType("image/jpeg");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (uris.size() == 1) {
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }

        ClipData clip = ClipData.newUri(getContentResolver(), "ZipPix", uris.get(0));
        for (int i = 1; i < uris.size(); i++) {
            clip.addItem(new ClipData.Item(uris.get(i)));
        }
        intent.setClipData(clip);

        try {
            intent.setPackage("ir.nasim");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
        } catch (Exception ignored) {}

        try {
            intent.setPackage(null);
            startActivity(Intent.createChooser(intent, "اشتراک‌گذاری تصاویر"));
            toast("پیام‌رسان بله پیدا نشد؛ فهرست اشتراک باز شد.");
        } catch (Exception e) {
            toast("اشتراک‌گذاری ممکن نشد.");
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    private void startZipConversion(Uri outputUri) {
        lastResultMode = OUTPUT_ZIP;
        lastOutputUri = outputUri;
        lastWasZip = true;
        lastOutputName = displayName(outputUri);
        if (lastOutputName == null || !lastOutputName.toLowerCase(Locale.US).endsWith(".zip")) {
            lastOutputName = "ZipPix_" + timestamp() + ".zip";
        }
        cancelRequested = false;
        showProgressScreen();

        final List<Uri> inputs = new ArrayList<>(selectedUris);
        final boolean targetMode = target490Mode;

        executor.execute(() -> {
            int success = 0;
            int failed = 0;
            boolean canceled = false;

            try (OutputStream raw = getContentResolver().openOutputStream(outputUri, "w")) {
                if (raw == null) throw new IOException("فایل ZIP باز نشد");

                try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                    for (int i = 0; i < inputs.size(); i++) {
                        if (cancelRequested) { canceled = true; break; }
                        setProgressState(i, "در حال تبدیل…", BLUE);
                        try {
                            EncodedJpeg jpeg = convertOne(inputs.get(i), targetMode);
                            ZipEntry entry = new ZipEntry(String.format(Locale.US, "%03d_%s", i + 1, jpgName(inputs.get(i))));
                            zip.putNextEntry(entry);
                            zip.write(jpeg.bytes);
                            zip.closeEntry();
                            success++;
                            setProgressState(i, "تبدیل شد ✓", GREEN);
                        } catch (Exception e) {
                            failed++;
                            setProgressState(i, "خطا", Color.rgb(225, 72, 93));
                        }
                        updateRing(i + 1, inputs.size());
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    toast("خطا در ساخت ZIP: " + safeMessage(e));
                    showHome(true);
                });
                return;
            }

            if (canceled) {
                runOnUiThread(() -> {
                    toast("تبدیل لغو شد.");
                    showHome(true);
                });
                return;
            }

            lastOutputBytes = querySize(outputUri);
            final int ok = success;
            final int bad = failed;
            runOnUiThread(() -> showSuccess(ok, bad));
        });
    }

    private void startFolderConversion(Uri treeUri) {
        lastResultMode = OUTPUT_FOLDER;
        lastOutputUri = treeUri;
        lastWasZip = false;
        lastOutputName = "پوشه خروجی ZipPix";
        lastOutputBytes = 0;
        cancelRequested = false;
        showProgressScreen();

        final List<Uri> inputs = new ArrayList<>(selectedUris);
        final boolean targetMode = target490Mode;

        executor.execute(() -> {
            int success = 0;
            int failed = 0;
            long bytes = 0;
            boolean canceled = false;
            ContentResolver resolver = getContentResolver();
            Uri parent;

            try {
                String treeId = DocumentsContract.getTreeDocumentId(treeUri);
                parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    toast("پوشه انتخاب‌شده قابل استفاده نیست.");
                    showHome(true);
                });
                return;
            }

            for (int i = 0; i < inputs.size(); i++) {
                if (cancelRequested) { canceled = true; break; }
                setProgressState(i, "در حال تبدیل…", BLUE);
                try {
                    EncodedJpeg jpeg = convertOne(inputs.get(i), targetMode);
                    String name = String.format(Locale.US, "%03d_%s", i + 1, jpgName(inputs.get(i)));
                    Uri output = DocumentsContract.createDocument(resolver, parent, "image/jpeg", name);
                    if (output == null) throw new IOException("فایل JPG ساخته نشد");
                    try (OutputStream out = resolver.openOutputStream(output, "w")) {
                        if (out == null) throw new IOException("خروجی باز نشد");
                        out.write(jpeg.bytes);
                        out.flush();
                    }
                    bytes += jpeg.bytes.length;
                    success++;
                    setProgressState(i, "تبدیل شد ✓", GREEN);
                } catch (Exception e) {
                    failed++;
                    setProgressState(i, "خطا", Color.rgb(225, 72, 93));
                }
                updateRing(i + 1, inputs.size());
            }

            if (canceled) {
                runOnUiThread(() -> {
                    toast("تبدیل لغو شد.");
                    showHome(true);
                });
                return;
            }

            lastOutputBytes = bytes;
            final int ok = success;
            final int bad = failed;
            runOnUiThread(() -> showSuccess(ok, bad));
        });
    }

    private void showProgressScreen() {
        screenMode = 1;
        progressStatuses.clear();

        ScrollView scroll = baseScroll();
        LinearLayout root = baseRoot();
        scroll.addView(root);

        TextView title = titleText("در حال تبدیل تصاویر");
        root.addView(title, fullWidth());

        ringProgress = new RingProgress(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(170), dp(170));
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.topMargin = dp(18);
        root.addView(ringProgress, rp);

        TextView state = titleText("در حال تبدیل…");
        state.setTextSize(20);
        state.setGravity(Gravity.CENTER);
        root.addView(state, fullWidth());

        TextView sub = smallText("در حال پردازش فایل‌های انتخاب‌شده\nلطفاً کمی صبر کنید");
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subp = fullWidth();
        subp.topMargin = dp(5);
        root.addView(sub, subp);

        LinearLayout list = whiteCard();
        LinearLayout.LayoutParams lp = fullWidth();
        lp.topMargin = dp(18);
        root.addView(list, lp);

        for (int i = 0; i < selectedUris.size(); i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

            TextView status = new TextView(this);
            status.setText("در انتظار…");
            status.setTextColor(MUTED);
            status.setTextSize(12);
            status.setGravity(Gravity.CENTER);
            status.setTypeface(null, Typeface.BOLD);
            row.addView(status, new LinearLayout.LayoutParams(dp(92), dp(42)));
            progressStatuses.add(status);

            TextView name = new TextView(this);
            name.setText(displayName(selectedUris.get(i)));
            name.setTextColor(TEXT);
            name.setTextSize(12.5f);
            name.setSingleLine(true);
            name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(42), 1f));

            TextView index = new TextView(this);
            index.setText(toPersianDigits(String.format(Locale.US, "%02d", i + 1)));
            index.setTextColor(Color.WHITE);
            index.setGravity(Gravity.CENTER);
            index.setTypeface(null, Typeface.BOLD);
            index.setBackground(gradient(BLUE, PURPLE, 12));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(42), dp(42));
            ip.leftMargin = dp(8);
            row.addView(index, ip);

            list.addView(row, fullWidth());
            if (i < selectedUris.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(237, 239, 247));
                list.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }

        TextView cancel = secondaryAction("لغو تبدیل");
        LinearLayout.LayoutParams cp = fullWidth();
        cp.topMargin = dp(15);
        cp.bottomMargin = dp(16);
        root.addView(cancel, cp);
        cancel.setOnClickListener(v -> {
            cancelRequested = true;
            cancel.setText("در حال لغو…");
            cancel.setEnabled(false);
        });

        showScreen(scroll, true);
    }

    private void setProgressState(int index, String text, int color) {
        runOnUiThread(() -> {
            if (index >= 0 && index < progressStatuses.size()) {
                TextView s = progressStatuses.get(index);
                s.setText(text);
                s.setTextColor(color);
            }
        });
    }

    private void updateRing(int done, int total) {
        int percent = total == 0 ? 0 : Math.round(done * 100f / total);
        runOnUiThread(() -> {
            if (ringProgress != null) ringProgress.setProgress(percent);
        });
    }

    private void showSuccess(int success, int failed) {
        screenMode = 2;

        ScrollView scroll = baseScroll();
        LinearLayout root = baseRoot();
        scroll.addView(root);

        TextView top = titleText("تبدیل با موفقیت انجام شد");
        root.addView(top, fullWidth());

        TextView check = new TextView(this);
        check.setText("✓");
        check.setTextSize(54);
        check.setTextColor(Color.WHITE);
        check.setGravity(Gravity.CENTER);
        check.setTypeface(null, Typeface.BOLD);
        check.setBackground(roundRect(GREEN, 52));
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(dp(104), dp(104));
        chp.gravity = Gravity.CENTER_HORIZONTAL;
        chp.topMargin = dp(24);
        root.addView(check, chp);

        TextView message = titleText("آماده شد!");
        message.setTextSize(23);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = fullWidth();
        mp.topMargin = dp(16);
        root.addView(message, mp);

        String resultTail;
        if (lastResultMode == OUTPUT_BALE) {
            resultTail = "\nآماده اشتراک مستقیم در بله";
        } else if (lastResultMode == OUTPUT_ZIP) {
            resultTail = "\nو در یک فایل ZIP قرار گرفت";
        } else {
            resultTail = "\nو در پوشه انتخاب‌شده ذخیره شد";
        }
        TextView detail = smallText(
                toPersianDigits(String.valueOf(success)) + " فایل با موفقیت به JPG تبدیل شد"
                        + (failed > 0 ? "\n" + toPersianDigits(String.valueOf(failed)) + " فایل با خطا مواجه شد" : "")
                        + resultTail);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dp1 = fullWidth();
        dp1.topMargin = dp(7);
        root.addView(detail, dp1);

        LinearLayout resultCard = whiteCard();
        resultCard.setOrientation(LinearLayout.HORIZONTAL);
        resultCard.setGravity(Gravity.CENTER_VERTICAL);
        resultCard.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams rcp = fullWidth();
        rcp.topMargin = dp(22);
        root.addView(resultCard, rcp);

        TextView fileIcon = new TextView(this);
        fileIcon.setText(lastResultMode == OUTPUT_BALE ? "بله" : (lastWasZip ? "ZIP" : "JPG"));
        fileIcon.setTextColor(Color.WHITE);
        fileIcon.setTextSize(12);
        fileIcon.setGravity(Gravity.CENTER);
        fileIcon.setTypeface(null, Typeface.BOLD);
        fileIcon.setBackground(roundRect(lastResultMode == OUTPUT_BALE ? Color.rgb(23, 160, 210) : (lastWasZip ? Color.rgb(235, 73, 91) : GREEN), 14));
        resultCard.addView(fileIcon, new LinearLayout.LayoutParams(dp(56), dp(62)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);
        resultCard.addView(info, new LinearLayout.LayoutParams(0, dp(70), 1f));

        TextView name = new TextView(this);
        name.setText(lastOutputName);
        name.setTextColor(TEXT);
        name.setTextSize(14);
        name.setTypeface(null, Typeface.BOLD);
        name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        info.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView size = smallText(formatBytes(lastOutputBytes));
        size.setGravity(Gravity.START);
        info.addView(size, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (lastResultMode == OUTPUT_BALE) {
            TextView baleShare = primaryAction("ارسال دوباره به بله   ↗");
            LinearLayout.LayoutParams bp = fullWidth();
            bp.topMargin = dp(18);
            root.addView(baleShare, bp);
            baleShare.setOnClickListener(v -> shareToBale(new ArrayList<>(lastShareUris)));
        } else if (lastWasZip) {
            TextView viewZip = primaryAction("مشاهده فایل ZIP   ▰");
            LinearLayout.LayoutParams vp = fullWidth();
            vp.topMargin = dp(18);
            root.addView(viewZip, vp);
            viewZip.setOnClickListener(v -> viewZip());

            TextView share = secondaryAction("اشتراک‌گذاری   ↗");
            LinearLayout.LayoutParams shp = fullWidth();
            shp.topMargin = dp(10);
            root.addView(share, shp);
            share.setOnClickListener(v -> shareZip());
        }

        TextView home = secondaryAction("بازگشت به صفحه اصلی  ⌂");
        LinearLayout.LayoutParams hp = fullWidth();
        hp.topMargin = dp(10);
        hp.bottomMargin = dp(18);
        root.addView(home, hp);
        home.setOnClickListener(v -> {
            selectedUris.clear();
            showHome(true);
        });

        showScreen(scroll, true);
    }

    private void viewZip() {
        if (lastOutputUri == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(lastOutputUri, "application/zip");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "باز کردن فایل ZIP"));
        } catch (Exception e) {
            toast("برنامه‌ای برای باز کردن ZIP پیدا نشد.");
        }
    }

    private void shareZip() {
        if (lastOutputUri == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_STREAM, lastOutputUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل ZIP"));
        } catch (Exception e) {
            toast("اشتراک‌گذاری ممکن نشد.");
        }
    }

    private EncodedJpeg convertOne(Uri uri, boolean targetMode) throws IOException {
        Bitmap bitmap = decodeBitmap(uri);
        try {
            if (!targetMode) return new EncodedJpeg(compress(bitmap, 100), 100);
            return compressMax490Kb(bitmap);
        } finally {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private EncodedJpeg compressMax490Kb(Bitmap source) throws IOException {
        final int budget = MAX_OUTPUT_BYTES - 1024; // حاشیه امن برای سقف قطعی 490KB
        final int preferredQuality = 90;

        byte[] q96 = compress(source, 96);
        if (q96.length <= budget) return new EncodedJpeg(q96, 96);

        EncodedJpeg fullSize = findBestQualityAtOrBelow(source, 96, 84, budget);
        if (fullSize != null) return fullSize;

        // به جای افت شدید JPEG، بزرگ‌ترین رزولوشنی را پیدا می‌کنیم که با Q90 جا شود.
        double lowScale = 0.18d;
        double highScale = 1.0d;
        double bestScale = lowScale;

        for (int i = 0; i < 8; i++) {
            double mid = (lowScale + highScale) / 2d;
            Bitmap probe = highQualityScale(source, mid);
            byte[] data;
            try {
                data = compress(probe, preferredQuality);
            } finally {
                if (probe != source && !probe.isRecycled()) probe.recycle();
            }

            if (data.length <= budget) {
                bestScale = mid;
                lowScale = mid;
            } else {
                highScale = mid;
            }
        }

        Bitmap working = highQualityScale(source, bestScale);
        try {
            // اگر به‌خاطر رفتار غیرخطی JPEG هنوز کمی بزرگ بود، با گام‌های کوچک کم می‌کنیم.
            int guard = 0;
            while (compress(working, preferredQuality).length > budget && guard++ < 10) {
                int currentLong = Math.max(working.getWidth(), working.getHeight());
                int nextLong = Math.max(320, (int) Math.floor(currentLong * 0.94d));
                Bitmap next = scaleToLongEdge(working, nextLong);
                if (next == working) break;
                if (working != source && !working.isRecycled()) working.recycle();
                working = next;
            }

            EncodedJpeg best = findBestQualityAtOrBelow(working, 96, 88, budget);
            if (best != null) return best;

            best = findBestQualityAtOrBelow(working, 87, 82, budget);
            if (best != null) return best;

            // حالت بسیار نادر: رزولوشن را باز کمی کاهش می‌دهیم تا Q82 زیر سقف قرار بگیرد.
            for (int i = 0; i < 8; i++) {
                int currentLong = Math.max(working.getWidth(), working.getHeight());
                int nextLong = Math.max(240, (int) Math.floor(currentLong * 0.90d));
                Bitmap next = scaleToLongEdge(working, nextLong);
                if (next == working) break;
                if (working != source && !working.isRecycled()) working.recycle();
                working = next;

                EncodedJpeg candidate = findBestQualityAtOrBelow(working, 92, 82, budget);
                if (candidate != null) return candidate;
            }

            byte[] finalBytes = compress(working, 82);
            if (finalBytes.length > MAX_OUTPUT_BYTES) {
                throw new IOException("این تصویر با کیفیت امن به سقف ۴۹۰KB نرسید");
            }
            return new EncodedJpeg(finalBytes, 82);
        } finally {
            if (working != source && !working.isRecycled()) working.recycle();
        }
    }

    private Bitmap highQualityScale(Bitmap source, double scale) {
        if (scale >= 0.999d) return source;

        int targetW = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(source.getHeight() * scale));

        Bitmap current = source;
        boolean ownsCurrent = false;

        while (current.getWidth() / 2 >= targetW * 1.25
                && current.getHeight() / 2 >= targetH * 1.25) {
            int nextW = Math.max(targetW, current.getWidth() / 2);
            int nextH = Math.max(targetH, current.getHeight() / 2);
            Bitmap next = Bitmap.createScaledBitmap(current, nextW, nextH, true);
            if (ownsCurrent && current != source && !current.isRecycled()) current.recycle();
            current = next;
            ownsCurrent = true;
        }

        if (current.getWidth() != targetW || current.getHeight() != targetH) {
            Bitmap next = Bitmap.createScaledBitmap(current, targetW, targetH, true);
            if (ownsCurrent && current != source && !current.isRecycled()) current.recycle();
            current = next;
        }

        return current;
    }

    private Bitmap scaleToLongEdge(Bitmap source, int targetLongEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longEdge = Math.max(width, height);

        if (longEdge <= targetLongEdge) return source;

        double scale = targetLongEdge / (double) longEdge;
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));

        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }

    private EncodedJpeg findBestQualityAtOrBelow(Bitmap bitmap, int highQuality, int lowQuality, int maxBytes) throws IOException {
        byte[] lowBytes = compress(bitmap, lowQuality);
        if (lowBytes.length > maxBytes) return null;

        int low = lowQuality;
        int high = highQuality;
        int bestQuality = lowQuality;
        byte[] best = lowBytes;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            byte[] candidate = compress(bitmap, mid);
            if (candidate.length <= maxBytes) {
                best = candidate;
                bestQuality = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return new EncodedJpeg(best, bestQuality);
    }

    private Bitmap decodeBitmap(Uri uri) throws IOException {
        Throwable lastError = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
                    decoder.setMutableRequired(false);
                });
                if (bitmap != null) return bitmap;
            } catch (Throwable e) {
                lastError = e;
            }
        }

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap != null) return bitmap;
            }
        } catch (Throwable e) {
            lastError = e;
        }

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
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw new IOException("تبدیل JPEG ناموفق بود");
        byte[] bytes = out.toByteArray();
        if (!isRealJpeg(bytes)) throw new IOException("خروجی JPEG معتبر نیست");
        return bytes;
    }

    private boolean isRealJpeg(byte[] bytes) {
        return bytes != null && bytes.length >= 4
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
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value;
                }
            }
        } catch (Exception ignored) {}
        return "image.heic";
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024) return toPersianDigits(bytes + " بایت");
        double kb = bytes / 1024.0;
        if (kb < 1024) return toPersianDigits(String.format(Locale.US, "%.0f KB", kb));
        return toPersianDigits(String.format(Locale.US, "%.2f مگابایت", kb / 1024.0));
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private String toPersianDigits(String input) {
        String[] en = {"0","1","2","3","4","5","6","7","8","9"};
        String[] fa = {"۰","۱","۲","۳","۴","۵","۶","۷","۸","۹"};
        for (int i = 0; i < 10; i++) input = input.replace(en[i], fa[i]);
        return input;
    }

    private ScrollView baseScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return scroll;
    }

    private LinearLayout baseRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return root;
    }

    private LinearLayout whiteCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundRect(Color.WHITE, 22));
        card.setElevation(dp(2));
        return card;
    }

    private TextView titleText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(TEXT);
        t.setTextSize(22);
        t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView smallText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(MUTED);
        t.setTextSize(12.5f);
        t.setGravity(Gravity.END);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    private TextView primaryAction(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, Typeface.BOLD);
        button.setMinHeight(dp(58));
        button.setPadding(dp(12), dp(14), dp(12), dp(14));
        button.setBackground(gradient(BLUE, PURPLE, 19));
        button.setElevation(dp(6));
        return button;
    }

    private TextView secondaryAction(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.rgb(61, 67, 102));
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, Typeface.BOLD);
        button.setMinHeight(dp(54));
        button.setPadding(dp(12), dp(13), dp(12), dp(13));
        button.setBackground(roundRect(Color.rgb(237, 239, 248), 18));
        return button;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable gradient(int c1, int c2, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable optionBackground(boolean selected, int tint) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(blendWithWhite(tint, selected ? 0.90f : 0.96f));
        d.setCornerRadius(dp(18));
        d.setStroke(dp(selected ? 2 : 1), selected ? tint : blendWithWhite(tint, 0.72f));
        return d;
    }

    private int blendWithWhite(int color, float whiteRatio) {
        int r = Math.round(Color.red(color) * (1f - whiteRatio) + 255 * whiteRatio);
        int g = Math.round(Color.green(color) * (1f - whiteRatio) + 255 * whiteRatio);
        int b = Math.round(Color.blue(color) * (1f - whiteRatio) + 255 * whiteRatio);
        return Color.rgb(r, g, b);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void showScreen(View view, boolean animate) {
        applyVazir(view);
        setContentView(view);
        if (animate) {
            view.setAlpha(0f);
            view.setTranslationY(dp(10));
            view.animate().alpha(1f).translationY(0).setDuration(220).start();
        }
    }

    private void applyVazir(View view) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int style = tv.getTypeface() == null ? Typeface.NORMAL : tv.getTypeface().getStyle();
            tv.setTypeface(vazir == null ? Typeface.DEFAULT : vazir, style);
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) applyVazir(vg.getChildAt(i));
        }
    }

    private void pulse(View view) {
        if (view == null) return;
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator sx1 = ObjectAnimator.ofFloat(view, View.SCALE_X, view.getScaleX(), 1.035f);
        ObjectAnimator sy1 = ObjectAnimator.ofFloat(view, View.SCALE_Y, view.getScaleY(), 1.035f);
        ObjectAnimator sx2 = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.035f, 1f);
        ObjectAnimator sy2 = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.035f, 1f);
        sx1.setDuration(105); sy1.setDuration(105); sx2.setDuration(180); sy2.setDuration(180);
        AnimatorSet up = new AnimatorSet();
        up.playTogether(sx1, sy1);
        AnimatorSet down = new AnimatorSet();
        down.playTogether(sx2, sy2);
        set.playSequentially(up, down);
        set.setInterpolator(new OvershootInterpolator(1.35f));
        set.start();
    }

    private void animateHome(View view) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dp(10));
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 45L)
                    .setDuration(260)
                    .start();
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        if (screenMode == 1) {
            cancelRequested = true;
            toast("درخواست لغو ثبت شد.");
            return;
        }
        if (screenMode == 2) {
            showHome(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        thumbnailExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class OptionStyle {
        final TextView icon;
        final int tint;
        OptionStyle(TextView icon, int tint) {
            this.icon = icon;
            this.tint = tint;
        }
    }

    private static final class EncodedJpeg {
        final byte[] bytes;
        final int quality;
        EncodedJpeg(byte[] bytes, int quality) {
            this.bytes = bytes;
            this.quality = quality;
        }
    }

    private static final class RingProgress extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int progress = 0;

        RingProgress(Activity context) {
            super(context);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeCap(Paint.Cap.ROUND);
            track.setStrokeWidth(context.getResources().getDisplayMetrics().density * 12f);
            track.setColor(Color.rgb(229, 233, 245));

            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setStrokeWidth(context.getResources().getDisplayMetrics().density * 12f);
            arc.setColor(Color.rgb(72, 89, 244));

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(context.getResources().getDisplayMetrics().scaledDensity * 29f);
            text.setColor(Color.rgb(20, 28, 54));
        }

        void setProgress(int value) {
            progress = Math.max(0, Math.min(100, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float stroke = arc.getStrokeWidth();
            RectF rect = new RectF(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
            canvas.drawArc(rect, -90, 360, false, track);
            canvas.drawArc(rect, -90, 360f * progress / 100f, false, arc);
            Paint.FontMetrics fm = text.getFontMetrics();
            float y = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(toPersian(progress) + "٪", getWidth() / 2f, y, text);
        }

        private String toPersian(int value) {
            return String.valueOf(value)
                    .replace("0","۰").replace("1","۱").replace("2","۲").replace("3","۳").replace("4","۴")
                    .replace("5","۵").replace("6","۶").replace("7","۷").replace("8","۸").replace("9","۹");
        }
    }
}
