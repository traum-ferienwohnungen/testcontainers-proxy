package com.traum.mountebank;

/*-
 * #%L
 * testcontainers-proxy-mountebank
 * %%
 * Copyright (C) 2020 Traum-Ferienwohnungen GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.traum.io.ReplacingInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

public abstract class MountebankProxy {

  private final HttpClient client = HttpClient.newBuilder().build();

  public void start() {}

  public void stop() {}

  public boolean isRunning() {
    return false;
  }

  public void importImposters(Path impostersInput) throws IOException {
    final String mountebankUri = getApiUrl();

    HttpRequest postRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(mountebankUri + "/imposters"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofFile(impostersInput))
            .build();

    client
        .sendAsync(postRequest, HttpResponse.BodyHandlers.ofString())
        .thenAccept(
            response ->
                assertTrue(
                    response.statusCode() >= 200 && response.statusCode() < 300, response.body()))
        .join();
  }

  public void saveImposters(Path impostersOutput, boolean forReplay) {
    if (impostersOutput != null) {
      final String query = forReplay ? "?removeProxies=true&replayabe=true" : "?removeProxies=true";
      HttpRequest getRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(getApiUrl() + "/imposters" + query))
              .header("Accept", "application/json")
              .GET()
              .build();

      client
          .sendAsync(getRequest, HttpResponse.BodyHandlers.ofInputStream())
          .thenApply(HttpResponse::body)
          .thenApply(
              body ->
                  new ReplacingInputStream(
                      body, "\"recordRequests\": false", "\"recordRequests\": true"))
          .thenAccept(
              body -> {
                try (body) {
                  Files.createDirectories(impostersOutput.getParent());
                  Files.copy(body, impostersOutput, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                  throw new RuntimeException(
                      "failed to write imposters output to " + impostersOutput, e);
                }
              })
          .join();
    }
  }

  public abstract String getApiUrl();

  public abstract String getImposterAuthority(int imposterPort) throws IllegalArgumentException;

  public abstract Collection<Integer> getImposterPorts();
}
