/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infrarepo;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class GitterContext implements GitConfig {
    private String remoteRepository;
    private String httpAuthUsername;
    private String httpAuthPassword;
    private String localRepositoryRoot;
    private boolean ignoreInsecureHosts;
    private String privateKeyFile;
    private byte[] privateKey;

    private volatile Map<String, Gitter> instance;
    private static volatile Map<GitterContext, GitterContext> contexts;

    /**
     * Returns GitterContext object that can be used for further operations with repository
     * specified in configuration object.
     * @param config Configuration object describing remote repository and its credentials
     * @return Context to use for repository operations
     */
    public static GitterContext getContext(GitConfig config) {

        if (contexts == null) {
            synchronized (GitterContext.class) {
                if (contexts == null)
                    contexts = new TreeMap<>();
            }
        }

        GitterContext key = new GitterContext(config);
        GitterContext ctx;
        synchronized (contexts) {
            ctx = contexts.computeIfAbsent(key, k -> {
                key.instance = new ConcurrentHashMap<>();
                return key;
            });
        }
        return ctx;
    }


    /**
     * Returns instance of the object configured to work with specified branch.
     * <p>
     * None of the update operations are thread-safe and operations should be
     * synchronized using Gitter's internal lock objects
     *
     * <pre>
     * {@code
     * Gitter gitter = Gitter.getBranch(config, branch);
     * gitter.lock()
     * try {
     *     // do operations on repository
     * } finally {
     *     gitter.unlock();
     * }
     * }
     * </pre>
     * @param branch Repository branch, for which consecutive operations will be performed
     *               on this instance
     * @return
     */
    public Gitter getGitter(String branch) {
        return instance.computeIfAbsent(branch, k -> new Gitter(this, k));
    }


    public Set<String> getBranches() throws Exception {
        return Gitter.getBranches(this);
    }


    public Map<String, String> getAllBranchesCommits() throws Exception {
        return Gitter.getAllBranchesCommits(this);
    }


    private GitterContext(GitConfig config) {
        remoteRepository = config.getRemoteRepository();
        httpAuthUsername = config.getHttpAuthUsername();
        httpAuthPassword = config.getHttpAuthPassword();
        localRepositoryRoot = config.getLocalRepositoryRoot();
        ignoreInsecureHosts = config.ignoreInsecureHosts();
        privateKeyFile = config.getPrivateKeyFile();
        privateKey = config.getPrivateKey();
    }

    @Override
    public String getRemoteRepository() {
        return remoteRepository;
    }

    @Override
    public String getHttpAuthUsername() {
        return httpAuthUsername;
    }

    @Override
    public String getHttpAuthPassword() {
        return httpAuthPassword;
    }

    @Override
    public boolean ignoreInsecureHosts() {
        return ignoreInsecureHosts;
    }

    @Override
    public String getLocalRepositoryRoot() {
        return localRepositoryRoot;
    }

    @Override
    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    @Override
    public byte[] getPrivateKey() {
        return privateKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (! (o instanceof GitterContext))
            return false;
        GitterContext c = (GitterContext) o;
        return  this.ignoreInsecureHosts == c.ignoreInsecureHosts &&
                Objects.equals(this.localRepositoryRoot, c.localRepositoryRoot) &&
                Objects.equals(this.remoteRepository, c.remoteRepository) &&
                Objects.equals(this.httpAuthUsername, c.httpAuthUsername) &&
                Objects.equals(this.httpAuthPassword, c.httpAuthUsername) &&
                Objects.equals(this.privateKeyFile, c.privateKeyFile) &&
                Objects.equals(this.privateKey, c.privateKey);
    }
}
