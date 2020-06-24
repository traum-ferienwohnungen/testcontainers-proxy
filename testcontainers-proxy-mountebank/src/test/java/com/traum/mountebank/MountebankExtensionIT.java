package com.traum.mountebank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MountebankExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@MountebankExtension.WithProxy(
  recordImposters = "target/proxy/{class.name}-{method.name}-debug.json"
)
class MountebankExtensionIT {

  private static final String IMPOSTERS_OCTOCAT = "target/proxy/replay/github-users-octocat.json";
  private static final String RESPONSE_OCTOCAT = "target/proxy/github-users-octocat-response.json";
  private final HttpClient client = HttpClient.newBuilder().build();

  @BeforeAll
  static void cleanup() {
    Stream.of(IMPOSTERS_OCTOCAT, RESPONSE_OCTOCAT)
        .map(Path::of)
        .forEach(
            path -> {
              try {
                Files.deleteIfExists(path);
              } catch (IOException e) {
                throw new RuntimeException("failed to delete file " + path, e);
              }
            });
  }

  @Test
  @Order(1)
  @MountebankExtension.WithProxy(
    initialImposters = "src/test/resources/proxy/github-proxy.json",
    replayImposters = IMPOSTERS_OCTOCAT,
    initPolicy = MountebankExtension.WithProxy.InitPolicy.IF_REPLAY_NONEXISTENT
  )
  void initialize(MountebankProxy proxy) throws IOException {
    assertFalse(Files.exists(Paths.get(IMPOSTERS_OCTOCAT)));

    final String previousBody =
        GET(URI.create(
                "http://"
                    + proxy.getImposterAuthority(MountebankContainer.DEFAULT_PROXY_PORT)
                    + "/users/octocat"))
            .join();
    final Path responseBodyPath = Path.of(RESPONSE_OCTOCAT);
    Files.createDirectories(responseBodyPath.getParent());
    Files.write(
        responseBodyPath,
        previousBody.getBytes(),
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
  }

  @Test
  @Order(2)
  @MountebankExtension.WithProxy(
    initialImposters = "src/test/resources/proxy/github-proxy.json",
    replayImposters = IMPOSTERS_OCTOCAT,
    initPolicy = MountebankExtension.WithProxy.InitPolicy.IF_REPLAY_NONEXISTENT
  )
  void nowReplay(MountebankProxy proxy) throws IOException {
    assertTrue(Files.exists(Paths.get(IMPOSTERS_OCTOCAT)));

    final String body =
        GET(URI.create(
                "http://"
                    + proxy.getImposterAuthority(MountebankContainer.DEFAULT_PROXY_PORT)
                    + "/users/octocat"))
            .join();

    final String previousBody = Files.readString(Path.of(RESPONSE_OCTOCAT));
    assertEquals(body, previousBody);
  }

  private CompletableFuture<String> GET(URI uri) {
    HttpRequest getRequest =
        HttpRequest.newBuilder()
            .uri(uri)
            .header("Accept", "application/vnd.github.v3+json")
            .GET()
            .build();

    return client
        .sendAsync(getRequest, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            response -> {
              assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
              return response.body();
            });
  }
}
