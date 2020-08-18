package com.exactpro.th2.schema.schemaeditorbe;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;


public class Gitter {

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

    public static Set<String> getBranches(Config.GitConfig config) throws Exception {

        // retrieve all remote branches
        Collection<Ref> allBRanches = Git.lsRemoteRepository()
                .setHeads(true)
                .setRemote(config.getRemoteRepository())
                .setTransportConfigCallback(transportConfigCallback(config))
                .call();

        // filter, convert and normalize branch names
        Set<String> result = new TreeSet<>();
        final String lookupPrefix = "refs/heads/";
        allBRanches.stream()
                .filter(r -> r.getName().startsWith(lookupPrefix))
                .forEach(r -> result.add(r.getName().substring(lookupPrefix.length())));

        return result;
    }

    private static void checkout(Config.GitConfig config, String branch, String targetDir) throws Exception {
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

        git.checkout()
                .setName(branch)
                .setForced(true)
                .call();
    }

    public static void checkout(Config.GitConfig config, String branch) throws Exception {

        final String targetDir = config.getLocalRepositoryRoot() + "/" + branch;
        checkout(config, branch, targetDir);
    }


    public static void reset(Config.GitConfig config, String branch) throws Exception {

        final String targetDir = config.getLocalRepositoryRoot() + "/" + branch;
        final String repositoryDir = targetDir + "/.git";

        File dir = new File(targetDir);
        if (!dir.exists())
            throw new IllegalArgumentException("branch does not exist locally");

        Repository repo = new FileRepository(repositoryDir);
        Git git = new Git(repo);
        git.reset().setMode(ResetCommand.ResetType.HARD).call();
    }


    public static void commit(Config.GitConfig config, String branch, String message) throws Exception {
        final String targetDir = config.getLocalRepositoryRoot() + "/" + branch;
        final String repositoryDir = targetDir + "/.git";

        File dir = new File(targetDir);
        if (!dir.exists())
            throw new IllegalArgumentException("branch does not exist locally");

        Repository repo = new FileRepository(repositoryDir);
        Git git = new Git(repo);
        if (git.status().call().isClean()) {
            Logger.getLogger("").info("nothing to commit, leaving");
            return;
        }
        git.add()
                .setUpdate(true)
                .addFilepattern(".")
                .call();
        git.add()
                .addFilepattern(".")
                .call();
        git.commit()
                .setMessage(message)
                .call();
        git.push()
                .setTransportConfigCallback(transportConfigCallback(config))
                .call();
    }

    public static void createBranch(Config.GitConfig config, String branch, String sourceBranch) throws Exception {
        Set<String> branches = getBranches(config);
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
        git.checkout()
                .setName(branch)
                .call();
        git.push()
                .setTransportConfigCallback(transportConfigCallback(config))
                .call();
    }
}
