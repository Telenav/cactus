package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.List;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
public class CodeFlowersMojo extends ScopedCheckoutsMojo {

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout, ProjectTree tree, List<GitCheckout> checkouts) throws Exception {
        
    }

}
