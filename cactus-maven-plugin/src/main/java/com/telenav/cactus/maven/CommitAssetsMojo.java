package com.telenav.cactus.maven;

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.commit.CommitMessage.Section;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.SharedProjectTreeMojo;
import com.telenav.cactus.maven.shared.SharedDataKey;
import com.telenav.cactus.maven.trigger.RunPolicies;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.KEEP_ALIVE;

/**
 * A mojo specifically for generating a commit in dirty assets repositories.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = KEEP_ALIVE,
        name = "commit-assets", threadSafe = true)
@BaseMojoGoal("commit-assets")
public class CommitAssetsMojo extends SharedProjectTreeMojo
{

    /**
     * If true, also push those repositories that had commits generated.
     */
    @Parameter(property = "cactus.push", defaultValue = "false")
    boolean push;

    /**
     * If true, after committing, run <code>git gc --aggressive</code> - this
     * can make a substantial difference in how long the post-push back-end work
     * takes on Github's end before the push is complete and the connection is
     * closed.
     */
    @Parameter(property = "cactus.gc", defaultValue = "true")
    boolean gc;

    static SharedDataKey<Boolean> DONE = SharedDataKey.of("CommitAssetsMojo",
            Boolean.class);

    public CommitAssetsMojo()
    {
        super(RunPolicies.EVERY);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        Optional<Boolean> opt = sharedData().get(DONE);
        if (opt.isPresent() && opt.get())
        {
            return;
        }

        withProjectTree(tree ->
        {
            sharedData().put(DONE, true);
            Set<GitCheckout> assetsCheckouts = tree.nonMavenCheckouts();
            System.out.println("HAVE " + assetsCheckouts.size() + " checkouts");

            CommitMessage msg = new CommitMessage(CommitAssetsMojo.class,
                    "Update assets");
            Set<GitCheckout> toReallyCommit = new HashSet<>();
            try ( Section<CommitMessage> sect = msg.section(
                    "Assets Repositories"))
            {
                for (GitCheckout co : assetsCheckouts)
                {
                    boolean dirty = tree.isDirty(co) || co.hasUntrackedFiles();
                    System.out.println("CHECK " + co + " dirty " + dirty);
                    if (dirty)
                    {
                        toReallyCommit.add(co);
                        log.info("Have dirty assets checkout " + co
                                .logggingName());
                        sect.bulletPoint(co.logggingName());
                    }
                }
            }
            if (!toReallyCommit.isEmpty())
            {
                String cm = msg.toString();
                for (GitCheckout co : toReallyCommit)
                {
                    log.info("Commit " + co.logggingName());
                    ifNotPretending(() ->
                    {
                        co.addAll();
                        co.commit(cm);
                    });
                    if (gc)
                    {
                        log.info(
                                "git gc --aggresive to improve push performance");
                        ifNotPretending(co::gc);
                    }
                    if (push)
                    {
                        log.info("Push " + co.logggingName());
                        ifNotPretending(co::push);
                    }
                    ifNotPretending(tree::invalidateCache);
                }
            }
        });
    }
}
