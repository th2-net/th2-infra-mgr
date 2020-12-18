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

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.EntryExistsException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class Gitter {
    private static final Logger logger = LoggerFactory.getLogger(Gitter.class);
    public static final String REFS_HEADS = "refs/heads/";

    private static volatile Map<String, Gitter> instance = new ConcurrentHashMap<>();
    private GitConfig config;
    private String branch;
    private Lock lock;
    private TransportConfigCallback callback;
    private final String localCacheRoot;
    private final String repositoryDir;


    private Gitter(GitConfig config, String branch) {
        this.config = config;
        this.branch = branch;
        this.localCacheRoot = config.getLocalRepositoryRoot() + "/" + branch;
        this.repositoryDir = localCacheRoot + "/.git";
        this.callback = Gitter.transportConfigCallback(config);
        this.lock = new ReentrantLock();
    }

    public GitConfig getConfig() {
        return config;
    }

    public String getBranch() {
        return branch;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public static Gitter getBranch(GitConfig config, String branch) {

        return instance.computeIfAbsent(branch, k -> new Gitter(config, k));
    }

    private static TransportConfigCallback transportConfigCallback(GitConfig config) {

        if (config.getIsHttpAuth()){
            return transport -> {
                if (transport instanceof HttpTransport) {
                    HttpTransport httpTransport = (HttpTransport) transport;
                    httpTransport.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                            config.getHttpAuthUsername(),
                            config.getHttpAuthPassword()));
                } else {
                    logger.error("Http auth is enabled but manager is creating ssh connection, something is wrong with config");
                }
            };
        }

        if (config.ignoreInsecureHosts())
            JSch.setConfig("StrictHostKeyChecking", "no");
        SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);

                if (config.getPrivateKey() != null)
                    defaultJSch.addIdentity("backend-key", config.getPrivateKey(), null, null);
                else
                    if (config.getPrivateKeyFile() != null)
                        defaultJSch.addIdentity(config.getPrivateKeyFile());
                    else
                        throw  new IllegalArgumentException("repository private key not set");
                return defaultJSch;
            }
        };

        return transport -> {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(sshSessionFactory);
        };
    }


    public static Map<String, String> getAllBranchesCommits(GitConfig config) throws Exception {

        // retrieve all remote branches
        Collection<Ref> allBranches = Git.lsRemoteRepository()
                .setHeads(true)
                .setRemote(config.getRemoteRepository())
                .setTransportConfigCallback(Gitter.transportConfigCallback(config))
                .call();

        // filter, convert and normalize branch names
        Map<String, String> result = new HashMap<>();
        allBranches.stream()
                .filter(r -> r.getName().startsWith(REFS_HEADS))
                .forEach(r -> result.put(r.getName().substring(REFS_HEADS.length()), r.getObjectId().getName()));

        return result;
    }

    public static Set<String> getBranches(GitConfig config) throws Exception {

        Map<String, String> commits = getAllBranchesCommits(config);
        return commits.keySet();
    }

    private String checkout(GitConfig config, String branch, String targetDir) throws IOException, GitAPIException {

        // create branch directory if it does not exist
        File dir = new File(targetDir);
        if (!dir.exists() && !dir.mkdirs())
            throw new IOException(String.format("Error creating repository directory %s", targetDir));

        Repository repo = new FileRepository(repositoryDir);
        Git git = new Git(repo);

        // try to pull branch
        try {
            git
                    .pull()
                    .setStrategy(MergeStrategy.THEIRS)
                    .setTransportConfigCallback(callback)
                    .call();
        } catch (NoHeadException e) {
            // probably there is no git repository in the directory
            // try to clone
            Git.cloneRepository()
                    .setBranch(branch)
                    .setURI(config.getRemoteRepository())
                    .setDirectory(dir)
                    .setTransportConfigCallback(callback)
                    .call();
        } catch (WrongRepositoryStateException e) {
            // try to recreate local repository
            recreateCache();
        }

        Ref ref = git.checkout()
                .setName(branch)
                .setForced(true)
                .call();
        return ref.getObjectId().getName();
    }

    public String checkout() throws IOException, GitAPIException {

        return checkout(config, branch, localCacheRoot);
    }


    public String reset() throws InconsistentRepositoryStateException {

        checkAndGetLocalCacheRoot();

        try {
            Repository repo = new FileRepository(repositoryDir);
            Git git = new Git(repo);
            Ref ref = git.reset().setMode(ResetCommand.ResetType.HARD).call();
            return ref.getObjectId().getName();
        } catch (Exception e) {
            throw new InconsistentRepositoryStateException(
                    String.format("Exception resetting repository for branch \"%s\"", branch)
                    , e);
        }
    }


    public String recreateCache() throws IOException, GitAPIException {

        File rootDir = checkAndGetLocalCacheRoot();
        if (FileSystemUtils.deleteRecursively(rootDir))
            return checkout();
        else
            throw new IOException(String.format("Error deleting local repository cache for branch \"%s\"", branch));
    }


    public String commitAndPush(String message) throws IOException, GitAPIException, InconsistentRepositoryStateException {

        checkAndGetLocalCacheRoot();
        Repository repo = new FileRepository(repositoryDir);
        Git git = new Git(repo);
        if (git.status().call().isClean())
            return null;

        git.add()
                .setUpdate(true)
                .addFilepattern(".")
                .call();
        git.add()
                .addFilepattern(".")
                .call();

        try {
            String commitRef = git.commit()
                    .setMessage(message)
                    .call()
                    .getId()
                    .getName();

            String ref = Gitter.REFS_HEADS + branch;
            Iterator<PushResult> result = git
                    .push()
                    .add(ref)
                    .setForce(false)
                    .setTransportConfigCallback(transportConfigCallback(config))
                    .call()
                    .iterator();

            while (result.hasNext()) {
                PushResult pushResult = result.next();
                RemoteRefUpdate update = pushResult.getRemoteUpdate(ref);
                if (update != null) {

                    if (update.getStatus() == RemoteRefUpdate.Status.OK)
                        return commitRef;
                    else
                        throw new InconsistentRepositoryStateException (
                                String.format("Exception pushing branch \"%s\" to remote: %s"
                                        , branch, update.getStatus().name()));
                }
            }

            throw new InconsistentRepositoryStateException(
                    String.format("Cannot determine result of push command for branch \"%s\"", branch));

        } catch (InconsistentRepositoryStateException irse) {
            throw irse;
        } catch (Exception e) {
            throw new InconsistentRepositoryStateException(
                    String.format("Exception with commit and push for branch \"%s\"", branch), e);
        }
    }


    public String createBranch(String sourceBranch) throws Exception {

        Set<String> branches = Gitter.getBranches(config);
        if (!branches.contains(sourceBranch))
            throw new IllegalArgumentException("Source branch does not exists");
        if (branches.contains(branch))
            throw new EntryExistsException("Branch with such name already exists");

        try {
            checkout(config, sourceBranch, localCacheRoot);

            Repository repo = new FileRepository(repositoryDir);
            Git git = new Git(repo);
            git.branchCreate()
                    .setName(branch)
                    .call();
            Ref ref = git.checkout()
                    .setName(branch)
                    .call();
            git.push()
                    .add(ref)
                    .setTransportConfigCallback(callback)
                    .call();

            return ref.getObjectId().getName();
        } catch (Exception e) {
            if (!FileSystemUtils.deleteRecursively(new File(localCacheRoot)))
                throw new InconsistentRepositoryStateException(
                        String.format("Could not delete local repository cache %s", localCacheRoot), e);
            throw e;
        }
    }


    private File checkAndGetLocalCacheRoot() {

        File dir = new File(localCacheRoot);
        if (!dir.exists())
            throw new IllegalArgumentException("branch does not exist locally");
        return dir;
    }

}
