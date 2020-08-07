package com.exactpro.th2.schema.schemaeditorbe;

import com.exactpro.th2.schema.schemaeditorbe.models.RepositoryEntry;
import com.exactpro.th2.schema.schemaeditorbe.models.ResponseDataUnit;
import com.exactpro.th2.schema.schemaeditorbe.models.Th2CustomResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class Repository {

    private static ResponseDataUnit loadYMLFile(File ymlFile) throws Exception{

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Th2CustomResource cr = mapper.readValue(ymlFile, Th2CustomResource.class);

        ResponseDataUnit rdu = new ResponseDataUnit();
        rdu.setKind(RepositoryEntry.forKind(cr.getKind()));
        rdu.setName(cr.getMetadata().getName());
        rdu.setSpec(cr.getSpec());

        return rdu;
    }

    public static Set<ResponseDataUnit> loadBranchYMLFiles(File repositoryRoot) throws Exception {

        Logger logger = LoggerFactory.getLogger(Repository.class);

        Set<ResponseDataUnit> dataUnits = new HashSet<>();
        for (RepositoryEntry t : RepositoryEntry.values()) {
            File dir = new File(repositoryRoot.getAbsolutePath() + "/" + t.path());
            if (dir.exists()) {

                if (!dir.isDirectory()) {
                    logger.error("entry expected to be a directory: {}", dir.getAbsoluteFile());
                    continue;
                }

                File[] files = dir.listFiles();
                for (File f : files) {
                    if (f.isFile() && (f.getAbsolutePath().endsWith(".yml") || f.getAbsolutePath().endsWith(".yaml"))) {
                        ResponseDataUnit rdu = loadYMLFile(f);

                        if (rdu.getKind() != t)
                            logger.error("skipping {} | resource is located in wrong directory. kind: {}, dir: {}", f.getAbsolutePath(), rdu.getKind().kind(), t.path());

                        dataUnits.add(rdu);
                    }
                }
            }
        }
        return dataUnits;
    }

    private static File getFile(Config.GitConfig config, String branch, ResponseDataUnit data) {
        File file = new File (
                config.getLocalRepositoryRoot()
                        + "/" + branch
                        + "/" + data.getKind().path()
                        + "/" + data.getName()
                        + ".yml");
        return file;
    }


    public static Set<ResponseDataUnit> loadBranch(Config.GitConfig config, String branch) throws Exception {
        String path = config.getLocalRepositoryRoot() + "/" + branch;
        return loadBranchYMLFiles(new File(path));
    }




    private static void saveYMLFile(File ymlFile, Object object) throws Exception {
        ObjectMapper mapper = new ObjectMapper((new YAMLFactory())
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        mapper.writeValue(ymlFile, object);
    }


    public static void add(Config.GitConfig config, String branch, ResponseDataUnit data) throws Exception {
        File file = getFile(config, branch, data);
        if (file.exists())
            throw new IllegalArgumentException("resource already exist");
        Th2CustomResource cr = new Th2CustomResource(data);
        saveYMLFile(file, cr);
    }

    public static void update(Config.GitConfig config, String branch, ResponseDataUnit data) throws Exception {
        File file = getFile(config, branch, data);
        if (!file.exists() || !file.isFile())
            throw new IllegalArgumentException("resource does not exist");
        Th2CustomResource cr = new Th2CustomResource(data);
        saveYMLFile(file, cr);
    }

    public static void remove(Config.GitConfig config, String branch, ResponseDataUnit data) throws Exception {

        File file = getFile(config, branch, data);
        if (!file.exists() || !file.isFile())
            throw new IllegalArgumentException("resource does not exist");
        file.delete();
    }

}
