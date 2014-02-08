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

package de.haeberling.kapub.standalone;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import de.haeberling.kapub.standalone.Issue.Data;
import de.haeberling.kapub.standalone.Issue.DataProvider;

/**
 * Contains methods to log-in and retrieve the latest PDF of the Kreis-Anzeiger
 * portal.
 */
public class KaPubTools {
  private static final Logger log = Logger
      .getLogger(KaPubTools.class.getName());

  /** Required for logging in the user and fetching the newspaper. */
  private final LoginData loginData;

  public KaPubTools(LoginData loginData) {
    this.loginData = loginData;
  }

  /**
   * Returns the latest issue from the KA website.
   */
  public Issue getLatestIssue(File cacheDirectory) throws IOException {

    // Initialize the cookie store. We do cache cookies to avoid unnecessary
    // logins.
    CookieStore cookieStore = CookieStore.get(cacheDirectory);
    CookieData cookieData = null;
    if (cookieStore == null) {
      log.severe("Could not initialize cookie store.");
      return null;
    }
    cookieData = cookieStore.getCookiesFromFile();
    boolean cookieDataFromLogin = false;

    // Get cookie data from actually logging in as we don't seem to have any
    // data on file.
    if (cookieData == null) {
      cookieData = loginAndGetCookies(this.loginData, cookieStore);
      cookieDataFromLogin = true;
    }

    String pdfLink = getPdfLink(cookieData);

    // If retrieving the link failed but the cookie data is old, it might be
    // that the session timed out. In this case we try to get fresh session
    // data.
    if (pdfLink == null && !cookieDataFromLogin) {
      log.info("Looks like the session might not be valid anymore. Logging in again.");
      cookieData = loginAndGetCookies(this.loginData, cookieStore);
      pdfLink = getPdfLink(cookieData);
    }

    if (pdfLink == null) {
      log.severe("Could not extract pdf link.");
      return null;
    }

    // This will contain the information we need to retrieve the issue.
    Issue issue = new Issue(String.valueOf(System.currentTimeMillis()));
    createStreamForUrl(issue, pdfLink, cookieData);
    return issue;
  }

  /**
   * Phase 1: We make a POST request to log-in to the site.
   *
   * The cookie will be written to the given cookie store as well.
   */
  private static CookieData loginAndGetCookies(LoginData loginData,
      CookieStore cookieStore) throws IOException {
    // First we contacts the login page in order to get a new session ID cookie.
    // We get a new one without providing a username or password.
    HttpGet get = new HttpGet(Config.LOGIN_URL);
    DefaultHttpClient client = new DefaultHttpClient();
    HttpResponse response = client.execute(get);

    String cmsSessionId = null;
    log.info(response.getStatusLine().toString() + "\n");
    for (Header header : response.getAllHeaders()) {
      log.info(header.getName() + " : " + header.getValue() + "\n");
      if (header.getName().toLowerCase().equals("set-cookie")) {
        String cookieString = header.getValue();
        int start = cookieString.indexOf("CMS_SESSION_ID=");
        if (start >= 0) {
          cmsSessionId = cookieString.substring(start,
              cookieString.indexOf("; ", start));
        }
      }
    }

    if (cmsSessionId == null) {
      throw new IOException("Could not extract CMS_SESSION_ID cookie.");
    }

    log.info("CMS_SESSION_ID: " + cmsSessionId + "\n");
    CookieData cookieData = new CookieData(cmsSessionId);

    // Store the cookies from the login to file for later re-use.
    cookieStore.storeCookieToFile(cookieData);

    // Now that we have a session ID, we need to login. Logging in means
    // providing our fresh session ID together with the username and password.
    HttpPost post = getPostRequestForLogin(loginData, cookieData);
    client = new DefaultHttpClient();
    response = client.execute(post);

    // If the login succeeds, the returned status code is a redirect to the
    // overview page.
    if (response.getStatusLine().getStatusCode() != 302
        && response.getStatusLine().getStatusCode() != 200) {
      throw new IOException("Login failed. Wrong status code: "
          + response.getStatusLine().getStatusCode());
    }
    log.info("Login in was successful!");

    return cookieData;
  }

  /**
   * Phase 2: Makes a request to the nav site which contains the link to the
   * PDF.
   */
  private static String getPdfLink(CookieData cookieData) throws IOException {
    DefaultHttpClient client = new DefaultHttpClient();
    HttpGet get = new HttpGet(Config.MAIN_URL);
    get.setHeader("Cookie", cookieData.toString());
    try {
      HttpResponse response = client.execute(get);
      String content = readStringFromStream(response.getEntity().getContent());

      // Get the PDF link from the overview page.
      return parsePdfLink(content);
    } catch (Exception ex) {
      // Being defense, catching any kind of Exception.
      // A ProtocolException can be thrown if the session ran out and a redirect
      // 302 response is returned.
      log.log(Level.WARNING, ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Takes the URL and the cookie data, and returns a usable inputstream that
   * can be used to load the resource.
   */
  private static void createStreamForUrl(Issue issue, final String urlStr,
      final CookieData cookieData) {
    DataProvider dataProvider = new DataProvider() {
      @Override
      public Data getData() {
        try {
          log.info("Getting data for URL: " + urlStr);
          URL url = new URL(urlStr);
          HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
          if (cookieData != null) {
            urlConn.setRequestProperty("Cookie", cookieData.toString());
          }
          urlConn.setDoInput(true);
          urlConn.connect();

          String contentLengthStr = urlConn.getHeaderField("Content-Length");
          log.log(Level.INFO, "Content-length: " + contentLengthStr);
          int contentLength = contentLengthStr != null ? Integer
              .parseInt(contentLengthStr) : 0;
          return new Data(urlConn.getInputStream(), contentLength);
        } catch (IOException ex) {
          log.severe("Could not create Data object: " + ex.getMessage());
          return null;
        }
      }
    };
    issue.setDataProvider(dataProvider);
  }

  /**
   * Parses the redirect age for the actual PDF Link URL.
   */
  private static String parsePdfLink(String html) {
    // We have to search for the download link. The best way right now is to
    // search for the contents and end of the link, and then search backwards
    // the beginning of it. From there we just find the href, and we're done.
    final String DOWNLOAD_LINK_START = "<a href=\"pdf.php";

    int linkStart = html.indexOf(DOWNLOAD_LINK_START) + 9;
    int linkEnd = html.indexOf("\" ", linkStart);

    final String href = html.substring(linkStart, linkEnd);

    return Config.NAV_URL_HOST + '/' + href;
  }

  /**
   * Reads the data from the given stream as a string and returns the result.
   */
  private static String readStringFromStream(InputStream is) throws IOException {
    StringBuilder content = new StringBuilder();
    InputStreamReader reader = new InputStreamReader(is);
    char[] buffer = new char[1024];
    while (true) {
      int num = reader.read(buffer);
      if (num < 0) {
        break;
      }
      content.append(buffer, 0, num);
    }
    return content.toString();
  }

  /**
   * Creates and returns a POST request that is used for the login.
   */
  private static HttpPost getPostRequestForLogin(LoginData loginData,
      CookieData cookieData) throws UnsupportedEncodingException {
    HttpPost post = new HttpPost(Config.LOGIN_URL);
    post.setHeader("Accept-Encoding", "deflate");
    post.setHeader("Content-Type", "application/x-www-form-urlencoded");
    post.setHeader("User-Agent", Config.LOGIN_REQUEST_USERAGENT);
    post.setHeader("Cookie", cookieData.toString());
    post.setEntity(new StringEntity(getPostParameters(loginData)));
    return post;
  }

  /**
   * Returns the POST parameters to be used for the login request.
   */
  private static String getPostParameters(LoginData loginData) {
    return "username=" + loginData.username + "&password=" + loginData.password;
  }
}
