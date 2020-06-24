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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class MountebankExtension implements
        BeforeEachCallback, AfterEachCallback,
        ParameterResolver {

    public static final String EXTERNAL_PROXY_PROPERTY_PREFIX = "mountebank.external.proxy";
    public static final String EXTERNAL_PROXY_API_URL_PROPERTY = EXTERNAL_PROXY_PROPERTY_PREFIX + ".api.url";

    private static final String STORE_KEY_OUTPUT = "output";
    private static final String STORE_KEY_PROXY = "proxy";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern EXTERNAL_PROXY_IMPOSTER_AUTHORITIES = Pattern.compile(Pattern.quote(EXTERNAL_PROXY_PROPERTY_PREFIX) + "\\.(\\d+)\\.authority");

    private final Map<String, Function<ExtensionContext, String>> placeholders = Map.of(
            "method.name", context -> context.getTestMethod().map(Method::getName).orElseThrow(),
            "class.name", context -> context.getTestClass().map(Class::getName).map(name -> name.replace('$', '.')).orElseThrow()
    );

    private final MountebankProxyFactory factory;

    public MountebankExtension(MountebankProxyFactory factory) {
        this.factory = factory;
    }

    public MountebankExtension() {
        this(new MountebankProxyFactory() {
        });
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        getAnnotation(extensionContext::getTestMethod).ifPresent(annotation -> {
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
                                    throw new IllegalArgumentException("Failed to compare modification times", e);
                                }
                            })
                            .orElse(initialPath)
                    )
                    .orElseGet(() -> replayImposters
                            .orElseThrow(() -> new IllegalArgumentException("Neither initial nor replay imposters are given or existent")));

            getProxy(extensionContext, true)
                    .ifPresent(proxy -> {
                        final Store store = getStore(extensionContext);
                        replayImposters.filter(Predicate.not(importImposters::equals))
                                .ifPresent(outputPath -> store.put(STORE_KEY_OUTPUT, outputPath));

                        try {
                            if (!proxy.isRunning()) {
                                proxy.start();
                            }
                            proxy.importImposters(importImposters);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to inspect file", e);
                        }
                    });
        });
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        getProxy(extensionContext, false)
                .ifPresent(proxy -> {
                    final ExtensionContext.Store store = getStore(extensionContext);
                    final Path impostersOutput = (Path) store.get(STORE_KEY_OUTPUT);

                    if (impostersOutput != null) {
                        proxy.saveImposters(impostersOutput, true);
                    }

                    getPath(extensionContext, WithProxy::recordImposters).ifPresent(path -> {
                        proxy.saveImposters(path, false);
                    });

                    getProxy(extensionContext, STORE_KEY_PROXY).ifPresent(MountebankProxy::stop);
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
        return getProxy(extensionContext, false).orElseThrow();
    }

    private Optional<MountebankProxy> getProxy(ExtensionContext extensionContext, boolean create) {
        return getProxy(extensionContext, STORE_KEY_PROXY)
                .or(() -> {
                    if (create) {
                        final MountebankProxy proxy = factory.create();
                        getStore(extensionContext).put(STORE_KEY_PROXY, proxy);
                        return Optional.of(proxy);
                    }
                    return Optional.empty();
                });
    }

    private Optional<MountebankProxy> getProxy(ExtensionContext context, String key) {
        final Store store = getStore(context);

        return Optional.ofNullable((MountebankProxy) store.get(key))
                .or(() -> context.getParent().flatMap(parentContext -> getProxy(parentContext, key)));
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.GLOBAL);
    }

    private String replacePlaceholders(ExtensionContext extensionContext, String path) {
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(path);
        return matcher.replaceAll(result -> {
            final String placeholder = result.group(1);
            final Function<ExtensionContext, String> replacer = placeholders.get(placeholder);
            if (replacer == null) {
                throw new IllegalArgumentException("Unknown placeholder " + placeholder);
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
         * Path to ejs/json file. Used to initialize mountebank unless {@link #replayImposters()} is set or is outdated (depending on {@link #initPolicy()}).
         */
        String initialImposters() default "";

        /**
         * Path to ejs/json file. If used with {@link #initialImposters()} this file is overridden with re-play-ready imposters previously recorded by the mountebank proxy.
         */
        String replayImposters() default "";

        /**
         * Path to ejs/json file. If given {@link #replayImposters()} is configured to record requests and the output is stored under the given path.
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
            IF_REPLAY_OUTDATED;
        }

    }

    public interface MountebankProxyFactory {

        default MountebankProxy create() {
            if (System.getProperties().containsKey(EXTERNAL_PROXY_API_URL_PROPERTY)) {
                final HashMap<Integer, String> imposterAuthorities = Collections.list(System.getProperties().keys()).stream()
                        .map(key -> EXTERNAL_PROXY_IMPOSTER_AUTHORITIES.matcher((CharSequence) key))
                        .filter(Matcher::find)
                        .map(matcher -> Map.entry(Integer.parseInt(matcher.group(1)), System.getProperties().getProperty(matcher.group(0))))
                        .reduce(new HashMap<>(), (identity, entry) -> {
                            identity.put(entry.getKey(), entry.getValue());
                            return identity;
                        }, (a, b) -> {
                            a.putAll(b);
                            return a;
                        });

                return new ExternalMountebankProxy(System.getProperty(EXTERNAL_PROXY_API_URL_PROPERTY), imposterAuthorities);
            }
            return new ContainerMountebankProxy();
        }

    }

}







