package com.phcpransli.labreport;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PdfPrint;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);          // honours <meta viewport width=840> -> fits any screen width
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                        .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                        .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                        .setPositiveButton(android.R.string.ok, (d, w) -> r.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage m) { return true; }
        });

        if (savedInstanceState == null) web.loadUrl(HOME); else web.restoreState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setMessage("એપ બંધ કરવી છે? (ભરેલી માહિતી જતી રહેશે)")
                .setPositiveButton("હા", (d, w) -> finish())
                .setNegativeButton("ના", null).show();
    }

    // ------------------------------------------------------------------ helpers

    private void toast(final String m) {
        ui.post(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
    }

    private void done(final String id, final boolean ok, final String info) {
        final String js = "window.__nativeDone && window.__nativeDone("
                + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(info == null ? "" : info) + ");";
        ui.post(() -> web.evaluateJavascript(js, null));
    }

    private static String safeName(String n) {
        if (n == null || n.trim().isEmpty()) n = "Patient_Report";
        n = n.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        return n.isEmpty() ? "Patient_Report" : n;
    }

    private Uri saveToDownloads(File pdf, String name) throws Exception {
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name + ".pdf");
        v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/");
        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new Exception("Downloads માં ફાઇલ બનાવી શકાઈ નથી");
        try (InputStream in = new FileInputStream(pdf); OutputStream out = cr.openOutputStream(uri)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return uri;
    }

    private void share(File pdf, String phone, String message) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdf);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_TEXT, message);
        i.setClipData(ClipData.newRawUri("report", uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (phone != null && !phone.isEmpty()) {
            Intent wa = new Intent(i);
            wa.setPackage("com.whatsapp");
            wa.putExtra("jid", phone + "@s.whatsapp.net");
            try { startActivity(wa); return; } catch (ActivityNotFoundException ignored) {}
        }
        startActivity(Intent.createChooser(i, "Share report"));
    }

    // ------------------------------------------------------------------ JS bridge

    private class Bridge {

        @JavascriptInterface
        public void toast(String m) { MainActivity.this.toast(m); }

        /** Native system print dialog (printers / Save as PDF). */
        @JavascriptInterface
        public void printPdf(final String name) {
            ui.post(() -> {
                try {
                    PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                    String job = safeName(name);
                    pm.print(job, web.createPrintDocumentAdapter(job),
                            new PrintAttributes.Builder()
                                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                    .build());
                } catch (Exception e) {
                    toast("Print શરૂ ન થયું: " + e.getMessage());
                }
            });
        }

        /** Renders the already-prepared #printPages to a PDF file, then performs action: save | share. */
        @JavascriptInterface
        public void makePdf(final String id, final String name, final String action,
                            final String phone, final String message) {
            ui.post(() -> {
                try {
                    final String base = safeName(name);
                    File dir = new File(getCacheDir(), "pdf");
                    if (!dir.exists()) dir.mkdirs();
                    final File out = new File(dir, base + ".pdf");

                    new PdfPrint().print(web.createPrintDocumentAdapter(base), out, (ok, err) -> {
                        if (!ok) { done(id, false, err); return; }
                        try {
                            switch (action) {
                                case "save":
                                    saveToDownloads(out, base);
                                    break;
                                case "share":
                                    share(out, phone, message);
                                    break;
                            }
                            done(id, true, "");
                        } catch (Exception e) {
                            done(id, false, String.valueOf(e.getMessage()));
                        }
                    });
                } catch (Throwable t) {
                    done(id, false, String.valueOf(t.getMessage()));
                }
            });
        }
    }
}
