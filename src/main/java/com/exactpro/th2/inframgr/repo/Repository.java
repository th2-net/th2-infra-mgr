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
package com.exactpro.th2.inframgr.repo;

import com.exactpro.th2.inframgr.models.ResourceEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public class Repository {

    private static ResourceEntry loadYMLFile(File file) throws IOException {

        String contents = Files.readString(file.toPath());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        RepositoryResource resource = mapper.readValue(contents, RepositoryResource.class);

        ResourceEntry entry = new ResourceEntry();
        entry.setKind(ResourceType.forKind(resource.getKind()));
        entry.setName(resource.getMetadata().getName());
        entry.setSpec(resource.getSpec());

        entry.setSourceHash(Repository.digest(contents));

        return entry;
    }

    private static Set<ResourceEntry> loadBranchYMLFiles(File repositoryRoot) throws IOException {

        Logger logger = LoggerFactory.getLogger(Repository.class);

        Set<ResourceEntry> resources = new HashSet<>();
        for (ResourceType t : ResourceType.values())
            if (t.isRepositoryResource()) {
                File dir = new File(repositoryRoot.getAbsolutePath() + "/" + t.path());
                if (dir.exists()) {

                    if (!dir.isDirectory()) {
                        logger.error("entry expected to be a directory: {}", dir.getAbsoluteFile());
                        continue;
                    }

                    File[] files = dir.listFiles();
                    for (File f : files) {
                        if (f.isFile() && (f.getAbsolutePath().endsWith(".yml") || f.getAbsolutePath().endsWith(".yaml"))) {
                            ResourceEntry resourceEntry = Repository.loadYMLFile(f);

                            if (resourceEntry.getKind() != t)
                                logger.error("skipping {} | resource is located in wrong directory. kind: {}, dir: {}", f.getAbsolutePath(), resourceEntry.getKind().kind(), t.path());

                            resources.add(resourceEntry);
                        }
                    }
            }
        }
        return resources;
    }

    private static File getFile(GitConfig config, String branch, ResourceEntry entry) {

        File file = new File (
                config.getLocalRepositoryRoot()
                        + "/" + branch
                        + "/" + entry.getKind().path()
                        + "/" + entry.getName()
                        + ".yml");
        return file;
    }


    /**
     * This method will checkout latest version from the repository
     * and will create RepositorySnapshot from it.
     *
     * @param  gitter
     *         Gitter object that will be used to checkout data from the repository.
     *         Must be locked externally as this method does not lock repository by itself
     *
     * @return Latest snapshot of repository
     *
     * @throws IOException
     *         If repository IO operation fails
     * @throws GitAPIException
     *         If git checkout operation fails
     */
    public static RepositorySnapshot getSnapshot(Gitter gitter) throws IOException, GitAPIException {

        String path = gitter.getConfig().getLocalRepositoryRoot() + "/" + gitter.getBranch();
        String commitRef = gitter.checkout();
        Set<ResourceEntry> resources = Repository.loadBranchYMLFiles(new File(path));

        RepositorySnapshot snapshot = new RepositorySnapshot(commitRef);
        snapshot.setResources(resources);
        return snapshot;
    }


    private static void saveYMLFile(File file, RepositoryResource resource) throws IOException {

        file.getParentFile().mkdir();
        ObjectMapper mapper = new ObjectMapper((new YAMLFactory())
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        String contents = mapper.writeValueAsString(resource);
        resource.setSourceHash(Repository.digest(contents));
        Files.writeString(file.toPath(), contents);
    }


    public static void add(GitConfig config, String branch, ResourceEntry entry) throws IOException {

        File file = getFile(config, branch, entry);
        if (file.exists())
            throw new IllegalArgumentException("resource already exist");
        RepositoryResource resource = entry.toRepositoryResource();
        Repository.saveYMLFile(file, resource);
        entry.setSourceHash(resource.getSourceHash());
    }

    public static void update(GitConfig config, String branch, ResourceEntry entry) throws IOException {

        File file = getFile(config, branch, entry);
        if (!file.exists() || !file.isFile())
            throw new IllegalArgumentException("resource does not exist");
        RepositoryResource resource = entry.toRepositoryResource();
        Repository.saveYMLFile(file, resource);
        entry.setSourceHash(resource.getSourceHash());
    }

    public static void remove(GitConfig config, String branch, ResourceEntry entry){

        File file = getFile(config, branch, entry);
        if (!file.exists() || !file.isFile())
            throw new IllegalArgumentException("resource does not exist");
        file.delete();
    }

    public static String digest(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest)
                sb.append(String.format("%02x", b));

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
