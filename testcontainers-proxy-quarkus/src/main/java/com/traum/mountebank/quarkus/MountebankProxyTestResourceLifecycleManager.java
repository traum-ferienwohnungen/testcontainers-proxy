package com.traum.mountebank.quarkus;

/*-
 * #%L
 * testcontainers-proxy-quarkus
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
