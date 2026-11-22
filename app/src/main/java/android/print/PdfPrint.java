package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * Writes the WebView's print layout (A4, @media print CSS) straight to a PDF file.
 * Lives in package android.print because the layout/write callbacks are package-private.
 */
public class PdfPrint {

    public interface Callback {
        void onDone(boolean ok, String error);
    }

    private final PrintAttributes attrs = new PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build();

    public void print(final PrintDocumentAdapter adapter, final File file, final Callback cb) {
        try {
            adapter.onLayout(null, attrs, new CancellationSignal(),
                    new PrintDocumentAdapter.LayoutResultCallback() {
                        @Override
                        public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                            try {
                                final ParcelFileDescriptor fd = ParcelFileDescriptor.open(file,
                                        ParcelFileDescriptor.MODE_READ_WRITE
                                                | ParcelFileDescriptor.MODE_CREATE
                                                | ParcelFileDescriptor.MODE_TRUNCATE);
                                adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, fd,
                                        new CancellationSignal(),
                                        new PrintDocumentAdapter.WriteResultCallback() {
                                            @Override
                                            public void onWriteFinished(PageRange[] pages) {
                                                try { fd.close(); } catch (Exception ignored) {}
                                                adapter.onFinish();
                                                cb.onDone(true, null);
                                            }

                                            @Override
                                            public void onWriteFailed(CharSequence error) {
                                                try { fd.close(); } catch (Exception ignored) {}
                                                adapter.onFinish();
                                                cb.onDone(false, String.valueOf(error));
                                            }
                                        });
                            } catch (Throwable t) {
                                cb.onDone(false, String.valueOf(t));
                            }
                        }

                        @Override
                        public void onLayoutFailed(CharSequence error) {
                            cb.onDone(false, String.valueOf(error));
                        }
                    }, null);
        } catch (Throwable t) {
            cb.onDone(false, String.valueOf(t));
        }
    }
}
