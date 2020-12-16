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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Repository {

    private static RepositoryResource loadYAML(File file) throws IOException {

        String contents = Files.readString(file.toPath());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        RepositoryResource resource = mapper.readValue(contents, RepositoryResource.class);
        resource.setSourceHash(Repository.digest(contents));

        return resource;
    }


    private static void saveYAML(File file, RepositoryResource resource) throws IOException {

        file.getParentFile().mkdir();
        ObjectMapper mapper = new ObjectMapper((new YAMLFactory())
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        String contents = mapper.writeValueAsString(resource);
        resource.setSourceHash(Repository.digest(contents));
        Files.writeString(file.toPath(), contents);
    }


    private static Set<RepositoryResource> loadBranch(File repositoryRoot) throws IOException {

        Logger logger = LoggerFactory.getLogger(Repository.class);

        Set<RepositoryResource> resources = new HashSet<>();
        Map<String, RepositoryResource> firstOccurrences = new HashMap<>();

        for (ResourceType t : ResourceType.values())
            if (t.isRepositoryResource()) {
                File dir = new File(repositoryRoot.getAbsolutePath() + "/" + t.path());
                if (dir.exists()) {

                    if (!dir.isDirectory()) {
                        logger.error("entry expected to be a directory: \"{}\"", dir.getAbsoluteFile());
                        continue;
                    }

                    File[] files = dir.listFiles();
                    if (files == null)
                        return resources;

                    for (File f : files)
                        if (f.isFile() && (f.getAbsolutePath().endsWith(".yml") || f.getAbsolutePath().endsWith(".yaml")))
                            try {
                                RepositoryResource resource = Repository.loadYAML(f);
                                RepositoryResource.Metadata meta = resource.getMetadata();

                                if (meta == null || !extractName(f.getName()).equals(meta.getName())) {
                                    logger.warn("skipping \"{}\" | resource name does not match filename", f.getAbsolutePath());
                                    continue;
                                }

                                if (!ResourceType.forKind(resource.getKind()).path().equals(t.path())) {
                                    logger.error("skipping \"{}\" | resource is located in wrong directory. kind: {}, dir: {}"
                                            , f.getAbsolutePath(), resource.getKind(), t.path());
                                    continue;
                                }

                                // some directories might contain multiple resource kinds
                                // skip other kinds as they will be checked on their iteration
                                if (!resource.getKind().equals(t.kind()))
                                    continue;

                                String name = meta.getName();
                                RepositoryResource sameNameResource = firstOccurrences.get(name);
                                if (sameNameResource != null && !sameNameResource.getKind().equals(resource.getKind())) {
                                    // we already encountered resource with same name
                                    // ignore both of them
                                    logger.warn("\"{}/{}\" has the same name as \"{}/{}\". skipping both of them", resource.getKind(), name, sameNameResource.getKind(), name);
                                    resources.remove(firstOccurrences.get(name));
                                    continue;
                                }

                                resources.add(resource);
                                firstOccurrences.put(name, resource);
                            } catch (Exception e) {
                                logger.error("skipping \"{}\" | exception loading resource", f.getAbsolutePath());
                            }
            }
        }
        return resources;
    }


    private static File fileFor(Gitter gitter, RepositoryResource resource) {

        return new File (
                gitter.getConfig().getLocalRepositoryRoot()
                        + "/" + gitter.getBranch()
                        + "/" + ResourceType.forKind(resource.getKind()).path()
                        + "/" + resource.getMetadata().getName()
                        + ".yml");
    }


    private static String extractName(String fileName) {

        int index = fileName.lastIndexOf(".");
        if (index < 0)
            return fileName;
        else
            return fileName.substring(0, index);
    }


    private static String digest(String data) {
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
        Set<RepositoryResource> resources = Repository.loadBranch(new File(path));

        RepositorySnapshot snapshot = new RepositorySnapshot(commitRef);
        snapshot.setResources(resources);
        return snapshot;
    }


    /**
     * Adds resource to the local repository, but does not commit or push changes.
     * Throws an IllegalArgumentException if resource with same name and kind already exists
     *
     * @param  gitter
     *         Gitter object for which repository will be updated.
     *         Must be locked externally as this method does not lock repository by itself
     *
     * @throws IOException
     *         If repository IO operation fails
     * @throws IllegalArgumentException
     *         If resource already exists in the repository
     */
    public static void add(Gitter gitter, RepositoryResource resource) throws IOException {

        File file = fileFor(gitter, resource);
        if (file.exists())
            throw new IllegalArgumentException("resource already exist");
        Repository.saveYAML(file, resource);
    }


    /**
     * Updates resource in the local repository, but does not commit or push changes.
     * Throws an IllegalArgumentException if resource does not exists
     *
     * @param  gitter
     *         Gitter object for which repository will be updated.
     *         Must be locked externally as this method does not lock repository by itself
     *
     * @throws IOException
     *         If repository IO operation fails
     * @throws IllegalArgumentException
     *         If resource already exists in the repository
     */
    public static void update(Gitter gitter, RepositoryResource resource) throws IOException {

        File file = fileFor(gitter, resource);
        if (!file.exists() || !file.isFile())
            throw new IllegalArgumentException("resource does not exist");
        Repository.saveYAML(file, resource);
    }


    /**
     * Removes resource from the local repository, but does not commit or push changes.
     * Throws an IllegalArgumentException if resource does not exists
     *
     * @param  gitter
     *         Gitter object for which repository will be updated.
     *         Must be locked externally as this method does not lock repository by itself
     *
     * @throws IllegalArgumentException
     *         If resource already exists in the repository
     */
    public static void remove(Gitter gitter, RepositoryResource resource) {

        File file = fileFor(gitter, resource);
        if (!file.exists() || !file.isFile())
            throw new IllegalArgumentException("resource does not exist");
        file.delete();
    }

}
