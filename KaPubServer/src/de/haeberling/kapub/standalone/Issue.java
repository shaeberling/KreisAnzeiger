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

import java.io.InputStream;

/**
 * Contains information about the issue as well as the stream to download it.
 */
public class Issue {
  public static class Data {
    public final InputStream stream;
    public final int contentLength;

    public Data(InputStream stream, int contentLength) {
      this.stream = stream;
      this.contentLength = contentLength;
    }
  }

  public interface DataProvider {
    public Data getData();
  }

  private final String fileName;
  private DataProvider dataProvider;

  public Issue(String fileName) {
    this.fileName = fileName;
  }

  public String getFileName() {
    return this.fileName;
  }

  public void setDataProvider(DataProvider dataProvider) {
    this.dataProvider = dataProvider;
  }

  public Data getData() {
    return this.dataProvider.getData();
  }
}
