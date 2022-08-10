package com.telenav.cactus.maven;

import com.mastfrog.function.throwing.io.IOSupplier;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.trigger.RunPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static java.lang.System.getenv;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Base class for mojos which use the GitHub CLI which may require supplying an
 * authentication token.
 *
 * @author Tim Boudreau
 */
abstract class AbstractGithubMojo extends ScopedCheckoutsMojo
        implements IOSupplier<String>
{
    static final String GITHUB_CLI_PAT_ENV_VAR = "GITHUB_PAT";
    static final String GITHUB_CLI_PAT_FILE_ENV_VAR = "GITHUB_PAT_FILE";

    /**
     * Github authentication token to use with the github cli client. If not
     * present, the GITHUB_PAT environment variable must be set to a valid
     * github personal access token, or the GITHUB_PAT_FILE environment variable
     * must be set to an extant file that contains the personal access token and
     * nothing else.
     */
    @Parameter(property = "cactus.authentication-token", required = false)
    private String authenticationToken;

    protected AbstractGithubMojo()
    {
    }

    protected AbstractGithubMojo(boolean runFirst)
    {
        super(runFirst);
    }

    protected AbstractGithubMojo(RunPolicy policy)
    {
        super(policy);
    }
    
    @Override
    protected final void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        if (authenticationToken == null || authenticationToken.isBlank())
        {
            String result = getenv(GITHUB_CLI_PAT_ENV_VAR);
            if (result == null)
            {
                result = getenv(GITHUB_CLI_PAT_FILE_ENV_VAR);
            }
            if (result == null)
            {
                fail("-Dcactus.authentication-token not passed, and neither "
                        + GITHUB_CLI_PAT_ENV_VAR + " nor " + GITHUB_CLI_PAT_FILE_ENV_VAR
                        + " are set in the environment");
            }
        }
        onValidateGithubParameters(log, project);
    }

    protected void onValidateGithubParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        // do nothing - for subclasses
    }

    @Override
    public final String get() throws IOException
    {
        if (authenticationToken == null || authenticationToken.isBlank())
        {
            return getTokenFromEnvironment();
        }
        return authenticationToken;
    }

    private String getTokenFromEnvironment() throws IOException
    {
        String result = getenv(GITHUB_CLI_PAT_ENV_VAR);
        if (result == null)
        {
            String filePath = getenv(GITHUB_CLI_PAT_FILE_ENV_VAR);
            if (filePath != null)
            {
                Path file = Paths.get(filePath);
                if (Files.exists(file))
                {
                    return Files.readString(file, UTF_8);
                }
                else
                {
                    throw new IOException(GITHUB_CLI_PAT_FILE_ENV_VAR
                            + " is set to " + filePath + " but it does not exist.");
                }
            }
            fail("-Dcactus.authentication-token not passed, and GH_TOKEN "
                    + "environment variable is unset");
        }
        return result;
    }

}
