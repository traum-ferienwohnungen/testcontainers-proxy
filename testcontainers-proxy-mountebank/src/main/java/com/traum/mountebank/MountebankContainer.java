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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class MountebankContainer extends GenericContainer<MountebankContainer> {

  static final int MOUNTEBANK_API_PORT = 2525;
  static final int DEFAULT_PROXY_PORT = MOUNTEBANK_API_PORT * 2;

  private final List<Integer> imposterPorts;

  public MountebankContainer(Integer... imposterPorts) {
    this(new Slf4jLogConsumer(LoggerFactory.getLogger(MountebankContainer.class)), imposterPorts);
  }

  public MountebankContainer(Logger logger, Integer... imposterPorts) {
    this(new Slf4jLogConsumer(logger), imposterPorts);
  }

  public MountebankContainer(Consumer<OutputFrame> logConsumer, Integer... imposterPorts) {
    super("andyrbell/mountebank");
    this.imposterPorts = imposterPorts.length == 0 ? List.of(DEFAULT_PROXY_PORT) : List.of(imposterPorts);

    final List<Integer> arrayList = new ArrayList<>(this.imposterPorts.size() + 1);
    arrayList.add(MOUNTEBANK_API_PORT);
    arrayList.addAll(this.imposterPorts);

    withReuse(true);
    withExposedPorts(arrayList.toArray(Integer[]::new));
    withCommand("mb", "--debug");
    withLogConsumer(logConsumer);
    waitingFor(Wait.forLogMessage(".*now taking orders.*", 1));
  }

  public List<Integer> getImposterPorts() {
    return imposterPorts;
  }
}
