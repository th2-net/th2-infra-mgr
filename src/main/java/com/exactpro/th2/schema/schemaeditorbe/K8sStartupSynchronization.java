package com.exactpro.th2.schema.schemaeditorbe;

import com.exactpro.th2.schema.schemaeditorbe.k8s.K8sCustomResource;
import com.exactpro.th2.schema.schemaeditorbe.k8s.Kubernetes;
import com.exactpro.th2.schema.schemaeditorbe.models.RepositorySnapshot;
import com.exactpro.th2.schema.schemaeditorbe.models.ResourceEntry;
import com.exactpro.th2.schema.schemaeditorbe.models.ResourceType;
import com.exactpro.th2.schema.schemaeditorbe.models.Th2CustomResource;
import com.exactpro.th2.schema.schemaeditorbe.repository.Gitter;
import com.exactpro.th2.schema.schemaeditorbe.repository.Repository;
import com.exactpro.th2.schema.schemaeditorbe.util.Stringifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class K8sStartupSynchronization {

    private static final int SYNC_PARALELIZATION_THREADS = 3;
    private static final Logger logger = LoggerFactory.getLogger(K8sStartupSynchronization.class);

    private void synchronizeNamespace(Config.K8sConfig config, String schemaName, Map<ResourceType, Map<String, ResourceEntry>> repositoryEntries) throws Exception {

        try (Kubernetes kube = new Kubernetes(config, schemaName);) {

            kube.ensureNameSpace();

            // load custom resources from k8s
            Map<ResourceType, Map<String, K8sCustomResource>> k8sEntries = new HashMap<>();
            for (ResourceType t : ResourceType.values())
                if (t.isK8sResource())
                    k8sEntries.put(t, kube.loadCustomResources(t, Th2CustomResource.GROUP, Th2CustomResource.VERSION));

            // synchronize by resource type
            for (ResourceType resourceType : ResourceType.values())
                if (resourceType.isK8sResource()) {
                    Map<String, ResourceEntry> entries = repositoryEntries.get(resourceType);
                    Map<String, K8sCustomResource> customResources = k8sEntries.get(resourceType);

                    for (ResourceEntry entry: entries.values()) {
                        String resourceName = entry.getName();

                        // check repository items against k8s
                        if (!customResources.containsKey(resourceName)) {
                            // create custom resources that do not exist in k8s
                            logger.info("Creating Custom Resource ({}) \"{}.{}\"", resourceType.kind(), schemaName, resourceName);
                            Th2CustomResource resource = new Th2CustomResource(entry);
                            try {
                                Stringifier.stringify(resource.getSpec());
                                kube.createCustomResource(resource);
                            } catch (Exception e) {
                                logger.error("Exception creating Custom Resource ({}) \"{}.{}\" ({})", resourceType.kind(), schemaName, resourceName, e.getMessage());
                            }
                        } else {
                            // compare object's hashes and update custom resources who's hash labels do not match
                            K8sCustomResource cr = customResources.get(resourceName);

                            if (!(entry.getSourceHash() == null || entry.getSourceHash().equals(cr.getSourceHashLabel()))) {
                                // update custopm resource
                                logger.info("Updating Custom Resource ({}) \"{}.{}\"", resourceType.kind(), schemaName, resourceName);
                                Th2CustomResource resource = new Th2CustomResource(entry);
                                try {
                                    Stringifier.stringify(resource.getSpec());
                                    kube.replaceCustomResource(resource);
                                } catch (Exception e) {
                                    logger.error("Exception updating Custom Resource ({}) \"{}.{}\" ({})", resourceType.kind(), schemaName, resourceName, e.getMessage());
                                }
                            }
                        }
                    }

                    // delete k8s resources that do not exist in repository
                    for (String resourceName : customResources.keySet())
                        if (!entries.containsKey(resourceName))
                        try {
                            logger.info("Deleting Custom Resource ({}) \"{}.{}\"", resourceType.kind(), schemaName, resourceName);
                            ResourceEntry entry = new ResourceEntry();
                            entry.setKind(resourceType);
                            entry.setName(resourceName);
                            kube.deleteCustomResource(new Th2CustomResource(entry));
                        } catch (Exception e) {
                            logger.error("Exception deleting Custom Resource ({}) \"{}.{}\" ({})", resourceType.kind(), schemaName, resourceName, e.getMessage());
                        }
            }
        } catch (Exception e) {
            throw e;
        }
    }


    private void synchronizeBranch(Config config, String branch) {

        try {
            logger.info("Checking schema settings \"{}\"", branch);

            // get repository items
            Gitter gitter = Gitter.getBranch(config.getGit(), branch);
            RepositorySnapshot snapshot = Repository.getSnapshot(gitter);
            Set<ResourceEntry> repositoryEntries = snapshot.getResources();

            if (snapshot.getRepositorySettings() == null || !snapshot.getRepositorySettings().isK8sPropagationEnabled()) {
                logger.info("Ignoring schema \"{}\" as it is not configured for synchronization", branch);
                return;
            }

            logger.info("Proceeding with schema \"{}\"", branch);

            // convert to map
            Map<ResourceType, Map<String, ResourceEntry>> repositoryMap = new HashMap<>();
            for (ResourceType t : ResourceType.values())
                if (t.isK8sResource())
                    repositoryMap.put(t, new HashMap<>());

            for (ResourceEntry entry : repositoryEntries)
                if (entry.getKind().isK8sResource()) {
                    Map<String, ResourceEntry> typeMap = repositoryMap.get(entry.getKind());
                    repositoryMap.putIfAbsent(entry.getKind(), typeMap);
                    typeMap.put(entry.getName(), entry);
                }

            // synchronize entries
            synchronizeNamespace(config.getKubernetes(), branch, repositoryMap);

        } catch (Exception e) {
            logger.error("Exception synchronizing schema \"{}\": {}", branch, e.getMessage());
        }
    }


    @PostConstruct
    public void start() {
        logger.info("Starting Kubernetes synchronization phase");

        try {
            Config config = Config.getInstance();
            Set<String> branches = Gitter.getBranches(config.getGit());

            ExecutorService executor = Executors.newFixedThreadPool(SYNC_PARALELIZATION_THREADS);
            for (String branch : branches)
                if (!branch.equals("master"))
                    executor.submit(() -> synchronizeBranch(config, branch));

            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }

        } catch (Exception e) {
            logger.error("Exception fetching branch list from repository");
            throw new RuntimeException("Kubernetes synchronization failed");
        }

        logger.info("Kubernetes synchronization phase complete");
    }

}
