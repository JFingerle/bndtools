package bndtools.model.repo;

import java.io.File;
import java.text.MessageFormat;
import java.util.SortedSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Path;

import aQute.bnd.service.Actionable;
import aQute.bnd.service.RemoteRepositoryPlugin;
import aQute.bnd.service.RepositoryPlugin;
import aQute.bnd.service.ResourceHandle;
import aQute.bnd.service.ResourceHandle.Location;
import aQute.bnd.service.Strategy;
import aQute.bnd.version.Version;
import bndtools.Logger;
import bndtools.api.ILogger;

public class RepositoryBundle implements IAdaptable {
    private static final ILogger logger = Logger.getLogger();

    private final RepositoryPlugin repo;
    private final String bsn;

    RepositoryBundle(RepositoryPlugin repo, String bsn) {
        this.repo = repo;
        this.bsn = bsn;
    }

    public RepositoryPlugin getRepo() {
        return repo;
    }

    public String getBsn() {
        return bsn;
    }

    @Override
    public String toString() {
        return "RepositoryBundle [repo=" + repo + ", bsn=" + bsn + "]";
    }

    public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
        Object result = null;

        if (IFile.class.equals(adapter)) { // ||
                                           // IResource.class.equals(adapter))
                                           // {
            try {
                File file = getFile();
                if (file != null) {
                    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
                    result = root.getFileForLocation(new Path(file.getAbsolutePath()));
                }
            } catch (Exception e) {
                logger.logError(MessageFormat.format("Failed to query repository {0} for bundle {1}.", repo.getName(), bsn), e);
            }
        } else if (File.class.equals(adapter)) {
            result = getFile();
        }

        return result;
    }

    private File getFile() {
        try {
            File file;
            if (repo instanceof RemoteRepositoryPlugin) {
                ResourceHandle handle = ((RemoteRepositoryPlugin) repo).getHandle(bsn, "latest", Strategy.HIGHEST, null);
                if (handle.getLocation() == Location.local || handle.getLocation() == Location.remote_cached)
                    file = handle.request();
                else
                    file = null;
            } else {
                SortedSet<Version> versions = repo.versions(bsn);
                if (versions == null || versions.isEmpty())
                    file = null;
                else
                    file = repo.get(bsn, versions.last(), null);
            }
            return file;
        } catch (Exception e) {
            logger.logError(MessageFormat.format("Failed to query repository {0} for bundle {1}.", repo.getName(), bsn), e);
            return null;
        }
    }

    String getTooltip(Version version) {
        if (repo instanceof Actionable) {
            try {
                return ((Actionable) repo).tooltip(new Object[] {
                        bsn, version
                });
            } catch (Exception e) {
                logger.logError(MessageFormat.format("Failed to query repository {0} for bundle {1} for tooltip.", repo.getName(), bsn), e);
            }
        }
        return null;
    }
}
