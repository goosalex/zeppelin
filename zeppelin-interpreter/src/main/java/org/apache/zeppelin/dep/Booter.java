/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.dep;

import org.apache.commons.lang.Validate;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.repository.Authentication;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import static java.net.Proxy.Type.DIRECT;

/**
 * Manage mvn repository.
 */
public class Booter {
  private static Logger logger = LoggerFactory.getLogger(Booter.class);

  public static RepositorySystem newRepositorySystem() {
    return RepositorySystemFactory.newRepositorySystem();
  }

  public static RepositorySystemSession newRepositorySystemSession(
      RepositorySystem system, String localRepoPath) {
    Validate.notNull(localRepoPath, "localRepoPath should have a value");

    MavenRepositorySystemSession session = new MavenRepositorySystemSession();

    LocalRepository localRepo = new LocalRepository(resolveLocalRepoPath(localRepoPath));
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(localRepo));

    if (logger.isDebugEnabled()) {
      session.setTransferListener(new TransferListener());
      session.setRepositoryListener(new RepositoryListener());
    }
    // uncomment to generate dirty trees
    // session.setDependencyGraphTransformer( null );

    return session;
  }

  static String resolveLocalRepoPath(String localRepoPath) {
    // todo decouple home folder resolution
    // find homedir
    String home = System.getenv("ZEPPELIN_HOME");
    if (home == null) {
      home = System.getProperty("zeppelin.home");
    }
    if (home == null) {
      home = "..";
    }

    return Paths.get(home).resolve(localRepoPath).toAbsolutePath().toString();
  }

  public static RemoteRepository newCentralRepository() {
    String mvnRepo = System.getenv("ZEPPELIN_INTERPRETER_DEP_MVNREPO");
    if (mvnRepo == null) {
      mvnRepo = System.getProperty("zeppelin.interpreter.dep.mvnRepo");
    }
    if (mvnRepo == null) {
      mvnRepo = "http://repo1.maven.org/maven2/";
    }

    RemoteRepository defaultRepo = new RemoteRepository("central", "default", mvnRepo);
    java.net.Proxy defaultProxy = null;
    try {
      URI mvnRepoURI = new URI(mvnRepo);
      Authentication a = null;
      defaultProxy = ProxySelector.getDefault().select(mvnRepoURI).get(0);

      switch (defaultProxy.type()) {
        case HTTP:
          if (!System.getProperty("http.proxyUser", "").isEmpty()) {
            a = new Authentication(
                System.getProperty("http.proxyUser", ""),
                System.getProperty("http.proxyPassword", "")
            );
          }
          defaultRepo.setProxy(
              new Proxy(mvnRepoURI.getScheme(),
                  ((InetSocketAddress) defaultProxy.address()).getHostName(),
                  ((InetSocketAddress) defaultProxy.address()).getPort(),
                  a)
          );
          return defaultRepo;
        case DIRECT:
          return defaultRepo;

      }

    } catch (URISyntaxException e) {
      logger.warn("Error finding proxy for default RemoteRepository %s: %s",
          mvnRepo, e.getMessage());
    }


    return defaultRepo;
  }

  public static RemoteRepository newLocalRepository() {
    return new RemoteRepository("local",
        "default", "file://" + System.getProperty("user.home") + "/.m2/repository");
  }
}
