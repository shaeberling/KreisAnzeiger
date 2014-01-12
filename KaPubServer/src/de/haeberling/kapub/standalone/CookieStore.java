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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Cookie store stores and retrieves cookie information form disk.
 */
public class CookieStore {
  private static final Logger log = Logger.getLogger(CookieStore.class
      .getName());
  private static CookieStore instance;
  private final File cacheFile;

  /**
   * Returns a usable CookieStore or <code>null</code> if it could not be
   * created.
   */
  public static CookieStore get(File cacheDirectory) {
    if (instance == null) {
      log.info("Instantiating new cookie store.");
      if (!cacheDirectory.exists()) {
        if (!cacheDirectory.mkdirs()) {
          log.severe("Could not create cache directory.");
          return null;
        }
      }
      File cacheFile = new File(cacheDirectory, "cookies");
      if (!cacheFile.exists()) {
        try {
          cacheFile.createNewFile();
        } catch (IOException ex) {
          log.severe("Could not create cookie cache file.");
          return null;
        }
      }
      if (!cacheFile.canWrite()) {
        log.severe("Cannot write to cookie cache file.");
        return null;
      }
      instance = new CookieStore(cacheFile);
    }
    return instance;
  }

  private CookieStore(File cacheFile) {
    this.cacheFile = cacheFile;
  }

  /**
   * Stores the given cookie data in a file.
   */
  public void storeCookieToFile(CookieData data) throws IOException {
    log.info("Storing cookie data to file.");
    FileWriter writer = new FileWriter(cacheFile);
    writer.write(data.cmsSessionId + "\n");
    writer.close();
  }

  /**
   * Try to load previous cookie data from file. Return null if it failed.
   */
  public CookieData getCookiesFromFile() {
    log.info("Getting cookie data from file.");
    try {
      BufferedReader reader = new BufferedReader(new FileReader(cacheFile));
      String cmsSessionId = reader.readLine();
      reader.close();

      if (cmsSessionId != null) {
        return new CookieData(cmsSessionId);
      }
      log.warning("Could not read values form cookie file.");
    } catch (IOException ex) {
      log.severe("Could not reade cookie file: " + ex.getMessage());
    }
    return null;
  }
}
