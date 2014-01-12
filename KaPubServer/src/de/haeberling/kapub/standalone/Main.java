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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Logger;

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;

import de.haeberling.kapub.standalone.Issue.Data;

/**
 * The KaPub Main. This binary starts up a webserver from which it serves the
 * latest KA issue.
 */
public class Main implements Container {
  private static final Logger log = Logger.getLogger(Main.class.getName());
  private static final String AUTH_TOKEN_PARAM = "a";
  private static final int PORT = 9999;

  private static Issue cachedIssue = null;

  public static void main(String[] args) throws IOException {
    log.info("Kreis-Anzeiger Publisher Main starting up");
    Container container = new Main();
    Connection connection = new SocketConnection(container);
    SocketAddress address = new InetSocketAddress(PORT);
    connection.connect(address);
    log.info("Web server now ready on port " + PORT + ".");
  }

  @Override
  public void handle(Request req, Response resp) {
    log.info("Got Request: " + req.getAddress().toString());

    // Check whether this is a non-auth request.
    try {
      if (handleNonAuthRequest(req, resp)) {
        return;
      }
    } catch (Exception ex) {
      log.severe("Error while serving non-auth request: " + ex.getMessage());
      return;
    }

    // Check auth token.
    String authToken;
    try {
      authToken = req.getParameter(AUTH_TOKEN_PARAM);
      log.info("AuthToken: " + authToken);
      // TODO: Use new fetch method.
      // if (authToken == null || !authToken.equals(Config.AUTH_TOKEN)) {
      // log.warning("Auth-Token invalid.");
      // resp.setCode(401);
      // resp.close();
      // return;
      // }
    } catch (Exception ex) {
      log.warning("Could not read auth token," + ex.getMessage());
      resp.setCode(500);
      return;
    }

    try {
      boolean loadIndexPage = req.getAddress().getPath().toString().equals("/");
      boolean loadPdf = Boolean.parseBoolean(req.getParameter("pdf"));
      if (loadPdf) {
        if (cachedIssue == null) {
          handleIndexPageRequest(resp, true, authToken);
        } else {
          handlePdfServingRequest(resp);
        }
      } else if (loadIndexPage) {
        // Send e-mail about successful auth request.
        Mailing.sendMail("KaPub Request", req.toString() + "\n\nFrom: "
            + "From: " + req.getClientAddress().toString());
        // TODO. Make tools a member and set proper login data.
        LoginData loginData = null;
        KaPubTools tools = new KaPubTools(loginData);
        cachedIssue = tools.getLatestIssue(new File(Config.CACHE_PATH));
        boolean error = cachedIssue == null;
        handleIndexPageRequest(resp, error, authToken);
      } else {
        log.info("Not processing this request");
        resp.setCode(404);
        resp.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
      log.severe("Error while serving response: " + e.getMessage());
    }
  }

  /**
   * Serves the index page.
   */
  public void handleIndexPageRequest(Response resp, boolean error,
      String authToken) throws IOException {
    setResponseHeaders(resp, "text/html");
    PrintStream body = resp.getPrintStream();
    body.println("<html><head><title>Kreis-Anzeiger</title>");
    body.println("<link rel=\"apple-touch-icon\" href=\"/icon57.png\"/>");
    if (!error) {
      String fileName = cachedIssue.getFileName() + ".pdf";
      body.println("<meta http-equiv=\"refresh\" content=\"0;url=" + fileName
          + "?pdf=true&a=" + authToken + "\"></head><body>");
    }
    body.println("<style>body {font-family:Arial;font-size:4em}</style>");
    if (!error) {
      body.println("Ausgabe wird geladen ... ");
    } else {
      body.println("Es ist ein Fehler aufgetreten. Bitte Sascha bescheid geben ;)");
    }
    body.println("</body></html>");
    body.close();
    log.info("Index page response served.");
  }

  /**
   * Serves the PDF from the cached stream.
   */
  public static void handlePdfServingRequest(Response resp) throws IOException {
    Data data = cachedIssue.getData();
    if (data.contentLength > 0) {
      resp.setContentLength(data.contentLength);
    }
    if (serveStream(resp, data.stream, "application/pdf")) {
      log.info("PDF served.");
    }
  }

  /**
   * Handles requests that don't require auth. Returns whether the request was
   * served and thus no further handling is required.
   */
  private static boolean handleNonAuthRequest(Request req, Response resp)
      throws IOException {

    String fileToServe = null;
    if (req.getPath().toString().equals("/icon57.png")) {
      fileToServe = "data/icon57.png";
    } else if (req.getPath().toString().equals("/favicon.ico")) {
      fileToServe = "data/favicon57.png";
    }

    if (fileToServe != null) {
      log.info("Serving " + req.getPath().toString());
      InputStream stream = Main.class.getResourceAsStream(fileToServe);
      serveStream(resp, stream, "image/png");
      return true;
    }

    return false;
  }

  private static boolean serveStream(Response resp, InputStream stream,
      String mimeType) throws IOException {
    setResponseHeaders(resp, mimeType);
    BufferedOutputStream output = new BufferedOutputStream(
        resp.getOutputStream());
    BufferedInputStream input = new BufferedInputStream(stream);

    byte[] buffer = new byte[4096];
    int length;
    try {
      while ((length = input.read(buffer)) != -1) {
        output.write(buffer, 0, length);
      }
      output.flush();
      output.close();
      resp.close();
      return true;
    } catch (IOException ex) {
      log.warning("Writing aborted.");
      resp.close();
    }
    return false;
  }

  /**
   * Sets the response headers for web requests to the server.
   */
  private static void setResponseHeaders(Response resp, String mimeType) {
    final long time = System.currentTimeMillis();
    resp.set("Content-Type", mimeType);
    resp.set("Server", "KaPub/0.1 (Simple 4)");
    resp.set("Cache-Control", "max-age=0");
    resp.set("Expires", "-1");
    resp.setDate("Date", time);
    resp.setDate("Last-Modified", time);
  }
}
