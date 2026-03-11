package com.fsck.k9.provider;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.FileNotFoundException;

import com.fsck.k9.K9;
import timber.log.Timber;

import okio.ByteString;
import org.apache.commons.io.IOUtils;


public class AttachmentTempFileProvider extends FileProvider {
    private static final String CACHE_DIRECTORY = "temp";
    private static final long FILE_DELETE_THRESHOLD_MILLISECONDS = 3 * 60 * 1000;
    private static final Object tempFileWriteMonitor = new Object();
    private static final Object cleanupReceiverMonitor = new Object();

    private static String AUTHORITY;
    private static AttachmentTempFileProviderCleanupReceiver cleanupReceiver = null;


    @Override
    public boolean onCreate() {
        return super.onCreate();
    }

    @Override
    public void attachInfo(@NonNull Context context, @NonNull ProviderInfo info) {
        AUTHORITY = info.authority;
        super.attachInfo(context, info);
    }

    private static synchronized String getAuthority(Context context) {
        if (AUTHORITY == null) {
            AUTHORITY = context.getPackageName() + ".tempfileprovider";
        }
        return AUTHORITY;
    }

    @WorkerThread
    public static Uri createTempUriForContentUri(Context context, Uri uri, String displayName) throws IOException {
        Context applicationContext = context.getApplicationContext();

        String tempFilename = getTempFilenameForUri(uri);
        if (displayName != null) {
            int lastDot = displayName.lastIndexOf('.');
            if (lastDot >= 0) {
                String extension = displayName.substring(lastDot).toLowerCase(Locale.ENGLISH);
                // Basic validation of extension
                if (extension.length() > 1 && extension.length() < 10 && extension.indexOf('/') == -1) {
                    tempFilename += extension;
                }
            }
        }

        File tempDirectory = getTempFileDirectory(applicationContext);
        File tempFile = new File(tempDirectory, tempFilename);

        writeUriContentToTempFileIfNotExists(context, uri, tempFile);

        Uri tempFileUri = FileProvider.getUriForFile(context, getAuthority(applicationContext), tempFile);

        registerFileCleanupReceiver(applicationContext);

        return tempFileUri;
    }

    private static String getTempFilenameForUri(Uri uri) {
        return ByteString.encodeUtf8(uri.toString()).sha1().hex();
    }

    private static void writeUriContentToTempFileIfNotExists(Context context, Uri uri, File tempFile)
            throws IOException {
        synchronized (tempFileWriteMonitor) {
            if (tempFile.exists()) {
                return;
            }

            try (FileOutputStream outputStream = new FileOutputStream(tempFile);
                 InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    throw new IOException("Failed to resolve content at uri: " + uri);
                }
                IOUtils.copy(inputStream, outputStream);
            }
        }
    }

    public static Uri getMimeTypeUri(Uri contentUri, String mimeType) {
        if (contentUri.getQueryParameter("mime_type") != null) {
            return contentUri;
        }
        return contentUri.buildUpon().appendQueryParameter("mime_type", mimeType).build();
    }

    public static boolean deleteOldTemporaryFiles(Context context) {
        File tempDirectory = getTempFileDirectory(context);
        boolean allFilesDeleted = true;
        long deletionThreshold = System.currentTimeMillis() - FILE_DELETE_THRESHOLD_MILLISECONDS;
        File[] files = tempDirectory.listFiles();
        if (files == null) {
            return true;
        }
        for (File tempFile : files) {
            long lastModified = tempFile.lastModified();
            if (lastModified < deletionThreshold) {
                boolean fileDeleted = tempFile.delete();
                if (!fileDeleted) {
                    Timber.e("Failed to delete temporary file");
                    allFilesDeleted = false;
                }
            } else {
                if (K9.isDebugLoggingEnabled()) {
                    String timeLeftStr = String.format(
                            Locale.ENGLISH, "%.2f", (lastModified - deletionThreshold) / 1000 / 60.0);
                    Timber.e("Not deleting temp file (for another %s minutes)", timeLeftStr);
                }
                allFilesDeleted = false;
            }
        }

        return allFilesDeleted;
    }

    private static File getTempFileDirectory(Context context) {
        File directory = new File(context.getCacheDir(), CACHE_DIRECTORY);
        if (!directory.exists()) {
            if (!directory.mkdir()) {
                Timber.e("Error creating directory: %s", directory.getAbsolutePath());
            }
        }

        return directory;
    }


    @Override
    public String getType(@NonNull Uri uri) {
        String mimeType = uri.getQueryParameter("mime_type");
        if (mimeType != null) {
            return mimeType;
        }

        return super.getType(stripQuery(uri));
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        return super.openFile(stripQuery(uri), mode);
    }

    @NonNull
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        String[] columnNames = projection;
        if (columnNames == null || columnNames.length == 0) {
            columnNames = new String[] {
                    OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, "_data"
            };
        }

        MatrixCursor newCursor = new MatrixCursor(columnNames);

        File file = getFileForUri(uri);
        if (file != null && file.exists()) {
            String displayName = uri.getQueryParameter("display_name");
            if (displayName == null) {
                displayName = file.getName();
            }

            Object[] values = new Object[columnNames.length];
            for (int i = 0; i < columnNames.length; i++) {
                String column = columnNames[i];
                if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                    values[i] = displayName;
                } else if (OpenableColumns.SIZE.equals(column)) {
                    values[i] = file.length();
                } else if ("_data".equals(column)) {
                    values[i] = file.getAbsolutePath();
                } else {
                    values[i] = null;
                }
            }
            newCursor.addRow(values);
        } else {
            Timber.w("File not found for URI: %s", uri);
            // If the file is not found, we still return the columns but no rows.
            // Some apps might crash if we don't return the requested columns.
        }

        return newCursor;
    }

    private File getFileForUri(Uri uri) {
        List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() < 2 || !"temp".equals(pathSegments.get(0))) {
            return null;
        }

        String filename = pathSegments.get(1);
        Context context = getContext();
        if (context == null) return null;

        return new File(getTempFileDirectory(context), filename);
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return super.delete(stripQuery(uri), selection, selectionArgs);
    }

    private Uri stripQuery(Uri uri) {
        if (uri.getQuery() == null) {
            return uri;
        }
        return uri.buildUpon().query(null).build();
    }

    @Override
    public void onTrimMemory(int level) {
        if (level < TRIM_MEMORY_COMPLETE) {
            return;
        }
        final Context context = getContext();
        if (context == null) {
            return;
        }

        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                deleteOldTemporaryFiles(context);
                return null;
            }
        }.execute();

        unregisterFileCleanupReceiver(context);
    }

    private static void unregisterFileCleanupReceiver(Context context) {
        synchronized (cleanupReceiverMonitor) {
            if (cleanupReceiver == null) {
                return;
            }

            Timber.d("Unregistering temp file cleanup receiver");
            context.unregisterReceiver(cleanupReceiver);
            cleanupReceiver = null;
        }
    }

    private static void registerFileCleanupReceiver(Context context) {
        synchronized (cleanupReceiverMonitor) {
            if (cleanupReceiver != null) {
                return;
            }

            Timber.d("Registering temp file cleanup receiver");
            cleanupReceiver = new AttachmentTempFileProviderCleanupReceiver();

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            ContextCompat.registerReceiver(context, cleanupReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }
    }

    private static class AttachmentTempFileProviderCleanupReceiver extends BroadcastReceiver {
        @Override
        @MainThread
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                throw new IllegalArgumentException("onReceive called with action that isn't screen off!");
            }

            Timber.d("Cleaning up temp files");

            boolean allFilesDeleted = deleteOldTemporaryFiles(context);
            if (allFilesDeleted) {
                unregisterFileCleanupReceiver(context);
            }
        }
    }
}
