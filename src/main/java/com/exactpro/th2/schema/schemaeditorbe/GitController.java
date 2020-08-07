package com.exactpro.th2.schema.schemaeditorbe;

import com.exactpro.th2.schema.schemaeditorbe.models.ResponseDataUnit;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

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

}