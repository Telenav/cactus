package com.telenav.cactus.github;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.function.throwing.io.IOFunction;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.mastfrog.util.preconditions.Exceptions.chuck;
import static java.util.Collections.emptyList;

/**
 * Pojo to represent the data we get back from a call to gh pr list with the
 * argument
 * <pre>
 * --json url,title,state,mergeable,body,number,mergeCommit,headRefName,baseRefName
 * </pre>
 *
 * @author Tim Boudreau
 */
public class MinimalPRItem
{
    public final String baseRefName;
    public final String body;
    public final String headRefName;
    public final String mergeable;
    public final long number;
    public final String state;
    public final String title;
    public final String url;

    @JsonCreator
    public MinimalPRItem(
            @JsonProperty("baseRefName") String baseRefName,
            @JsonProperty("body") String body,
            @JsonProperty("headRefName") String headRefName,
            @JsonProperty("mergeable") String mergeable,
            @JsonProperty("number") long number,
            @JsonProperty("state") String state,
            @JsonProperty("title") String title,
            @JsonProperty("url") String url)
    {
        this.baseRefName = baseRefName;
        this.body = body;
        this.headRefName = headRefName;
        this.mergeable = mergeable;
        this.number = number;
        this.state = state;
        this.title = title;
        this.url = url;
    }

    public URI toURI()
    {
        return URI.create(url);
    }

    public boolean isMergeable()
    {
        return "MERGEABLE".equals(mergeable);
    }

    public boolean isOpen()
    {
        return "OPEN".equals(state);
    }

    public static List<MinimalPRItem> parse(String output) throws IOException
    {
        if ("[]".equals(output.trim())) {
            return emptyList();
        }
        return Arrays.asList(
                new ObjectMapper().readValue(output, MinimalPRItem[].class));
    }

    public static Function<String, List<MinimalPRItem>> parser()
    {
        IOFunction<String, List<MinimalPRItem>> raw = MinimalPRItem::parse;
        return raw.toNonThrowing();
    }

    @Override
    public String toString()
    {
        try
        {
            return new ObjectMapper().writeValueAsString(this);
        }
        catch (JsonProcessingException ex)
        {
            return chuck(ex);
        }
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 73 * hash + Objects.hashCode(this.baseRefName);
        hash = 73 * hash + Objects.hashCode(this.headRefName);
        hash = 73 * hash + (int) (this.number ^ (this.number >>> 32));
        hash = 73 * hash + Objects.hashCode(this.url);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final MinimalPRItem other = (MinimalPRItem) obj;
        if (this.number != other.number)
            return false;
        if (!Objects.equals(this.baseRefName, other.baseRefName))
            return false;
        if (!Objects.equals(this.headRefName, other.headRefName))
            return false;
        return Objects.equals(this.url, other.url);
    }
}
