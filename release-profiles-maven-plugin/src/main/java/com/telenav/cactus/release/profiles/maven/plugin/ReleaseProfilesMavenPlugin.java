////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2022 Telenav, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package com.telenav.cactus.release.profiles.maven.plugin;

import com.telenav.cactus.git.GitCheckout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import static java.nio.file.StandardOpenOption.READ;

/**
 *
 * @author Tim Boudreau
 */
@Named("release-profiles")
@Singleton
public class ReleaseProfilesMavenPlugin extends AbstractMavenLifecycleParticipant
{
    private static final String RELEASE_PROFILES_XML_FILE_NAME = "release-profiles.xml";

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException
    {
        Path path = Paths.get(session.getExecutionRootDirectory());
        System.out.println("exe root is " + path);
        GitCheckout root = GitCheckout.checkout(path).flatMap(co -> co
                .submoduleRoot().toOptional())
                .orElse(null);
        if (root == null)
        {
            return;
        }
        System.out.println("Checkout root is " + root.checkoutRoot());
        Path profiles = root.checkoutRoot().resolve(
                RELEASE_PROFILES_XML_FILE_NAME);
        System.out.println("Look for release profiles at " + profiles);
        if (!Files.exists(profiles) || Files.isDirectory(profiles) || !Files
                .isReadable(profiles))
        {
            return;
        }
        try
        {
            applyProfiles(profiles, session);
        }
        catch (IOException | XmlPullParserException ex)
        {
            throw new MavenExecutionException("Exception parsing " + profiles,
                    ex);
        }
    }

    private void applyProfiles(Path profilesFile, MavenSession session) throws IOException, XmlPullParserException
    {
        System.out.println("Have profiles file " + profilesFile);
        List<Profile> profiles = profiles(profilesFile);
        System.out.println("READ " + profiles.size() + " PROFILES: ");
        for (Profile p : profiles) {
            System.out.println(" * " + p.getId());
        }
        session.getAllProjects().forEach(prj ->
        {
            for (Profile pf : profiles)
            {
                Profile p = pf.clone();
                System.out.println("APPLY PROFILE " + pf.getId() + " to " + prj.getArtifactId());
                p.setBuild(prj.getBuild());
                prj.getActiveProfiles().add(p);
            }
        });
    }

    private List<Profile> profiles(Path profiles) throws IOException, XmlPullParserException
    {
        try (InputStream in = Files.newInputStream(profiles, READ))
        {
            return new MavenXpp3Reader().read(in);
        }
    }

}
