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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;


public class ImageKeyboard extends InputMethodService {

  private static final String TAG = "ImageKeyboard";
  private static final String AUTHORITY = "com.rncustomkeyboard.inputcontent";
  private static final String MIME_TYPE_PNG = "image/png";
  private boolean pngSupported;
  private String[] rawFiles;

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


  /**
   * Gets all resources names from the raw folder
   * @return String []
   */
  private String[] getAllRawResources() {
    Field fields[] = R.raw.class.getDeclaredFields() ;
    String[] names = new String[fields.length] ;

    try {
      for( int i=0; i< fields.length; i++ ) {
        Field f = fields[i] ;
        names[i] = f.getName();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return names;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    rawFiles = getAllRawResources();
  }

  @Override
  public View onCreateInputView() {
    final File imagesDir = new File(getFilesDir(), "images");
    RelativeLayout KeyboardLayout = (RelativeLayout) getLayoutInflater().inflate(R.layout.keyboard_layout, null);
    LinearLayout ImageContainer = (LinearLayout) KeyboardLayout.findViewById(R.id.imageContainer);

    LinearLayout ImageContainerColumn = (LinearLayout) getLayoutInflater().inflate(R.layout.image_container_column, ImageContainer, false);

    for (int i = 0; i < rawFiles.length; i++) {
      System.out.println(i);
      if ((i % 2) == 0) {
        ImageContainerColumn = (LinearLayout) getLayoutInflater().inflate(R.layout.image_container_column, ImageContainer, false);
      }

      // Creating button
      ImageButton ImgButton = (ImageButton) getLayoutInflater().inflate(R.layout.image_button, ImageContainerColumn, false);
      ImgButton.setImageResource(getResources().getIdentifier(rawFiles[i], "raw", getPackageName()));
      ImgButton.setTag(rawFiles[i]);
      ImgButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          String emojiName = view.getTag().toString().replaceAll("_", "-");

          final File file = getFileForResource(ImageKeyboard.this, getResources().getIdentifier(view.getTag().toString(), "raw", getPackageName()), imagesDir, "${view.getTag().toString()}.png");
          ImageKeyboard.this.doCommitContent("A ${emojiName} logo", MIME_TYPE_PNG, file);
        }
      });

      ImageContainerColumn.addView(ImgButton);

      if ((i % 2) == 0) {
        ImageContainer.addView(ImageContainerColumn);
      }
    }
    return KeyboardLayout;


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