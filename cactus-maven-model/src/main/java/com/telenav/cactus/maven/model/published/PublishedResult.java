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
package com.telenav.cactus.maven.model.published;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Optional;

/**
 * Published state of a maven artifact, and publication timestamp (derived from
 * the last-modified header of the maven repository's response).
 *
 * @author Tim Boudreau
 */
public final class PublishedResult
{

    private final PublishedState state;
    private final ZonedDateTime publicationTimestamp;

    PublishedResult(PublishedState state)
    {
        this.state = state;
        publicationTimestamp = null;
    }

    PublishedResult(PublishedState state, HttpResponse<?> response)
    {
        this.state = state;
        Optional<String> lm = response.headers().firstValue("last-modified");
        if (lm.isPresent())
        {
            // Don't crash the build if the server returns an unparsable date header
            ZonedDateTime zdt;
            try
            {
                zdt = ZonedDateTime.parse(lm.get(),
                        ISO2822DateFormat);
            }
            catch (DateTimeParseException dtpe)
            {
                System.err.println("Invalid date from server: " + lm.get());
                dtpe.printStackTrace(System.err);
                zdt = null;
            }
            publicationTimestamp = zdt;
        }
        else
        {
            publicationTimestamp = null;
        }
    }

    public PublishedState state()
    {
        return state;
    }

    public Optional<ZonedDateTime> publicationTimestamp()
    {
        return Optional.ofNullable(publicationTimestamp);
    }

    public Optional<Instant> publicationInstant()
    {
        return publicationTimestamp().map(ZonedDateTime::toInstant);
    }

    public boolean differs()
    {
        return state.differs();
    }

    public String toString()
    {
        return state.toString()
                + (publicationTimestamp == null
                   ? null
                   : publicationInstant().get());
    }

    // Borrowed from acteur-headers
    private static final DateTimeFormatter ISO2822DateFormat
            = new DateTimeFormatterBuilder()
                    .appendText(ChronoField.DAY_OF_WEEK,
                            TextStyle.SHORT_STANDALONE).appendLiteral(", ")
                    .appendText(ChronoField.DAY_OF_MONTH, TextStyle.FULL)
                    .appendLiteral(" ")
                    .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
                    .appendLiteral(" ")
                    .appendText(ChronoField.YEAR, TextStyle.FULL).appendLiteral(
                    " ")
                    .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(":")
                    .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(
                    ":")
                    .appendValue(ChronoField.SECOND_OF_MINUTE, 2).appendLiteral(
                    " ")
                    .appendOffsetId().toFormatter();

}
