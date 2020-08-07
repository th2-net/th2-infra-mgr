package com.exactpro.th2.schema.schemaeditorbe;

import com.exactpro.th2.schema.schemaeditorbe.models.RequestEntry;
import com.exactpro.th2.schema.schemaeditorbe.models.ResourceEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Controller
public class SchemaController {

    @GetMapping("/schemas")
    @ResponseBody
    public Set<String> getAvailableSchemas() throws Exception  {
        return Gitter.getBranches(Config.getInstance().getGit());
    }

    @GetMapping("/schema/{name}")
    @ResponseBody
    public Set<ResourceEntry> getSchemaFiles(@PathVariable(name="name") String name) throws Exception {
        Config.GitConfig config = Config.getInstance().getGit();
        Gitter.checkout(config, name);
        return Repository.loadBranch(config, name);
    }

    @PutMapping("/schema/{name}")
    @ResponseBody
    public Set<ResourceEntry> createSchema(@PathVariable(name="name") String name) throws Exception {
        Config.GitConfig config = Config.getInstance().getGit();
        Set<String> branches = Gitter.getBranches(config);
        if (branches.contains(name))
                throw new IllegalArgumentException("schema already exists");

        Gitter.createBranch(config, name, "master");
        return Repository.loadBranch(config, name);
    }

    @PostMapping("/schema/{name}")
    @ResponseBody
    public Set<ResourceEntry> updateSchema(@PathVariable(name="name") String name, @RequestBody String requestBody) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        List<RequestEntry> operations = mapper.readValue(requestBody, new TypeReference<List<RequestEntry>>(){});

        Config.GitConfig config = Config.getInstance().getGit();
        Gitter.checkout(config, name);

        // apply operations
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


        Gitter.commit(config, name, "schema update");
        return Repository.loadBranch(config, name);
    }

}