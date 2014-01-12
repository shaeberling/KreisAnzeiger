/*
 * Copyright 2011 Sascha HŠberling
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.haeberling.kapub.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import de.haeberling.kapub.standalone.Issue;
import de.haeberling.kapub.standalone.Issue.Data;
import de.haeberling.kapub.standalone.KaPubTools;
import de.haeberling.kapub.standalone.LoginData;

/**
 * The main activity which deals with logging in, and downloading the PDF to
 * disk. Once downloaded, an activity chooser is opened.
 * <p>
 * Viewing itself is therefore not handled by this application but instead
 * delegated to capable PDF viewers.
 */
public class DownloadActivity extends Activity {
  private static final String TAG = DownloadActivity.class.getSimpleName();

  private TextView statusView;
  private TextView bytesDownloadedView;

  private boolean cancelDownload = false;

  private KaPubTools kaPubTools;

  private class DownloadTask extends AsyncTask<Void, Integer, File> {
    @Override
    protected File doInBackground(Void... params) {
      try {
        Issue issue = kaPubTools.getLatestIssue(getFilesDir());
        if (issue == null) {
          Log.e(TAG, "Could not get issue. Aborting.");
          return null;
        }

        Data data = issue.getData();

        publishProgress(1);
        File extPath = getExternalFilesDir(null);
        Log.d(TAG, "External Path: " + extPath);

        File tempFile = new File(getExternalFilesDir(null),
            "Kreis-Anzeiger.pdf");
        if (writeToFile(data.stream, tempFile, new Callback<Integer>() {

          @Override
          public void onCallback(Integer downloadedBytes) {
            publishProgress(downloadedBytes);
          }
        })) {
          return tempFile;
        } else {
          return null;
        }
      } catch (IOException e) {
        Log.e(TAG, e.getMessage(), e);
      }
      return null;
    }

    @Override
    protected void onPostExecute(File pdfFile) {
      if (pdfFile == null) {
        statusView.setText(R.string.error);
        return;
      }

      statusView.setText(R.string.done_opening);
      bytesDownloadedView.setText("");

      Intent showPdfIntent = new Intent(android.content.Intent.ACTION_VIEW);

      Uri pdfUri = Uri.fromFile(pdfFile);
      showPdfIntent.setDataAndType(pdfUri, "application/pdf");
      showPdfIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(showPdfIntent,
          getString(R.string.open_width)));
      finish();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
      statusView.setText(R.string.downloading_issue);
      bytesDownloadedView.setText(values[0] / 1024 + " KB");
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(Menu.NONE, 1, Menu.CATEGORY_SYSTEM, R.string.settings)
        .setIcon(android.R.drawable.ic_menu_preferences)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    return true;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final String accountUsernameKey = getResources().getString(
        R.string.account_username_key);
    final String accountPasswordKey = getResources().getString(
        R.string.account_password_key);
    final SharedPreferences sharedPref = PreferenceManager
        .getDefaultSharedPreferences(this);

    cancelDownload = false;
    setContentView(R.layout.activity_download);
    getActionBar().hide();

    statusView = (TextView) findViewById(R.id.statusView);
    bytesDownloadedView = (TextView) findViewById(R.id.bytesDownloaded);

    ImageButton preferencesButton = (ImageButton) findViewById(R.id.preferencesButton);
    preferencesButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        startActivity(new Intent(DownloadActivity.this, SettingsActivity.class));
      }
    });

    final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
    final Button downloadButton = (Button) findViewById(R.id.downloadButton);
    downloadButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        String username = sharedPref.getString(accountUsernameKey, null);
        String password = sharedPref.getString(accountPasswordKey, null);

        // Make sure login information has been provided.
        if (username == null || password == null || username.isEmpty()
            || password.isEmpty()) {
          Toast.makeText(DownloadActivity.this,
              R.string.loginInformationMissing, Toast.LENGTH_LONG).show();
          return;
        }

        LoginData loginData = new LoginData(username, password);
        kaPubTools = new KaPubTools(loginData);

        downloadButton.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        DownloadTask downloadTask = new DownloadTask();
        downloadTask.execute((Void) null);
      }
    });
  }

  @Override
  protected void onPause() {
    super.onPause();
    cancelDownload = true;
  }

  private boolean writeToFile(InputStream stream, File tempFile,
      Callback<Integer> progressCallback) {
    try {
      FileOutputStream fos = new FileOutputStream(tempFile);
      byte[] buffer = new byte[4096];
      int length = 0;
      int total = 0;
      while ((length = stream.read(buffer)) > 0 && !cancelDownload) {
        fos.write(buffer, 0, length);
        total += length;
        progressCallback.onCallback(total);
      }
      fos.close();
      return !cancelDownload;
    } catch (IOException e) {
      Log.e(TAG, e.getMessage(), e);
    }
    return false;
  }
}
