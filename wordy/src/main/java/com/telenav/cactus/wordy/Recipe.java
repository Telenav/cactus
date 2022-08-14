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
package com.telenav.cactus.wordy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static com.telenav.cactus.wordy.WordLists.*;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.Collections.sort;

/**
 * A list of word lists which can be applied sequentially and repeatedly to a
 * BitsBag to generate a series of words representing all of the bits for a set
 * of bound values.
 *
 * @author Tim Boudreau
 */
public final class Recipe implements Comparable<Recipe>
{
    public static final Recipe DEFAULT = recipe(WordLists.values());
    public static final Recipe PRONOUN_VERB = recipe(PRONOUNS, VERBS);
    public static final Recipe ADVERB_VERB = recipe(ADVERBS, VERBS);
    public static final Recipe POSSESSIVE_ADJECTIVE_NOUN = recipe(POSESSIVES,
            ADJECTIVES, VERBS);
    public static final Recipe POSSESSIVE_ADJECTIVE_NOUN_ADVERB = recipe(
            POSESSIVES, ADJECTIVES, VERBS, ADVERBS);
    public static final Recipe PRONOUN_VERB_NOUN = recipe(PRONOUNS, VERBS, NOUNS);
    public static final Recipe ADJECTIVE_NOUN = recipe(ADJECTIVES, NOUNS);
    public static final Recipe DOUBLE_ADJECTIVE_NOUN = recipe(ADJECTIVES,
            ADJECTIVES, NOUNS);
    public static final Recipe SENTENCE_LIKE = recipe(ADJECTIVES, NOUNS, ADVERBS,
            VERBS, PREPOSITIONS, POSESSIVES, NOUNS);
    public static final Recipe VERB_NOUN = recipe(VERBS, NOUNS);
    public static final Recipe VERB_ADJECTIVE_NOUN = recipe(VERBS, ADJECTIVES,
            LARGE_NOUNS);
    public static final Recipe TELENAV_DEFAULT = recipe(BUILD_NAME_ADJECTIVES,
            BUILD_NAME_NOUNS);

    private static final Recipe LARGE_NOUN_ADJECTIVE = recipe(LARGE_ADJECTIVES,
            LARGE_NOUNS);
    private static final Recipe LARGE_NOUN_VERB = recipe(LARGE_NOUNS,
            LARGE_VERBS);
    private static final Recipe LARGE_NOUN_DOUBLE_ADJECTIVE = recipe(
            LARGE_ADJECTIVES, LARGE_ADJECTIVES, LARGE_NOUNS);
    public static final Recipe LARGE_SENTENCE_LIKE
            = recipe(LARGE_ADJECTIVES, LARGE_ADJECTIVES, LARGE_NOUNS,
                    LARGE_VERBS, LARGE_ADVERBS);

    public static List<? extends Recipe> recipes()
    {
        List<Recipe> result = new ArrayList<>(asList(
                DEFAULT, PRONOUN_VERB, PRONOUN_VERB_NOUN,
                ADJECTIVE_NOUN, DOUBLE_ADJECTIVE_NOUN, SENTENCE_LIKE,
                VERB_NOUN, ADVERB_VERB, POSSESSIVE_ADJECTIVE_NOUN_ADVERB,
                VERB_ADJECTIVE_NOUN, TELENAV_DEFAULT,
                LARGE_SENTENCE_LIKE, LARGE_NOUN_ADJECTIVE,
                LARGE_NOUN_VERB, LARGE_NOUN_DOUBLE_ADJECTIVE
        ));
        sort(result);
        return result;
    }

    public static Recipe best(int bits)
    {
        List<? extends Recipe> all = recipes();
        for (Recipe r : all)
        {
            if (bits <= r.bits())
            {
                return r;
            }
        }
        return all.get(all.size() - 1);
    }

    private final WordList[] items;

    Recipe(WordList... items)
    {
        this.items = items;
    }

    @Override
    public String toString()
    {
        StringBuilder result = new StringBuilder();
        for (WordList wl : items)
        {
            if (result.length() > 0)
            {
                result.append('-');
            }
            result.append(wl);
        }
        return result.toString();
    }

    public int bits()
    {
        int result = 0;
        for (WordList item : items)
        {
            result += item.bits();
        }
        return result;
    }

    public String randomPhrase(Random rnd)
    {
        return randomPhrase("-", rnd);
    }

    public String randomPhrase(String delimiter, Random rnd)
    {
        StringBuilder sb = new StringBuilder();
        for (WordList wl : items)
        {
            if (sb.length() > 0)
            {
                sb.append(delimiter);
            }
            sb.append(wl.word(rnd.nextInt(wl.size())));
        }
        return sb.toString();
    }

    public static void main(String[] args)
    {
        Random rnd = new Random(currentTimeMillis());
        for (Recipe r : recipes())
        {
            System.out.println(r.randomPhrase(rnd));
        }
    }

    public static Recipe recipe(WordList... items)
    {
        return new Recipe(items);
    }

    /**
     * Use this recipe to convert the bits represented by the passed array of
     * values into a phrase.
     *
     * @param values Some bound values
     * @return A phrase
     */
    public String createPhrase(BoundValue... values)
    {
        return createPhrase(" ", values);
    }

    /**
     * Use this recipe to convert the bits represented by the passed array of
     * values into a phrase.
     *
     * @param delimiter the delimiter
     * @param values Some bound values
     * @return A phrase
     */
    public String createPhrase(char delimiter, BoundValue... values)
    {
        return createPhrase(Character.toString(delimiter), values);
    }

    /**
     * Use this recipe to convert the bits represented by the passed array of
     * values into a phrase.
     *
     * @param delimiter the delimiter
     * @param values Some bound values
     * @return A phrase
     */
    public String createPhrase(String delimiter, BoundValue... values)
    {
        return createPhrase(delimiter, false, values);
    }

    /**
     * Use this recipe to convert the bits represented by the passed array of
     * values into a phrase.
     *
     * @param delimiter the delimiter
     * @param shuffleBits If true, shuffle the bits using the random seed (can
     * be supplied as a system property) - this will result in consistency, but
     * more variation in the generated phrases, since some low bits will be
     * swapped with high bits and vice versa
     * @param values Some bound values
     * @return A phrase
     */
    public String createPhrase(String delimiter, boolean shuffleBits,
            BoundValue... values)
    {
        if (values.length == 0)
        {
            throw new IllegalStateException("No values passed");
        }
        BitsBag bag = new BitsBag();
        StringBuilder sb = new StringBuilder();
        Consumer<String> c = word ->
        {
            if (!delimiter.isEmpty() && sb.length() > 0)
            {
                sb.append(delimiter);
            }
            sb.append(word);
        };
        for (BoundValue bv : values)
        {
            bv.addTo(bag);
        }
        if (shuffleBits)
        {
            bag.shuffle(shuffler());
        }
        apply(bag, c);
        return sb.toString();
    }

    /**
     * Given a bag of bits, and a consumer for words, iteratively applies each
     * word list to generate a word until all of the bits have been consumed.
     *
     * @param bag A bag of bits (which presumably has had some BoundValues added
     * to it)
     * @param c A consumer of words
     */
    public void apply(BitsBag bag, Consumer<String> c)
    {
        AggregateBitsConsumer agg = new AggregateBitsConsumer(c, items);
        while (agg.consumed() < bag.bits())
        {
            bag.consume(agg);
        }
    }

    @Override
    public int compareTo(Recipe o)
    {
        return Integer.compare(bits(), o.bits());
    }
}
