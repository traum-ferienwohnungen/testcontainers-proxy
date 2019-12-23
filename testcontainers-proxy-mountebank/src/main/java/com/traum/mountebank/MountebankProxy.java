package com.traum.mountebank;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.traum.io.ReplacingInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MountebankProxy {

    private static final int DEFAULT_PROXY_PORT = MountebankContainer.MOUNTEBANK_API_PORT * 2;

    private final MountebankContainer container;

    private final HttpClient client = HttpClient.newBuilder().build();

    public MountebankProxy() {
        this(new MountebankContainer(DEFAULT_PROXY_PORT));
    }

    public MountebankProxy(MountebankContainer container) {
        this.container = container;
    }

    public MountebankContainer getContainer() {
        return container;
    }

    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    public void importImposters(Path impostersInput) throws IOException {
        final String mountebankUri = getApiUrl();

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(mountebankUri + "/imposters"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofFile(impostersInput))
                .build();

        client.sendAsync(postRequest, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, response.body()))
                .join();
    }

    public void saveImposters(Path impostersOutput, boolean forReplay) {
        if (impostersOutput != null) {
            final String query = forReplay ? "?removeProxies=true&replayabe=true" : "?removeProxies=true";
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(getApiUrl() + "/imposters" + query))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            client.sendAsync(getRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> new ReplacingInputStream(body, "\"recordRequests\": false", "\"recordRequests\": true"))
                    .thenAccept(body -> {
                        try (body) {
                            Files.createDirectories(impostersOutput.getParent());
                            Files.copy(body, impostersOutput, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException("failed to write imposters output to " + impostersOutput, e);
                        }
                    }).join();
        }
    }

    private String getApiUrl() {
        return getUrl("http", MountebankContainer.MOUNTEBANK_API_PORT);
    }

    public String getUrl() {
        return getUrl("http", DEFAULT_PROXY_PORT);
    }

    public String getUrl(String protocol, int internalPort) {
        return protocol + "://" + container.getContainerIpAddress() + ":" + container.getMappedPort(internalPort);
    }
}
