package com.exactpro.th2.schema.schemaeditorbe;

import com.exactpro.th2.schema.schemaeditorbe.models.RepositoryDataType;
import com.exactpro.th2.schema.schemaeditorbe.models.ResponseDataUnit;
import com.exactpro.th2.schema.schemaeditorbe.models.Th2CustomResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class DataLoader {

    private static ResponseDataUnit loadYMLFile(File ymlFile) throws Exception{

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Th2CustomResource cr = mapper.readValue(ymlFile, Th2CustomResource.class);

        ResponseDataUnit rdu = new ResponseDataUnit();
        rdu.setKind(RepositoryDataType.forKind(cr.getKind()));
        rdu.setName(cr.getMetadata().getName());
        rdu.setSpec(cr.getSpec());

        return rdu;
    }

    public static Set<ResponseDataUnit> loadBranchYMLFiles(File repositoryRoot) throws Exception {

        Logger logger = LoggerFactory.getLogger(DataLoader.class);

        Set<ResponseDataUnit> dataUnits = new HashSet<>();
        for (RepositoryDataType t : RepositoryDataType.values()) {
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


    public static Set<ResponseDataUnit> loadBranch(Config.GitConfig config, String branch) throws Exception {
        String path = config.getLocalRepositoryRoot() + "/" + branch;
        return loadBranchYMLFiles(new File(path));
    }
}
