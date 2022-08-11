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
package com.telenav.cactus.maven.task;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import java.nio.file.Path;
import java.util.Collection;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Collects file modifications and other tasks, and can roll them back.
 *
 * @author Tim Boudreau
 */
public final class Rollback
{
    private final BuildLog log = BuildLog.get().child("rollback");
    private final ThrowingRunnable rollback = ThrowingRunnable.oneShot(true);

    /**
     * Explicitly invoke rollback.
     *
     * @throws Exception if something goes wrong
     */
    public void rollback() throws Exception
    {
        rollback.run();
    }

    /**
     * Execute some code, performing the rollback tasks and then rethrowing in
     * the event the code throws. If the rollback code throws, whatever it threw
     * is attached to the original failure as a suppressed exception, so no
     * information about what went wrong is lost.
     *
     * @param code Something to run
     */
    public <T extends ThrowingRunnable> T executeWithRollback(T code)
    {
        try
        {
            code.run();
        }
        catch (Exception | Error failure)
        {
            try
            {
                rollback.run();
            }
            catch (Exception | Error rollbackFailure)
            {
                failure.addSuppressed(rollbackFailure);
            }
            return Exceptions.chuck(failure); // rethrows
        }
        return code;
    }

    /**
     * Execute some code, performing the rollback tasks and then rethrowing in
     * the event the code throws. If the rollback code throws, whatever it threw
     * is attached to the original failure as a suppressed exception, so no
     * information about what went wrong is lost.
     *
     * @param <T> the return type
     * @param code Something to run
     * @return the result of the supplier
     */
    public <T> T executeWithRollback(ThrowingSupplier<? extends T> code)
    {
        try
        {
            return code.get();
        }
        catch (Exception | Error failure)
        {
            try
            {
                rollback.run();
            }
            catch (Exception | Error rollbackFailure)
            {
                failure.addSuppressed(rollbackFailure);
            }
            return Exceptions.chuck(failure); // rethrows
        }
    }

    /**
     * Add a rollback task to perform in the event of failure; rollback tasks
     * are run in LIFO order.
     *
     * @param run A task to run
     * @return this
     */
    public Rollback addRollbackTask(ThrowingRunnable run)
    {
        rollback.andAlways(notNull("run", run));
        return this;
    }

    /**
     * Add some files which should be checked out from the index in the event of
     * failure.
     *
     * @param paths A collection of paths
     * @return this
     */
    public Rollback addFileModifications(
            Collection<? extends Path> paths)
    {
        notNull("paths", paths).forEach(this::addFileModification);
        return this;
    }

    /**
     * Add a modified file which should be checked out from the index in the
     * event of failure.
     *
     * @param path A path
     * @return this
     */
    public Rollback addFileModification(Path path)
    {
        notNull("path", path);
        // ThrowingRunnable.oneShot() and friends *guarantee* that
        // each runnable added runs, even if the previous one throws.
        // (and throws an aggegated exception at the end if something did).
        // It's basically an arbitrarily deep set of try/finally blocks,
        // if you have things that rollback tasks that MUST be done.
        // Uses a trieber stack internally so one can be shared across threads.
        addRollbackTask(() ->
        {
            ThrowingOptional.from(GitCheckout.checkout(path))
                    .ifPresent(repo ->
                    {
                        log.error("Roll back changes in " + path);
                        repo.checkoutOneFile(path);
                    });
        });
        return this;
    }
}
