package com.exactpro.th2.schema.schemaeditorbe.repository;

import com.exactpro.th2.schema.schemaeditorbe.Config;
import com.exactpro.th2.schema.schemaeditorbe.models.RepositorySnapshot;
import com.exactpro.th2.schema.schemaeditorbe.models.ResourceEntry;
import com.exactpro.th2.schema.schemaeditorbe.models.ResourceType;
import com.exactpro.th2.schema.schemaeditorbe.models.Th2CustomResource;
import com.exactpro.th2.schema.schemaeditorbe.util.Hash;
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
        Th2CustomResource cr = mapper.readValue(ymlFileContents, Th2CustomResource.class);

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
        for (ResourceType t : ResourceType.values()) {
            File dir = new File(repositoryRoot.getAbsolutePath() + "/" + t.path());
            if (dir.exists()) {

                if (!dir.isDirectory()) {
                    logger.error("entry expected to be a directory: {}", dir.getAbsoluteFile());
                    continue;
                }

                File[] files = dir.listFiles();
                for (File f : files) {
                    if (f.isFile() && (f.getAbsolutePath().endsWith(".yml") || f.getAbsolutePath().endsWith(".yaml"))) {
                        ResourceEntry rdu = loadYMLFile(f);

                        if (rdu.getKind() != t)
                            logger.error("skipping {} | resource is located in wrong directory. kind: {}, dir: {}", f.getAbsolutePath(), rdu.getKind().kind(), t.path());

                        resources.add(rdu);
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
        Set<ResourceEntry> resources = loadBranchYMLFiles(new File(path));

        RepositorySnapshot snapshot = new RepositorySnapshot(commitRef);
        snapshot.setResources(resources);
        return snapshot;
    }


    private static void saveYMLFile(File ymlFile, Th2CustomResource object) throws Exception {
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
        Th2CustomResource cr = new Th2CustomResource(data);
        Repository.saveYMLFile(file, cr);
        data.setSourceHash(cr.getSourceHash());
    }

    public static void update(Config.GitConfig config, String branch, ResourceEntry data) throws Exception {
        File file = getFile(config, branch, data);
        if (!file.exists() || !file.isFile())
            throw new IllegalArgumentException("resource does not exist");
        Th2CustomResource cr = new Th2CustomResource(data);
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
