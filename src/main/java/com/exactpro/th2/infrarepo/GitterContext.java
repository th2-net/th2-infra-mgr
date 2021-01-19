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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GitterContext implements GitConfig {
    private final String remoteRepository;
    private final String httpAuthUsername;
    private final String httpAuthPassword;
    private final String localRepositoryRoot;
    private final boolean ignoreInsecureHosts;
    private final String privateKeyFile;
    private final byte[] privateKey;

    private volatile Map<String, Gitter> gitters;
    private static volatile Map<GitterContext, GitterContext> contexts;

    /**
     * Returns context instance that can be used for further operations with repository
     * specified in configuration parameter.
     * @param config Configuration object describing remote repository and its credentials
     * @return Context to use for repository operations
     */
    public static GitterContext getContext(GitConfig config) {

        if (contexts == null) {
            synchronized (GitterContext.class) {
                if (contexts == null)
                    contexts = new ConcurrentHashMap<>();
            }
        }

        GitterContext key = new GitterContext(config);
        return contexts.computeIfAbsent(key, k -> {
            key.gitters = new ConcurrentHashMap<>();
            return key;
        });
    }


    /**
     * Returns instance of the Gitter configured to work with specified branch for
     * repository for which this context is created
     * <p>
     * None of the update operations are thread-safe and operations should be
     * synchronized using Gitter's internal lock objects
     *
     * <pre>
     * {@code
     * GitterContext ctx = GitterContext.getContext(config);
     * Gitter gitter = ctx.getBranch(branch);
     * gitter.lock()
     * try {
     *     // do operations on repository
     * } finally {
     *     gitter.unlock();
     * }
     * }
     * </pre>
     * @param branch Name of the branch in repository, for which consecutive operations will be performed
     *               on this instance
     * @return Gitter object configured to work with the branch
     */
    public Gitter getGitter(String branch) {
        return gitters.computeIfAbsent(branch, k -> new Gitter(this, k));
    }


    /**
     * Returns list of branches known at remote repository for which this context object is created
     * @return Set of strings, containing branch names in remote repository
     * @throws Exception
     */
    public Set<String> getBranches() throws Exception {
        return Gitter.getBranches(this);
    }


    /**
     * Retrieves latest commit refs for all branches from remote repository
     * @return Map, whose keys are branch names and values are commitRefs for these branches
     * @throws Exception
     */
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
                Arrays.equals(this.privateKey, c.privateKey);
    }

    @Override
    public int hashCode() {
        return (localRepositoryRoot.hashCode() + ":" +remoteRepository.hashCode()).hashCode();
    }
}
