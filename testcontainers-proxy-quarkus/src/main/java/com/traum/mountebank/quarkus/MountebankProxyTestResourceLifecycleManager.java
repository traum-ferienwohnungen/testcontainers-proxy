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

import com.traum.mountebank.ContainerMountebankProxy;
import com.traum.mountebank.MountebankExtension;
import com.traum.mountebank.MountebankProxy;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

public class MountebankProxyTestResourceLifecycleManager implements QuarkusTestResourceLifecycleManager {

    private final ContainerMountebankProxy proxy;

    public MountebankProxyTestResourceLifecycleManager() {
        this(new Integer[0]);
    }

    public MountebankProxyTestResourceLifecycleManager(Integer...imposterPorts) {
        proxy = new ContainerMountebankProxy(imposterPorts);
    }

    @Override
    public Map<String, String> start() {
        proxy.start();
        System.setProperty(MountebankExtension.EXTERNAL_PROXY_API_URL_PROPERTY, proxy.getApiUrl());
        proxy.getContainer().getImposterPorts().forEach(port -> {
                    String key = MountebankExtension.EXTERNAL_PROXY_PROPERTY_PREFIX + "." + port + ".authority";
                    System.setProperty(key, proxy.getImposterAuthority(port));
                });
        return Map.of();
    }

    @Override
    public void stop() {
        proxy.stop();
    }

    protected MountebankProxy getProxy() {
        return proxy;
    }
}
