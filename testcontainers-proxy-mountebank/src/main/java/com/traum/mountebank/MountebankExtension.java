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

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class MountebankExtension implements
        BeforeEachCallback, AfterEachCallback,
        BeforeAllCallback, AfterAllCallback,
        ParameterResolver {

    private static final String STORE_KEY_PROXY = "proxy";
    private static final String STORE_KEY_OUTPUT = "output";

    private MountebankProxy externalProxy;

    private MountebankProxy classProxy;
    private Future<MountebankProxy> classProxyLauncher;

    private static final Pattern placeholderPattern = Pattern.compile("\\{([^}]+)\\}");

    private Map<String, Function<ExtensionContext, String>> placeholders = Map.of(
            "method.name", context -> context.getTestMethod().map(Method::getName).orElseThrow(),
            "class.name", context -> context.getTestClass().map(Class::getName).map(name -> name.replace('$', '.')).orElseThrow()
    );

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public void setExternalProxy(MountebankProxy externalProxy) {
        this.externalProxy = externalProxy;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (externalProxy == null) {
            context.getTestClass().map(testClass ->
                    Arrays.stream(testClass.getDeclaredMethods())
                            .filter(testMethod -> testMethod.getAnnotation(WithProxy.class) != null)
                            .collect(Collectors.toList()))
                    .filter(Predicate.not(List::isEmpty))
                    .ifPresent(proxiedMethods -> {
                        classProxy = createProxy();
                        classProxyLauncher = executor.submit(() -> {
                            classProxy.start();
                            return classProxy;
                        });
                    });
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (classProxy != null) {
            classProxy.stop();
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        getAnnotation(extensionContext::getTestMethod)
                .ifPresent(annotation -> {
                    final Optional<Path> replayImposters = getPath(extensionContext, WithProxy::replayImposters);
                    final Path importImposters = getPath(extensionContext, WithProxy::initialImposters)
                            .filter(Files::exists)
                            .map(initialPath -> replayImposters
                                    .filter(replayPath -> {
                                        try {
                                            if (Files.exists(replayPath)) {
                                                final WithProxy.InitPolicy initPolicy = getInitPolicy(extensionContext, WithProxy::initPolicy);

                                                if (initPolicy == WithProxy.InitPolicy.IF_REPLAY_OUTDATED) {
                                                    return Files.getLastModifiedTime(replayPath).compareTo(Files.getLastModifiedTime(initialPath)) > 0;
                                                }
                                                return initPolicy == WithProxy.InitPolicy.IF_REPLAY_NONEXISTENT;
                                            }
                                            return false;
                                        } catch (IOException e) {
                                            throw new IllegalArgumentException("failed to compare modification times", e);
                                        }
                                    })
                                    .orElse(initialPath)
                            )
                            .orElseGet(() -> replayImposters
                                    .orElseThrow(() -> new IllegalArgumentException("neither initial nor replay imposters are given or existent")));

                    try {
                        final ExtensionContext.Store store = extensionContext.getStore(ExtensionContext.Namespace.create(annotation));
                        replayImposters.filter(Predicate.not(importImposters::equals)).ifPresent(outputPath -> store.put(STORE_KEY_OUTPUT, outputPath));

                        final MountebankProxy proxy = getProxy();
                        proxy.start();
                        proxy.importImposters(importImposters);

                        store.put(STORE_KEY_PROXY, proxy);
                    } catch (IOException | ExecutionException | InterruptedException e) {
                        throw new RuntimeException("failed to inspect file", e);
                    }
                });
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        getAnnotation(extensionContext::getTestMethod)
                .ifPresent(annotation -> {
                    final ExtensionContext.Store store = extensionContext.getStore(ExtensionContext.Namespace.create(annotation));
                    final Path impostersOutput = (Path) store.get(STORE_KEY_OUTPUT);
                    final MountebankProxy proxy = (MountebankProxy) store.get(STORE_KEY_PROXY);

                    if (proxy != null) {
                        if (impostersOutput != null) {
                            proxy.saveImposters(impostersOutput, true);
                        }

                        getPath(extensionContext, WithProxy::recordImposters).ifPresent(path -> {
                            proxy.saveImposters(path, false);
                        });

                        if (proxy != classProxy && proxy != externalProxy) {
                            proxy.stop();
                        }
                    }
                });
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return getAnnotation(extensionContext::getTestMethod)
                .map(annotation -> parameterContext.getParameter().getType().equals(MountebankProxy.class))
                .orElse(false);
    }

    @Override
    public MountebankProxy resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return getAnnotation(extensionContext::getTestMethod)
                .map(ExtensionContext.Namespace::create)
                .map(extensionContext::getStore)
                .map(store -> (MountebankProxy) store.get(STORE_KEY_PROXY))
                .orElseThrow();
    }

    private MountebankProxy getProxy() throws ExecutionException, InterruptedException {
        return externalProxy != null ? externalProxy : (classProxy != null ? classProxyLauncher.get() : createProxy());
    }

    private MountebankProxy createProxy() {
        return new MountebankProxy();
    }

    private String replacePlaceholders(ExtensionContext extensionContext, String path) {
        final Matcher matcher = placeholderPattern.matcher(path);
        return matcher.replaceAll(result -> {
            final String placeholder = result.group(1);
            final Function<ExtensionContext, String> replacer = placeholders.get(placeholder);
            if (replacer == null) {
                throw new IllegalArgumentException("unknown placeholder " + placeholder);
            }
            return replacer.apply(extensionContext);
        });
    }

    private WithProxy.InitPolicy getInitPolicy(ExtensionContext extensionContext, Function<WithProxy, WithProxy.InitPolicy> annotationProperty) {
        return getAnnotation(extensionContext::getTestMethod)
                .map(annotationProperty)
                .or(() -> getAnnotation(extensionContext::getTestClass)
                        .map(annotationProperty))
                .orElseThrow();
    }

    private Optional<Path> getPath(ExtensionContext extensionContext, Function<WithProxy, String> annotationProperty) {
        return getAnnotation(extensionContext::getTestMethod)
                .map(annotationProperty)
                .filter(Predicate.not(String::isBlank))
                .or(() -> getAnnotation(extensionContext::getTestClass)
                        .map(annotationProperty)
                        .filter(Predicate.not(String::isBlank)))
                .map(path -> replacePlaceholders(extensionContext, path))
                .map(Paths::get);
    }

    private Optional<WithProxy> getAnnotation(Supplier<Optional<? extends AnnotatedElement>> supplier) {
        return supplier.get().map(method -> method.getAnnotation(WithProxy.class));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface WithProxy {


        /**
         * Path to ejs/json file.
         * Used to initialize mountebank unless {@link #replayImposters()} is set or is outdated
         * (depending on {@link #initPolicy()}).
         */
        String initialImposters() default "";

        /**
         * Path to ejs/json file.
         * If used with {@link #initialImposters()} this file is overridden with re-play-ready imposters
         * previously recorded by the mountebank proxy.
         */
        String replayImposters() default "";

        /**
         * Path to ejs/json file.
         * If given {@link #replayImposters()} is configured to record requests and the output is stored
         * under the given path.
         */
        String recordImposters() default "";

        /**
         * Determines if {@link #replayImposters()} is used.
         */
        InitPolicy initPolicy() default InitPolicy.IF_REPLAY_NONEXISTENT;

        enum InitPolicy {
            /**
             * Always use {@link #initialImposters()}.
             */
            ALWAYS,

            /**
             * Only use if {@link #replayImposters()} is nonexistent.
             */
            IF_REPLAY_NONEXISTENT,

            /**
             * Only use if {@link #replayImposters()} is older than {@link #initialImposters()}.
             */
            IF_REPLAY_OUTDATED
        }
    }
}
