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

/**
 * Various configuration flags.
 */
public class Config {
  /**
   * Against this URL we will make the initial request to get the session ID as
   * well as the login POST request.
   */
  public static final String LOGIN_URL = "http://www.kreis-anzeiger.de/epaper/login.php";

  /**
   * This is used as the value for "r" in the login form request. Probably a
   * redirect URL. Might not be necessary, but we try to mimic it as close as
   * possible.
   */
  public static final String LOGIN_FORM_VALUE_R = "http%3A%2F%2Fepaper.vrm.de%2Feausgaben%2Fzgz%2Fzgz%2Fmain.php";

  /**
   * This URL is the site which contains the links to the current issue.
   */
  public static final String MAIN_URL = "http://www.kreis-anzeiger.de/epaper/overview.php";

  /**
   * The PDF URL is relative, so we need to prepend the host of the nav URL.
   */
  public static final String NAV_URL_HOST = "http://www.kreis-anzeiger.de/epaper";

  /** The user-agent string to use for requests to the KA website. */
  public static final String LOGIN_REQUEST_USERAGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_2) AppleWebKit/535.7 (KHTML, like Gecko) Chrome/16.0.912.63 Safari/535.7";

  /** This is where we store temporary runtime data such as caches. */
  public static final String CACHE_PATH = "./cache";
}
