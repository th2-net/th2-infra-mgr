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
package com.exactpro.th2.schema.inframgr.repository;

import com.exactpro.th2.schema.inframgr.Config;
import com.exactpro.th2.schema.inframgr.models.RepositoryResource;
import com.exactpro.th2.schema.inframgr.models.RepositorySnapshot;
import com.exactpro.th2.schema.inframgr.models.ResourceEntry;
import com.exactpro.th2.schema.inframgr.models.ResourceType;
import com.exactpro.th2.schema.inframgr.util.Hash;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class Repository {

    private static ResourceEntry loadYMLFile(File ymlFile) throws Exception{

        String ymlFileContents = Files.readString(ymlFile.toPath());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        RepositoryResource cr = mapper.readValue(ymlFileContents, RepositoryResource.class);

        ResourceEntry rdu = new ResourceEntry();
        rdu.setKind(ResourceType.forKind(cr.getKind()));
        rdu.setName(cr.getMetadata().getName());
        rdu.setSpec(cr.getSpec());

        rdu.setSourceHash(Hash.digest(ymlFileContents));

        return rdu;
    }

    private static Set<ResourceEntry> loadBranchYMLFiles(File repositoryRoot) throws Exception {

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

    private static File getFile(Config.GitConfig config, String branch, ResourceEntry data) {
        File file = new File (
                config.getLocalRepositoryRoot()
                        + "/" + branch
                        + "/" + data.getKind().path()
                        + "/" + data.getName()
                        + ".yml");
        return file;
    }


    public static RepositorySnapshot getSnapshot(Gitter gitter) throws Exception {

        String path = gitter.getConfig().getLocalRepositoryRoot() + "/" + gitter.getBranch();
        String commitRef = gitter.checkout();
        Set<ResourceEntry> resources = Repository.loadBranchYMLFiles(new File(path));

        RepositorySnapshot snapshot = new RepositorySnapshot(commitRef);
        snapshot.setResources(resources);
        return snapshot;
    }


    private static void saveYMLFile(File ymlFile, RepositoryResource object) throws Exception {
        ymlFile.getParentFile().mkdir();
        ObjectMapper mapper = new ObjectMapper((new YAMLFactory())
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        String ymlData = mapper.writeValueAsString(object);
        object.setSourceHash(Hash.digest(ymlData));
        Files.writeString(ymlFile.toPath(), ymlData);
        //mapper.writeValue(ymlFile, object);
    }


    public static void add(Config.GitConfig config, String branch, ResourceEntry data) throws Exception {
        File file = getFile(config, branch, data);
        if (file.exists())
            throw new IllegalArgumentException("resource already exist");
        RepositoryResource cr = new RepositoryResource(data);
        Repository.saveYMLFile(file, cr);
        data.setSourceHash(cr.getSourceHash());
    }

    public static void update(Config.GitConfig config, String branch, ResourceEntry data) throws Exception {
        File file = getFile(config, branch, data);
        if (!file.exists() || !file.isFile())
            throw new IllegalArgumentException("resource does not exist");
        RepositoryResource cr = new RepositoryResource(data);
        Repository.saveYMLFile(file, cr);
        data.setSourceHash(cr.getSourceHash());
    }

    public static void remove(Config.GitConfig config, String branch, ResourceEntry data) throws Exception {

        File file = getFile(config, branch, data);
        if (!file.exists() || !file.isFile())
            throw new IllegalArgumentException("resource does not exist");
        file.delete();
    }

}
