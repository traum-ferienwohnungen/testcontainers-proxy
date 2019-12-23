# Testing with Throwaway Proxies

This library is based on [testcontainers](https://www.testcontainers.org/)
which launches (docker)-containers providing test resources such as databases or 
– as in our case – proxy servers, to mock (potentially flaky) third party services.

The overall goal of this library is to simplify the process of recording and replaying
the responses of a third party service. It does so with a JUnit 5 extension
which processes launches an configures a proxy for every test annotated with `@WithProxy`.

## Installation

All library artifacts are released as github packages which can be found here:

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Apache Maven Packages</name>
        <url>https://maven.pkg.github.com/traum-ferienwohnungen/testcontainers-proxy</url>
    </repository>
</repositories>
```
This library assumes [`org.testcontainers:testcontainers`](https://mvnrepository.com/artifact/org.testcontainers/testcontainers) is available at runtime.
Therefore, the minimum set of dependencies looks as follows:

```xml
<dependencies>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>1.12.3</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.traum</groupId>
        <artifactId>testcontainers-proxy-mountebank</artifactId>
        <version>RELEASE</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

To get integration with [quarkus](https://quarkus.io/) add the following:

```xml
<dependency>
    <groupId>com.traum</groupId>
    <artifactId>testcontainers-proxy-quarkus</artifactId>
    <version>RELEASE</version>
    <scope>test</scope>
</dependency>
```

## General Usage with JUnit 5

The library provides a JUnit 5 extension which starts a ([mountebank](http://www.mbtest.org/)) proxy
for any method annotated with `@WithProxy`. That annotation can be configured:

* **`initialImposters`** path to JSON file with initial imposters which the proxy is
  initialized with unless `replayImposters` exists or is outdated (depending on `initPolicy`) 
* **`replayImposters`** path to JSON file which the recorded request-response-pairs are written to **ready for replay**
* **`recordImposters`** path to JSON file which the recorded request-response-pairs are written to **with debugging details**
* **`initPolicy`** the policy determining which set of imposters are used to initialize the proxy with

All paths, `initialImposters`, `replayImposters`, and `recordImposters`, may contain placeholders, namely `{method.name}` and `{class.name}`,
which will be replaced with the test-method name and test-class name, respectively.

If `@WithProxy` applied a class-level, the properties act as defaults at method-level.
However, you still have to explicitly annotate any test-method which needs a proxy. 

See [MountebankExtensionIT.java](testcontainers-proxy-mountebank/src/test/java/com/traum/mountebank/MountebankExtensionIT.java)

```java
@ExtendWith(MountebankExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@MountebankExtension.WithProxy(recordImposters = "target/proxy/{class.name}-{method.name}-debug.json")
class MountebankExtensionIT {

    private static final String IMPOSTERS_OCTOCAT = "target/proxy/replay/github-users-octocat.json";
    private static final String RESPONSE_OCTOCAT = "target/proxy/github-users-octocat-response.json";
    private final HttpClient client = HttpClient.newBuilder().build();

    @Test
    @Order(1)
    @MountebankExtension.WithProxy(
            initialImposters = "src/test/resources/proxy/github-proxy.json",
            replayImposters = IMPOSTERS_OCTOCAT,
            initPolicy = MountebankExtension.WithProxy.InitPolicy.IF_REPLAY_NONEXISTENT)
    void initialize(MountebankProxy proxy) throws IOException {
        assertFalse(Files.exists(Paths.get(IMPOSTERS_OCTOCAT)));

        final String previousBody = GET(URI.create(proxy.getUrl() + "/users/octocat")).join();
        final Path responseBodyPath = Path.of(RESPONSE_OCTOCAT);
        Files.createDirectories(responseBodyPath.getParent());
        Files.write(responseBodyPath, previousBody.getBytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Test
    @Order(2)
    @MountebankExtension.WithProxy(
            initialImposters = "src/test/resources/proxy/github-proxy.json",
            replayImposters = IMPOSTERS_OCTOCAT,
            initPolicy = MountebankExtension.WithProxy.InitPolicy.IF_REPLAY_NONEXISTENT)
    void nowReplay(MountebankProxy proxy) throws IOException {
        assertTrue(Files.exists(Paths.get(IMPOSTERS_OCTOCAT)));

        final String body = GET(URI.create(proxy.getUrl() + "/users/octocat")).join();

        final String previousBody = Files.readString(Path.of(RESPONSE_OCTOCAT));
        assertEquals(body, previousBody);
    }

    private CompletableFuture<String> GET(URI uri) {
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();

        return client.sendAsync(getRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
                    return response.body();
                });
    }

    @BeforeAll
    static void cleanup() {
        Stream.of(IMPOSTERS_OCTOCAT, RESPONSE_OCTOCAT).map(Path::of).forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new RuntimeException("failed to delete file " + path, e);
            }
        });
    }
}
```

### Quarkus Test Resource

First, extend `MountebankProxyTestResourceLifecycleManager` and override `start()` to obtain the proxy URL:

```java
public class ProxyTestResourceLifecycleManager extends MountebankProxyTestResourceLifecycleManager {
    
    @Override
    public Map<String, String> start() {
        super.start();
           
         // set quarkus application property
        return Map.of("quarkus.property", this.proxy.getUrl());
    }

}
```

Then, use the class through `@QuarkusTestResource`:

```java
@QuarkusTestResource(ProxyTestResourceLifecycleManager.class)
class QuarkusTest {
    
    @RegisterExtension
    MountebankExtension mountebank = new MountebankExtension();

    @Test
    @MountebankExtension.WithProxy(initialImposters = "path/to/initial-imposters.json")
    void test() {
        given()
          .when().get("/resource-which-calls-proxied-service")
          .then()
             .statusCode(200)
             .body(is("(proxied) response"));
    }
}
```
