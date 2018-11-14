package com.rncustomkeyboard;

import android.app.AppOpsManager;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class ImageKeyboard extends InputMethodService {

  private static final String TAG = "ImageKeyboard";
  private static final String AUTHORITY = "com.rncustomkeyboard.inputcontent";
  private static final String MIME_TYPE_PNG = "image/png";
  private boolean pngSupported;
  private File smileFile;

  private boolean isCommitContentSupported(
      @Nullable EditorInfo editorInfo, @NonNull String mimeType) {

    if (editorInfo == null) {
      return false;
    }

    final InputConnection ic = getCurrentInputConnection();
    if (ic == null) {
      return false;
    }

    if (!validatePackageName(editorInfo)) {
      return false;
    }

    final String[] supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo);

    System.out.println(editorInfo);
    for (String supportedMimeType : supportedMimeTypes) {

      if (ClipDescription.compareMimeTypes(mimeType, supportedMimeType)) {
        return true;
      }
    }
    return false;
  }

  private void doCommitContent(@NonNull String description, @NonNull String mimeType,
                               @NonNull File file) {

    final EditorInfo editorInfo = getCurrentInputEditorInfo();

    final Uri contentUri = FileProvider.getUriForFile(this, AUTHORITY, file);

    final int flag;
    if (Build.VERSION.SDK_INT >= 25) {
      // On API 25 and later devices, as an analogy of Intent.FLAG_GRANT_READ_URI_PERMISSION,
      // you can specify InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION to give
      // a temporary read access to the recipient application without exporting your content
      // provider.
      flag = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
    } else {
      // On API 24 and prior devices, we cannot rely on
      // InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION. You as an IME author
      // need to decide what access control is needed (or not needed) for content URIs that
      // you are going to expose. This sample uses Context.grantUriPermission(), but you can
      // implement your own mechanism that satisfies your own requirements.
      flag = 0;
      try {
        grantUriPermission(
            editorInfo.packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (Exception e){
        Log.e(TAG, "grantUriPermission failed packageName=" + editorInfo.packageName
            + " contentUri=" + contentUri, e);
      }
    }

    final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
        contentUri,
        new ClipDescription(description, new String[]{mimeType}),
        null);
    InputConnectionCompat.commitContent(
        getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat,
        flag, null);
  }

  private boolean validatePackageName(@Nullable EditorInfo editorInfo) {
    if (editorInfo == null) {
      return false;
    }
    final String packageName = editorInfo.packageName;
    if (packageName == null) {
      return false;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return true;
    }

    final InputBinding inputBinding = getCurrentInputBinding();
    if (inputBinding == null) {
      // Due to b.android.com/225029, it is possible that getCurrentInputBinding() returns
      // null even after onStartInputView() is called.
      // TODO: Come up with a way to work around this bug....
      Log.e(TAG, "inputBinding should not be null here. "
          + "You are likely to be hitting b.android.com/225029");
      return false;
    }
    final int packageUid = inputBinding.getUid();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      final AppOpsManager appOpsManager =
          (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
      try {
        appOpsManager.checkPackage(packageUid, packageName);
      } catch (Exception e) {
        return false;
      }
      return true;
    }

    final PackageManager packageManager = getPackageManager();
    final String possiblePackageNames[] = packageManager.getPackagesForUid(packageUid);
    for (final String possiblePackageName : possiblePackageNames) {
      if (packageName.equals(possiblePackageName)) {
        return true;
      }
    }
    return false;
  }


  public void addImage(View view) {
    ImageKeyboard.this.doCommitContent(
        "Android N recovery animation", MIME_TYPE_PNG, smileFile);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    final File imagesDir = new File(getFilesDir(), "images");
    imagesDir.mkdirs();
    smileFile = getFileForResource(this, R.raw.smile, imagesDir, "smile.png");
  }

  @Override
  public View onCreateInputView() {

    return getLayoutInflater().inflate(R.layout.keyboard_layout, null);

  }

  @Override
  public boolean onEvaluateFullscreenMode() {
    // In full-screen mode the inserted content is likely to be hidden by the IME. Hence in this
    // sample we simply disable full-screen mode.
    return false;
  }

  @Override
  public void onStartInputView(EditorInfo info, boolean restarting) {
    pngSupported = isCommitContentSupported(info, MIME_TYPE_PNG);

    if(!pngSupported) {
      Toast.makeText(getApplicationContext(),
          "Images not supported here. Please change to another keyboard.",
          Toast.LENGTH_SHORT).show();
    }
  }

  //  Maybe call this on button click
  private static File getFileForResource(
      @NonNull Context context, @RawRes int res, @NonNull File outputDir,
      @NonNull String filename) {
    final File outputFile = new File(outputDir, filename);
    final byte[] buffer = new byte[4096];
    InputStream resourceReader = null;
    try {
      try {
        resourceReader = context.getResources().openRawResource(res);
        OutputStream dataWriter = null;
        try {
          dataWriter = new FileOutputStream(outputFile);
          while (true) {
            final int numRead = resourceReader.read(buffer);
            if (numRead <= 0) {
              break;
            }
            dataWriter.write(buffer, 0, numRead);
          }
          return outputFile;
        } finally {
          if (dataWriter != null) {
            dataWriter.flush();
            dataWriter.close();
          }
        }
      } finally {
        if (resourceReader != null) {
          resourceReader.close();
        }
      }
    } catch (IOException e) {
      return null;
    }
  }
}