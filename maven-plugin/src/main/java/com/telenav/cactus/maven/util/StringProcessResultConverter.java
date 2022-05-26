package com.telenav.cactus.maven.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter interface specific to strings with convenience wrappers for
 * trimming, pattern matching and simply testing for output or not.
 */
public interface StringProcessResultConverter extends ProcessResultConverter<String>
{

    default ProcessResultConverter<Boolean> trueIfEmpty()
    {
        return testedWith(String::isEmpty);
    }

    default ProcessResultConverter<Boolean> testedWith(Predicate<String> predicate)
    {
        return map(text -> predicate.test(text));
    }

    default StringProcessResultConverter trimmed()
    {
        return (description, proc) ->
        {
            return AwaitableCompletionStage.of(onProcessStarted(description, proc).thenApply(String::trim));
        };
    }

    default StringProcessResultConverter filter(Pattern pattern)
    {
        return (description, proc) ->
        {
            CompletableFuture<String> result = new CompletableFuture<>();
            onProcessStarted(description, proc).whenComplete((str, thrown) ->
            {
                if (thrown != null)
                {
                    result.completeExceptionally(thrown);
                } else
                {
                    Matcher m = pattern.matcher(str);
                    assert m.groupCount() == 1;
                    if (m.find())
                    {
                        result.complete(m.group(1));
                    } else
                    {
                        result.completeExceptionally(new IllegalStateException("Pattern " + pattern.pattern() + " not matched in '" + str + "'"));
                    }
                }
            });
            return AwaitableCompletionStage.of(result);
        };
    }
}
