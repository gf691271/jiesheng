package com.frank.jiesheng;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class VideoDocumentFixtureProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        String[] columns = projection != null ? projection : new String[] {
            OpenableColumns.DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        };
        Object[] row = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            switch (columns[index]) {
                case OpenableColumns.DISPLAY_NAME:
                    row[index] = "采访原片-可识别名称.mp4";
                    break;
                case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                    row[index] = 1_784_550_896_000L;
                    break;
                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    row[index] = "video/mp4";
                    break;
                case OpenableColumns.SIZE:
                    row[index] = videoFile().length();
                    break;
                default:
                    row[index] = null;
            }
        }
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(videoFile(), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "video/mp4";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File videoFile() {
        return new File(getContext().getCacheDir(), "provider-tone-video.mp4");
    }
}
