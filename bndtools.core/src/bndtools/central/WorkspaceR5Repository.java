package bndtools.central;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bndtools.api.ILogger;
import org.bndtools.api.Logger;
import org.bndtools.utils.Function;
import org.bndtools.utils.log.LogServiceAdapter;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.log.LogService;
import org.osgi.service.repository.Repository;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.deployer.repository.CapabilityIndex;
import aQute.bnd.deployer.repository.api.IRepositoryContentProvider;
import aQute.bnd.deployer.repository.api.IRepositoryIndexProcessor;
import aQute.bnd.deployer.repository.api.Referral;
import aQute.bnd.deployer.repository.providers.R5RepoContentProvider;
import aQute.bnd.service.Refreshable;
import aQute.lib.io.IO;

public class WorkspaceR5Repository implements Repository, Refreshable {

    private static final String NAME = "Workspace";

    private Map<IProject,CapabilityIndex> projectMap = new HashMap<IProject,CapabilityIndex>();
    private final IRepositoryContentProvider contentProvider = new R5RepoContentProvider();

    private final ILogger logger = Logger.getLogger(WorkspaceR5Repository.class);
    private final LogService logAdapter = new LogServiceAdapter(logger);

    /**
     * Can only be instantiated within the package.
     */
    WorkspaceR5Repository() {}

    void init() throws Exception {
        Central.onWorkspaceInit(new Function<Workspace,Void>() {

            public Void run(Workspace a) {
                try {
                    setupProjects();
                } catch (Exception e) {
                    logger.logError("Error initializing workspace repository", e);
                }
                return null;
            }
        });
    }

    void setupProjects() throws Exception {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            Project model = Central.getProject(project.getLocation().toFile());
            if (model != null) {
                File targetDir = getTarget(model);
                if (targetDir != null) {
                    File indexFile = new File(targetDir, ".index");
                    if (indexFile.isFile()) {
                        loadProjectIndex(project, new FileInputStream(indexFile), project.getLocation().toFile().toURI());
                    }
                }
            }
        }
    }

    // This is equivalent to Project.getTarget0(). It gets the target dir without a prepare,
    // which would initialise the plugins too early.
    private File getTarget(Project project) throws IOException {
        File target = project.getFile(project.getProperty("target", "generated"));
        if (!target.exists()) {
            if (!target.mkdirs()) {
                throw new IOException("Could not create directory " + target);
            }
            project.getWorkspace().changedFile(target);
        }
        return target;
    }

    public void loadProjectIndex(final IProject project, InputStream index, URI baseUri) {
        synchronized (projectMap) {
            try {
                cleanProject(project);

                IRepositoryIndexProcessor processor = new IRepositoryIndexProcessor() {
                    public void processResource(Resource resource) {
                        addResource(project, resource);
                    }

                    public void processReferral(URI parentUri, Referral referral, int maxDepth, int currentDepth) {
                        // ignore: we don't create any referrals
                    }
                };
                contentProvider.parseIndex(index, baseUri, processor, logAdapter);
            } catch (Exception e) {
                logger.logError(MessageFormat.format("Failed to process index file for bundles in project {0}.", project.getName()), e);
            } finally {
                IO.close(index);
            }
        }
    }

    private void cleanProject(IProject project) {
        CapabilityIndex index = projectMap.get(project);
        if (index != null)
            index.clear();
    }

    private void addResource(IProject project, Resource resource) {
        CapabilityIndex index = projectMap.get(project);
        if (index == null) {
            index = new CapabilityIndex();
            projectMap.put(project, index);
        }
        index.addResource(resource);
    }

    public Map<Requirement,Collection<Capability>> findProviders(Collection< ? extends Requirement> requirements) {
        Map<Requirement,Collection<Capability>> result = new HashMap<Requirement,Collection<Capability>>();
        for (Requirement requirement : requirements) {
            List<Capability> matches = new LinkedList<Capability>();
            result.put(requirement, matches);

            for (Entry<IProject,CapabilityIndex> entry : projectMap.entrySet()) {
                CapabilityIndex capabilityIndex = entry.getValue();
                capabilityIndex.appendMatchingCapabilities(requirement, matches);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return NAME;
    }

    @Override
    public boolean refresh() throws Exception {
        // reset the projects first
        projectMap = new HashMap<IProject,CapabilityIndex>();
        try {
            setupProjects();
        } catch (Exception e) {
            logger.logError(MessageFormat.format("Failed refresh plugin {0}.", this.getClass().getName()), e);
            return false;
        }
        return true;
    }

    @Override
    public File getRoot() {
        return null;
    }

}
