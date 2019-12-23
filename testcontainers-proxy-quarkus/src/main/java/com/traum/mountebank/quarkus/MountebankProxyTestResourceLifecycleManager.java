package com.traum.mountebank.quarkus;

import java.util.Arrays;
import java.util.Map;

import com.traum.mountebank.MountebankExtension;
import com.traum.mountebank.MountebankProxy;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class MountebankProxyTestResourceLifecycleManager implements QuarkusTestResourceLifecycleManager {

    protected final MountebankProxy proxy = new MountebankProxy();

    @Override
    public void inject(Object testInstance) {
        Arrays.stream(testInstance.getClass().getFields())
                .filter(field -> field.getType().equals(MountebankExtension.class))
                .forEach(field -> {
                    try {
                        ((MountebankExtension) field.get(testInstance)).setExternalProxy(proxy);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("failed to set proxy", e);
                    }
                });

    }

    @Override
    public Map<String, String> start() {
        proxy.getContainer().start();
        return Map.of();
    }

    @Override
    public void stop() {
        proxy.getContainer().stop();
    }
}
