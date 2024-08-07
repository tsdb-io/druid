/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.k8s.discovery;

import com.fasterxml.jackson.databind.Module;
import com.google.inject.Binder;
import com.google.inject.Key;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import org.apache.druid.client.coordinator.Coordinator;
import org.apache.druid.client.indexing.IndexingService;
import org.apache.druid.discovery.DruidLeaderSelector;
import org.apache.druid.discovery.DruidNodeAnnouncer;
import org.apache.druid.discovery.DruidNodeDiscoveryProvider;
import org.apache.druid.guice.JsonConfigProvider;
import org.apache.druid.guice.LazySingleton;
import org.apache.druid.guice.PolyBind;
import org.apache.druid.initialization.DruidModule;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class K8sDiscoveryModule implements DruidModule
{
  private static final String K8S_KEY = "k8s";

  @Override
  public List<? extends Module> getJacksonModules()
  {
    return Collections.emptyList();
  }

  @Override
  public void configure(Binder binder)
  {
    JsonConfigProvider.bind(binder, "druid.discovery.k8s", K8sDiscoveryConfig.class);

    binder.bind(ApiClient.class)
          .toProvider(
              () -> {
                try {
                  // Note: we can probably improve things here about figuring out how to find the K8S API server,
                  // HTTP client timeouts etc.
                  return Config.defaultClient();
                }
                catch (IOException ex) {
                  throw new RuntimeException("Failed to create K8s ApiClient instance", ex);
                }
              }
          )
          .in(LazySingleton.class);

    binder.bind(K8sApiClient.class).to(DefaultK8sApiClient.class).in(LazySingleton.class);
    binder.bind(K8sLeaderElectorFactory.class).to(DefaultK8sLeaderElectorFactory.class).in(LazySingleton.class);

    PolyBind.optionBinder(binder, Key.get(DruidNodeDiscoveryProvider.class))
            .addBinding(K8S_KEY)
            .to(K8sDruidNodeDiscoveryProvider.class)
            .in(LazySingleton.class);

    PolyBind.optionBinder(binder, Key.get(DruidNodeAnnouncer.class))
            .addBinding(K8S_KEY)
            .to(K8sDruidNodeAnnouncer.class)
            .in(LazySingleton.class);

    PolyBind.optionBinder(binder, Key.get(DruidLeaderSelector.class, Coordinator.class))
            .addBinding(K8S_KEY)
            .toProvider(
                K8sDruidLeaderSelectorProvider.K8sCoordinatorDruidLeaderSelectorProvider.class
            )
            .in(LazySingleton.class);

    PolyBind.optionBinder(binder, Key.get(DruidLeaderSelector.class, IndexingService.class))
            .addBinding(K8S_KEY)
            .toProvider(
                K8sDruidLeaderSelectorProvider.K8sIndexingServiceDruidLeaderSelectorProvider.class
            )
            .in(LazySingleton.class);
  }
}
