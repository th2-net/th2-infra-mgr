package com.exactpro.th2.schema.schemaeditorbe;

import com.exactpro.th2.schema.schemaeditorbe.errors.BadRequestException;
import com.exactpro.th2.schema.schemaeditorbe.errors.NotAcceptableException;
import com.exactpro.th2.schema.schemaeditorbe.errors.ServiceException;
import com.exactpro.th2.schema.schemaeditorbe.models.RequestEntry;
import com.exactpro.th2.schema.schemaeditorbe.models.ResourceEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Controller
public class SchemaController {

    public static final String SCHEMA_EXISTS = "SCHEMA_EXISTS";
    public static final String REPOSITORY_ERROR = "REPOSITORY_ERROR";

    @GetMapping("/schemas")
    @ResponseBody
    public Set<String> getAvailableSchemas() throws NotAcceptableException  {

        try {
            return Gitter.getBranches(Config.getInstance().getGit());
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }

    @GetMapping("/schema/{name}")
    @ResponseBody
    public Set<ResourceEntry> getSchemaFiles(@PathVariable(name="name") String name) throws Exception {

        Config.GitConfig config = Config.getInstance().getGit();
        try {
            Gitter.checkout(config, name);
            return Repository.loadBranch(config, name);
        } catch (Exception e) {
            throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
        }
    }

    @PutMapping("/schema/{name}")
    @ResponseBody
    public Set<ResourceEntry> createSchema(@PathVariable(name="name") String name) throws Exception {

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
        try {
            Gitter.createBranch(config, name, "master");
            return Repository.loadBranch(config, name);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }

    @PostMapping("/schema/{name}")
    @ResponseBody
    public Set<ResourceEntry> updateSchema(@PathVariable(name="name") String name, @RequestBody String requestBody) throws Exception {

        // deserialize request body
        List<RequestEntry> operations = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            operations = mapper.readValue(requestBody, new TypeReference<List<RequestEntry>>() {});
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        Config.GitConfig config = Config.getInstance().getGit();

        // check if the schema exists
        Set<String> branches;
        try {
            branches = Gitter.getBranches(config);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
        if (!branches.contains(name))
            throw new ServiceException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "schema does not exists");

        Gitter.checkout(config, name);

        // apply operations
        try {
            for (RequestEntry entry : operations) {
                switch (entry.getOperation()) {
                    case add:
                        Repository.add(config, name, entry.getPayload());
                        break;
                    case update:
                        Repository.update(config, name, entry.getPayload());
                        break;
                    case remove:
                        Repository.remove(config, name, entry.getPayload());
                        break;
                }
            }
        } catch (Exception e) {
            Gitter.reset(config, name);
            throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
        }


        // create schema
        try {
            Gitter.commit(config, name, "schema update");
            return Repository.loadBranch(config, name);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }

}