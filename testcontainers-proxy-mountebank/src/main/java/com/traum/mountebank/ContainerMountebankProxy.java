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

public class ContainerMountebankProxy extends MountebankProxy {

    private final MountebankContainer container;

    public ContainerMountebankProxy() {
        this(new MountebankContainer(DEFAULT_PROXY_PORT));
    }

    public ContainerMountebankProxy(MountebankContainer container) {
        this.container = container;
    }

    public MountebankContainer getContainer() {
        return container;
    }

    @Override
    public void start() {
        container.start();
    }

    @Override
    public void stop() {
        container.stop();
    }

    @Override
    public boolean isRunning() {
        return container.isRunning();
    }

    @Override
    public String getApiUrl()  {
        return getUrl("http", MountebankContainer.MOUNTEBANK_API_PORT);
    }

    @Override
    public String getUrl() {
        return getUrl("http", DEFAULT_PROXY_PORT);
    }

    public String getUrl(String protocol, int internalPort) {
        return protocol + "://" + container.getContainerIpAddress() + ":" + container.getMappedPort(internalPort);
    }
}
