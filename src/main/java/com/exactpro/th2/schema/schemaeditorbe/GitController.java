package com.exactpro.th2.schema.schemaeditorbe;

import com.exactpro.th2.schema.schemaeditorbe.models.RequestEntry;
import com.exactpro.th2.schema.schemaeditorbe.models.ResponseDataUnit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Controller
public class GitController {

    @GetMapping("/schemas")
    @ResponseBody
    public Set<String> getAvailableSchemas() throws Exception  {
        return Gitter.getBranches(Config.getInstance().getGit());
    }

    @GetMapping("/schema/{name}")
    @ResponseBody
    public Set<ResponseDataUnit> getSchemaFiles(@PathVariable(name="name") String name) throws Exception {
        Config.GitConfig config = Config.getInstance().getGit();
        Gitter.checkout(config, name);
        return DataLoader.loadBranch(config, name);
    }

    @PutMapping("/schema/{name}")
    @ResponseBody
    public Set<ResponseDataUnit> createSchema(@PathVariable(name="name") String name) throws Exception {
        Config.GitConfig config = Config.getInstance().getGit();
        Set<String> branches = Gitter.getBranches(config);
        if (branches.contains(name))
                throw new IllegalArgumentException("schema already exists");

        Gitter.createBranch(config, name, "master");
        return DataLoader.loadBranch(config, name);
    }

    @PostMapping("/schema/{name}")
    @ResponseBody
    public Set<ResponseDataUnit> updateSchema(@PathVariable(name="name") String name, @RequestBody String requestBody) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        List<RequestEntry> operations = mapper.readValue(requestBody, new TypeReference<List<RequestEntry>>(){});

        Config.GitConfig config = Config.getInstance().getGit();
        Gitter.checkout(config, name);

        // apply operations
        for (RequestEntry entry : operations) {
            switch (entry.getOperation()) {
                case add:
                    DataLoader.add(config, name, entry.getPayload());
                    break;
                case update:
                    DataLoader.update(config, name, entry.getPayload());
                    break;
                case remove:
                    DataLoader.remove(config, name, entry.getPayload());
                    break;
            }
        }


        Gitter.commit(config, name, "schema update");
        return DataLoader.loadBranch(config, name);
    }

}