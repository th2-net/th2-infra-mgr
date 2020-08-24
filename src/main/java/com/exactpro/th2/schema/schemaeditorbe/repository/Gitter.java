package com.exactpro.th2.schema.schemaeditorbe.repository;

import com.exactpro.th2.schema.schemaeditorbe.Config;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.EntryExistsException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class Gitter {

    private static volatile Map<String, Gitter> instance = new HashMap<>();
    private Config.GitConfig config;
    private String branch;

    private Gitter(Config.GitConfig config, String branch) {
        this.config = config;
        this.branch = branch;
    }

    public static Gitter getBranch(Config.GitConfig config, String branch) {

        if (instance.get(branch) == null)
            synchronized (instance) {
                if (instance.get(branch) == null)
                    instance.put(branch, new Gitter(config, branch));
            }

        return instance.get(branch);
    }

    // TODO : initialize transport on instance creation
    private static TransportConfigCallback transportConfigCallback(Config.GitConfig config) {


        if (config.ignoreInsecureHosts())
            JSch.setConfig("StrictHostKeyChecking", "no");
        SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);

                if (config.getPrivateKey() != null)
                    defaultJSch.addIdentity("backend-key", config.getPrivateKey(), null, null);
                else
                    if (config.getPrivateKeyFile() != null)
                        defaultJSch.addIdentity(config.getPrivateKeyFile());
                    else
                        throw  new IllegalArgumentException("repository private key not set");
                return defaultJSch;
            }
        };

        return transport -> {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(sshSessionFactory);
        };
    }


    public static Map<String, String> getAllBranchesCommits(Config.GitConfig config) throws Exception {

        // retrieve all remote branches
        Collection<Ref> allBRanches = Git.lsRemoteRepository()
                .setHeads(true)
                .setRemote(config.getRemoteRepository())
                .setTransportConfigCallback(transportConfigCallback(config))
                .call();

        // filter, convert and normalize branch names
        Map<String, String> result = new HashMap<>();
        final String lookupPrefix = "refs/heads/";
        allBRanches.stream()
                .filter(r -> r.getName().startsWith(lookupPrefix))
                .forEach(r -> result.put(r.getName().substring(lookupPrefix.length()), r.getObjectId().getName()));

        return result;
    }

    public static Set<String> getBranches(Config.GitConfig config) throws Exception {

        Map<String, String> commits = getAllBranchesCommits(config);
        return commits.keySet();
    }

    private String checkout(Config.GitConfig config, String branch, String targetDir) throws Exception {
        final String repositoryDir = targetDir + "/.git";

        // create bracnh directory if it does not exist
        File dir = new File(targetDir);
        if (!dir.exists())
            dir.mkdirs();

        Repository repo = new FileRepository(repositoryDir);
        Git git = new Git(repo);

        // try to pull branch
        try {
            git
                    .pull()
                    .setTransportConfigCallback(transportConfigCallback(config))
                    .call();
        } catch(NoHeadException e) {
            // probably there is no git repository in the directory
            // try to clone
            Git.cloneRepository()
                    .setBranch(branch)
                    .setURI(config.getRemoteRepository())
                    .setDirectory(dir)
                    .setTransportConfigCallback(transportConfigCallback(config))
                    .call();
        }

        Ref ref = git.checkout()
                .setName(branch)
                .setForced(true)
                .call();
        return ref.getObjectId().getName();
    }

    public String checkout() throws Exception {

        final String targetDir = config.getLocalRepositoryRoot() + "/" + branch;
        return checkout(config, branch, targetDir);
    }


    public String reset() throws Exception {

        final String targetDir = config.getLocalRepositoryRoot() + "/" + branch;
        final String repositoryDir = targetDir + "/.git";

        File dir = new File(targetDir);
        if (!dir.exists())
            throw new IllegalArgumentException("branch does not exist locally");

        Repository repo = new FileRepository(repositoryDir);
        Git git = new Git(repo);
        Ref ref = git.reset().setMode(ResetCommand.ResetType.HARD).call();
        return ref.getObjectId().getName();
    }


    public String commit(String message) throws Exception {
        final String targetDir = config.getLocalRepositoryRoot() + "/" + branch;
        final String repositoryDir = targetDir + "/.git";

        File dir = new File(targetDir);
        if (!dir.exists())
            throw new IllegalArgumentException("branch does not exist locally");

        Repository repo = new FileRepository(repositoryDir);
        Git git = new Git(repo);
        if (git.status().call().isClean())
            return null;
        git.add()
                .setUpdate(true)
                .addFilepattern(".")
                .call();
        git.add()
                .addFilepattern(".")
                .call();
        String commitRef = git.commit()
                .setMessage(message)
                .call()
                .getId().getName();

        git.push()
                .setTransportConfigCallback(transportConfigCallback(config))
                .call();
        return commitRef;
    }

    public String createBranch(String sourceBranch) throws Exception {

        Set<String> branches = Gitter.getBranches(config);
        if (!branches.contains(sourceBranch))
            throw new IllegalArgumentException("Source branch does not exists");
        if (branches.contains(branch))
            throw new EntryExistsException("Branch with such name already exists");

        final String targetDir = config.getLocalRepositoryRoot() + "/" + branch;
        final String repositoryDir = targetDir + "/.git";

        checkout(config, sourceBranch, targetDir);

        Repository repo = new FileRepository(repositoryDir);
        Git git = new Git(repo);
        git.branchCreate()
                .setName(branch)
                .call();
        Ref ref = git.checkout()
                .setName(branch)
                .call();
        git.push()
                .setTransportConfigCallback(transportConfigCallback(config))
                .call();

        return ref.getObjectId().getName();
    }
}
