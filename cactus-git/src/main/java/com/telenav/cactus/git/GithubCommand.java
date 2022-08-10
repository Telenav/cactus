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
package com.telenav.cactus.git;

import com.mastfrog.concurrent.future.AwaitableCompletionStage;
import com.mastfrog.function.throwing.io.IOSupplier;
import com.mastfrog.util.preconditions.Checks;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.cli.CliCommand;
import com.telenav.cactus.cli.ProcessResultConverter;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author Tim Boudreau
 * @author jonathanl (shibo)
 */
@SuppressWarnings("unused")
public class GithubCommand<T> extends CliCommand<T>
{
    private final Path workingDir;

    private final String[] args;

    private final BuildLog log;
    private final IOSupplier<String> accessTokenSupplier;
    private RetryWithAuthProcessResultConverter<T> retryConverter;

    public GithubCommand(ProcessResultConverter<T> resultCreator, String... args)
    {
        this((IOSupplier<String>) null, resultCreator, null, args);
    }

    public GithubCommand(IOSupplier<String> authTokenSupplier,
            ProcessResultConverter<T> resultCreator,
            Path workingDir, String... args)
    {
        super("gh", resultCreator);
        this.accessTokenSupplier = authTokenSupplier;
        this.workingDir = workingDir;
        this.args = Checks.notNull("args", args);
        this.log = BuildLog.get()
                .child(getClass().getSimpleName());
    }

    private GithubCommand(BuildLog log, ProcessResultConverter<T> resultCreator,
            Path workingDir, String... args)
    {
        super("gh", resultCreator);
        this.workingDir = workingDir;
        this.args = Checks.notNull("args", args);
        this.log = log;
        this.accessTokenSupplier = null;
    }

    public GithubCommand<T> withWorkingDir(Path dir)
    {
        return new GithubCommand<>(accessTokenSupplier, resultCreator, dir, args);
    }

    public GithubCommand<T> withAuthTokenSupplier(
            IOSupplier<String> authTokenSupplier)
    {
        return new GithubCommand<>(authTokenSupplier, resultCreator, workingDir,
                args);
    }

    @Override
    protected void configureArguments(List<String> list)
    {
        list.addAll(Arrays.asList(args));
    }

    @Override
    protected void onLaunch(Process proc)
    {
        log.debug(() -> "started: " + this);
        super.onLaunch(proc);
    }

    @Override
    protected void validate()
    {
        if (workingDir == null)
        {
            throw new IllegalStateException("Command is a template. Use "
                    + "withWorkingDir() to get an instance that has "
                    + "somewhere to run.");
        }
    }

    @Override
    protected Optional<Path> workingDirectory()
    {
        return Optional.ofNullable(workingDir);
    }

    String accessToken() throws IOException
    {
        return accessTokenSupplier == null
               ? null
               : accessTokenSupplier.get();
    }

    @Override
    protected synchronized ProcessResultConverter<T> resultConverter()
    {
        ProcessResultConverter<T> orig = super.resultConverter();
        if (accessTokenSupplier != null && retryConverter == null)
        {
            // We need a single instance, because it is stateful - we need to
            // detect if we're in a retry and not do so endlessly
            retryConverter = new RetryWithAuthProcessResultConverter<>(this,
                    orig);
        }
        return retryConverter == null || retryConverter.isInRetry()
               ? orig
               : retryConverter;
    }

    static class RetryWithAuthProcessResultConverter<T> implements
            ProcessResultConverter<T>
    {
        private final ProcessResultConverter<T> orig;
        private volatile boolean inRetry;
        private final BuildLog childLog;
        private final GithubCommand<T> cmd;

        private RetryWithAuthProcessResultConverter(
                GithubCommand<T> cmd,
                ProcessResultConverter<T> orig)
        {
            this.orig = orig;
            this.childLog = cmd.log.child(getClass().getSimpleName());
            this.cmd = cmd;
        }

        boolean isInRetry()
        {
            return inRetry;
        }

        @Override
        public AwaitableCompletionStage<T> onProcessStarted(
                Supplier<String> description, Process process)
        {
            if (inRetry)
            {
                // Reentrancy - should not happen due to logic in resultConverter(),
                // but under no circumstances should we loop endlessly through here.
                childLog.debug("Retrying - delegate to " + orig);
                return orig.onProcessStarted(description, process);
            }
            CompletableFuture<T> fut = new CompletableFuture<>();
            childLog.debug(() -> "onProcessStarted");
            process.onExit()
                    .whenComplete((proc, thrown) ->
                    {
                        childLog.debug(
                                () -> "Initial attempt exited with " + proc
                                        .exitValue());
                        if (thrown != null)
                        {
                            fut.completeExceptionally(thrown);
                        }
                        else
                            if (proc.exitValue() == 4)
                            {
                                try
                                {
                                    authenticateAndRetry(fut);
                                }
                                catch (IOException ex)
                                {
                                    childLog.error("Reading auth info", ex);
                                    fut.completeExceptionally(ex);
                                }
                            }
                            else
                            {
                                forwardToOriginalConverter(description, process,
                                        fut);
                            }
                    });
            return AwaitableCompletionStage.of(fut);
        }

        private void forwardToOriginalConverter(Supplier<String> description,
                Process process, CompletableFuture<T> futureReturnedToCaller)
        {
            childLog.debug(
                    () -> "Forward to original");
            AwaitableCompletionStage<T> innerFut = orig
                    .onProcessStarted(description, process);
            innerFut.whenComplete((res, thrown2) ->
            {
                if (thrown2 != null)
                {
                    futureReturnedToCaller.completeExceptionally(thrown2);
                }
                else
                {
                    futureReturnedToCaller.complete(res);
                }
            });
        }

        private void authenticateAndRetry(
                CompletableFuture<T> futureReturnedToCaller) throws IOException
        {
            // gh returns an exit code of 4 if not authenticated
            String accessToken = cmd.accessToken();
            if (accessToken == null || accessToken.isBlank())
            {
                childLog.warn(
                        "Access token provider returned null or blank token.");
                futureReturnedToCaller.completeExceptionally(
                        new RuntimeException(
                                "Not authenticated, and access token not provided"));
            }
            else
            {
                childLog.info(
                        "Not authenticated with GitHub - will authenticate and retry.");
                Auth auth = new Auth(childLog.child("auth"),
                        accessToken, cmd.workingDir);

                auth.run().whenComplete((authSuccess, authThrown) ->
                {
                    onAfterAuthenticationAttempt(authThrown,
                            futureReturnedToCaller, authSuccess);
                });
            }
        }

        private void onAfterAuthenticationAttempt(Throwable authThrown,
                CompletableFuture<T> futureReturnedToCaller, Boolean authSuccess)
        {
            if (authThrown != null)
            {
                childLog.info(
                        "Auth threw.  Original process exit code was " + 4,
                        authThrown);
                futureReturnedToCaller.completeExceptionally(authThrown);
            }
            else
                if (authSuccess)
                {
                    inRetry = true;
                    childLog.info(
                            "Authentication was successful.  Retrying.");
                    onAfterAuthenticationSucceeded(futureReturnedToCaller);
                }
                else
                {
                    futureReturnedToCaller.completeExceptionally(
                            new RuntimeException(
                                    "Authentication failed"));
                }
        }

        private void onAfterAuthenticationSucceeded(
                CompletableFuture<T> futureReturnedToCaller)
        {
            AwaitableCompletionStage<T> rerunStage = cmd.run();
            rerunStage.whenComplete(
                    (rerunResult, rerunThrown) ->
            {
                try
                {
                    if (rerunThrown != null)
                    {
                        childLog.info("Rerun threw ",
                                rerunThrown);
                        futureReturnedToCaller
                                .completeExceptionally(rerunThrown);
                    }
                    else
                    {
                        childLog.debug(
                                () -> "Rerun successful with " + rerunResult);
                        futureReturnedToCaller.complete(rerunResult);
                    }
                }
                finally
                {
                    // In theory, GithubCommand instances should never be reused, but
                    // if one were, failing to clear this would result in a very difficult
                    // to diagnose bug.
                    inRetry = false;
                }
            });
        }
    }

    private static class Auth extends GithubCommand<Boolean>
    {
        private final String accessToken;

        Auth(BuildLog log, String accessToken, Path workingDir)
        {
            super(log, ProcessResultConverter.exitCodeIsZero(),
                    workingDir,
                    "auth",
                    "login",
                    "--hostname",
                    "github.com",
                    "--with-token");
            this.accessToken = accessToken;
        }

        @Override
        protected void onLaunch(Process process)
        {
            super.onLaunch(process);
            try ( var out = new PrintWriter(process.getOutputStream()))
            {
                out.println(accessToken);
            }
        }
    }
}
