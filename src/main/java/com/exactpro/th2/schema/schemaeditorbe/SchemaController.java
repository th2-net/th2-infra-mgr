package com.exactpro.th2.schema.schemaeditorbe;

import com.exactpro.th2.schema.schemaeditorbe.errors.BadRequestException;
import com.exactpro.th2.schema.schemaeditorbe.errors.NotAcceptableException;
import com.exactpro.th2.schema.schemaeditorbe.errors.ServiceException;
import com.exactpro.th2.schema.schemaeditorbe.k8s.K8sCustomResource;
import com.exactpro.th2.schema.schemaeditorbe.k8s.Kubernetes;
import com.exactpro.th2.schema.schemaeditorbe.models.RepositorySnapshot;
import com.exactpro.th2.schema.schemaeditorbe.models.RequestEntry;
import com.exactpro.th2.schema.schemaeditorbe.models.ResourceEntry;
import com.exactpro.th2.schema.schemaeditorbe.models.Th2CustomResource;
import com.exactpro.th2.schema.schemaeditorbe.repository.Gitter;
import com.exactpro.th2.schema.schemaeditorbe.repository.Repository;
import com.exactpro.th2.schema.schemaeditorbe.repository.RepositoryUpdateEvent;
import com.exactpro.th2.schema.schemaeditorbe.util.Stringifier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Controller
public class SchemaController {

    public static final String SCHEMA_EXISTS = "SCHEMA_EXISTS";
    public static final String REPOSITORY_ERROR = "REPOSITORY_ERROR";
    public static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";

    private Logger logger = LoggerFactory.getLogger(SchemaController.class);

    @GetMapping("/schemas")
    @ResponseBody
    public Set<String> getAvailableSchemas() throws ServiceException  {

        try {
            return Gitter.getBranches(Config.getInstance().getGit());
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }

    @GetMapping("/schema/{name}")
    @ResponseBody
    public RepositorySnapshot getSchemaFiles(@PathVariable(name="name") String name) throws Exception {

        Config.GitConfig config = Config.getInstance().getGit();
        Gitter gitter = Gitter.getBranch(config, name);
        try {
            String commitRef = gitter.checkout();
            Set<ResourceEntry> resources = Repository.loadBranch(config, name);
            RepositorySnapshot snapshot = new RepositorySnapshot(commitRef);
            snapshot.setResources(resources);
            return snapshot;
        } catch (Exception e) {
            throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
        }
    }

    @PutMapping("/schema/{name}")
    @ResponseBody
    public RepositorySnapshot createSchema(@PathVariable(name="name") String name) throws Exception {

        Config.GitConfig config = Config.getInstance().getGit();

        // check if the schema already exists
        Set<String> branches;
        try {
             branches = Gitter.getBranches(config);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
        if (branches.contains(name))
                throw new NotAcceptableException(SCHEMA_EXISTS, "error crating schema. schema already exists");


        // create schema
        Gitter gitter = Gitter.getBranch(config, name);
        try {
            String commitRef = gitter.createBranch("master");
            Set<ResourceEntry> resources = Repository.loadBranch(config, name);
            RepositorySnapshot snapshot = new RepositorySnapshot(commitRef);
            snapshot.setResources(resources);
            return snapshot;
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }


    @PostMapping("/schema/{name}")
    @ResponseBody
    public RepositorySnapshot updateSchema(@PathVariable(name="name") String name, @RequestBody String requestBody) throws Exception {

        // deserialize request body
        List<RequestEntry> operations = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            operations = mapper.readValue(requestBody, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        // validate resource names
        Pattern regex = Pattern.compile(K8sCustomResource.RESOURCE_NAME_REGEXP);
        for (RequestEntry entry : operations)
            if (!regex.matcher(entry.getPayload().getName()).matches())
                throw new NotAcceptableException(BAD_RESOURCE_NAME, String.format(
                        "Invalid resource name : \"%s\" (%s)"
                        , entry.getPayload().getName()
                        , entry.getPayload().getKind().kind()
                ));

        Config config = Config.getInstance();
        Config.GitConfig git = config.getGit();

        // check if the schema exists
        Set<String> branches;
        try {
            branches = Gitter.getBranches(git);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
        if (!branches.contains(name))
            throw new ServiceException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "schema does not exists");

        Gitter gitter = Gitter.getBranch(git, name);
        gitter.checkout();

        // apply operations
        try {
            for (RequestEntry entry : operations) {
                switch (entry.getOperation()) {
                    case add:
                        Repository.add(git, name, entry.getPayload());
                        break;
                    case update:
                        Repository.update(git, name, entry.getPayload());
                        break;
                    case remove:
                        Repository.remove(git, name, entry.getPayload());
                        break;
                }
            }
        } catch (Exception e) {
            gitter.reset();
            throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
        }

        // create schema
        try {
            String commitRef = gitter.commit("schema update");
            if (commitRef == null) {
                logger.info("Nothing changed, leaving");
            } else {
                //synchronize with k8s
                boolean k8sErrorDetected = false;
                try (Kubernetes kube = new Kubernetes(config.getKubernetes(), name);) {

                    for (RequestEntry entry : operations) {
                        try {
                            Stringifier.stringify(entry.getPayload().getSpec());
                            switch (entry.getOperation()) {
                                case add:
                                    kube.create(new Th2CustomResource(entry.getPayload()));
                                    break;
                                case update:
                                    kube.replace(new Th2CustomResource(entry.getPayload()));
                                    break;
                                case remove:
                                    kube.delete(new Th2CustomResource(entry.getPayload()));
                                    break;
                            }
                        } catch (Exception e) {
                            k8sErrorDetected = true;
                            logger.error("Exception provisioning {} resource \"{}\" to Kubernetes ({})"
                                    , entry.getPayload().getKind().kind()
                                    , entry.getPayload().getName()
                                    , e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    k8sErrorDetected = true;
                    logger.error("Exception provisioning resource(s) to Kubernetes ({})", e.getMessage());
                }
                // TODO: report partial success


                SchemaEventRouter router = SchemaEventRouter.getInstance();
                router.addEvent(new RepositoryUpdateEvent(name, commitRef));
            }

            commitRef = gitter.checkout();
            Set<ResourceEntry> resources = Repository.loadBranch(git, name);
            RepositorySnapshot snapshot = new RepositorySnapshot(commitRef);
            snapshot.setResources(resources);
            return snapshot;

        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }

}