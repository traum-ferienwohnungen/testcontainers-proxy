package com.traum.mountebank;

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

import java.util.Map;

/** Facade for an externally managed instance of mountebank. */
public class ExternalMountebankProxy extends MountebankProxy {

  private final String apiUrl;
  private final Map<Integer, String> imposterAuthorities;

  public ExternalMountebankProxy(String apiUrl, Map<Integer, String> imposterAuthorities) {
    this.apiUrl = apiUrl;
    this.imposterAuthorities = imposterAuthorities;
  }

  @Override
  public String getApiUrl() {
    return apiUrl;
  }

  @Override
  public String getImposterAuthority(int imposterPort) throws IllegalArgumentException {
    if (imposterAuthorities.containsKey(imposterPort)) {
      return imposterAuthorities.get(imposterPort);
    }
    throw new IllegalArgumentException("No mapped authority for imposter port " + imposterPort);
  }
}
