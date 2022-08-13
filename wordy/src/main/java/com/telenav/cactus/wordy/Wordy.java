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
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.telenav.cactus.wordy.Recipe.SENTENCE_LIKE;
import static com.telenav.cactus.wordy.Recipe.best;
import static com.telenav.cactus.wordy.Recipe.recipe;
import static com.telenav.cactus.wordy.Recipe.recipes;
import static com.telenav.cactus.wordy.WordLists.find;
import static com.telenav.cactus.wordy.WordLists.nearestPowerOfTwoLessThan;
import static com.telenav.cactus.wordy.WordLists.values;
import static java.lang.Long.parseLong;
import static java.lang.System.exit;

/**
 * CLI entry point.
 *
 * @author Tim Boudreau
 */
public final class Wordy
{
    private static final Pattern FRACTION_PATTERN = Pattern.compile(
            "(\\d+)/(\\d+)");

    public static void main(String[] args)
    {
        CommandLineArguments arguments = parse(args);
        String phrase = arguments.recipe().createPhrase(
                arguments.delimiter(), arguments.shuffle, arguments.values());
        System.out.println(phrase);
    }

    static CommandLineArguments parse(String[] args)
    {
        List<BoundValue> result = new ArrayList<>();
        boolean bestFit = false;
        boolean debug = false;
        Recipe recipe = null;
        String delimiter = null;
        boolean verbose = false;
        boolean shuffle = false;
        for (int i = 0; i < args.length; i++)
        {
            String arg = args[i];
            switch (arg)
            {
                case "--shuffle":
                case "-s":
                    if (shuffle)
                    {
                        printHelpAndExit("--shuffle / -s passed twice.");
                    }
                    shuffle = true;
                    continue;
                case "--recipe":
                case "-r":
                    if (bestFit)
                    {
                        printHelpAndExit(
                                "Cannot use both --recipe / -r and --best / -b together.");
                    }
                    if (i == args.length - 1)
                    {
                        printHelpAndExit("--recipe / -r must be followed by "
                                + "a string such as adjectives-nouns-verbs-nouns. "
                                + "Possible names: " + listNames(
                                        new StringBuilder()));
                    }
                    i++;
                    recipe = parseRecipe(args[i]);
                    continue;
                case "--best":
                case "-b":
                    bestFit = true;
                    if (recipe != null)
                    {
                        printHelpAndExit(
                                "Cannot use both --recipe / -r and --best / -b together.");
                    }
                    continue;
                case "--delimiter":
                case "-d":
                    if (delimiter != null)
                    {
                        printHelpAndExit("--delimiter / -d passed twice.");
                    }
                    if (i == args.length - 1)
                    {
                        printHelpAndExit(
                                "--delimiter / -d must be followed by a string "
                                + "to use as a word separator.");
                    }
                    delimiter = args[++i];
                    continue;
                case "--verbose":
                case "-v":
                    if (verbose)
                    {
                        printHelpAndExit("--verbose / -v passed twice.");
                    }
                    verbose = true;
                    continue;
                case "--help":
                case "-h":
                    printHelpAndExit(0, null);
                    break;
                case "--debug":
                case "-g":
                    if (debug)
                    {
                        printHelpAndExit("--verbose / -v passed twice.");
                    }
                    debug = true;
                    continue;
                case "--surpriseme":
                case "-m":
                    Random rnd = ThreadLocalRandom.current();
                    List<? extends Recipe> allRecipes = Recipe.recipes();
                    Recipe target = allRecipes.get(rnd
                            .nextInt(allRecipes.size()));
                    String ph = target.randomPhrase(rnd);
                    System.out.println(ph);
                    System.exit(0);
                    break;
            }
            for (String s : arg.split("\\s+"))
            {
                if (s.isEmpty())
                {
                    continue;
                }
                Matcher m = FRACTION_PATTERN.matcher(s);
                if (!m.find())
                {
                    printHelpAndExit(
                            "Have input that does not match " + FRACTION_PATTERN
                                    .pattern()
                            + " in '" + s + "'");
                }
                long n = parseLong(m.group(1));
                long of = parseLong(m.group(2));
                if (of < n)
                {
                    printHelpAndExit(
                            "Illegal range " + n + " of " + of + " in '" + s + "'");
                }
                result.add(new FixedBoundValue(n, of));
            }
        }
        if (result.isEmpty())
        {
            printHelpAndExit(0, "No fractions were input.");
        }
        return new CommandLineArguments(recipe, result, delimiter, bestFit,
                verbose, debug, shuffle);
    }

    static void printHelpAndExit(String message)
    {
        printHelpAndExit(1, message);
    }

    static void printHelpAndExit(int code, String message)
    {
        if (message != null)
        {
            System.err.println(message);
            System.err.println();
        }
        System.err.println(
                "Wordy\n=====\n\nGenerate a phrase based on some input expressed as fractions.\n");
        System.err.println(
                "Contains a collection of word lists (below) each of which can express some"
                + "\nnumber of bits based on their size.  The bounded input - say, 1/4 - requires a certain\n"
                + "number of bits to express (in this case, 2 bits).  The input is combined into a bit stream\n"
                + "and bits are consumed by the requested lists consuming what bits they can and emitting the\n"
                + "corresponding word, until all bits have been consumed.\n");
        System.err.println("Usage: java -jar wordy.jar n1/n2 [n3/n4 ...]");
        System.err.println("\nArguments");
        System.err.println("----------");
        System.err.println("\t--best / -b\tUse the smallest built-in recipe "
                + "that can express at least\n\t\t\tthe number of bits in the input without "
                + "repeating");
        System.err.println(
                "\t--recipe / -r [list]\tUse a specific recipe of built in word lists, "
                + "e.g. adjectives-nouns-verbs");
        System.err.println(
                "\t--delimiter / -d [delim]\tUse the next argument as an inter-word delimiter");
        System.err.println(
                "\t--shuffle / -s\tShuffle the bits randomly (but consistently across runs) - this results \n\t\t\t"
                + "in fewer duplicate words in output for similar input");
        System.err.println(
                "\t--surpriseme / -m\tIgnore all other arguments, print a random phrase from a random recipe and exit.");
        System.err.println("\t--verbose / -v\tPrint debug output to stderr");
        System.err.println("\t--help / -h\tPrint this help");
        System.err.println();
        System.err.println(
                "Word lists are shuffled so that words are not chosen in alphabetic order.  The");
        System.err.println(
                "random seed can be set with the system property `wordy-shuffle-seed`.");
        System.err.println("\nBuilt In Word Lists");
        System.err.println("-------------------");
        System.err.println(listNamesDetail(new StringBuilder()));
        System.err.println();
        System.err.println("Examples");
        System.err.println("--------\n");
        System.err.println(
                "  java -jar wordy.jar --verbose --best 0/3 150/1150 233/250 54/58 --delimiter '_'");
        System.err.println(
                "  java -Dwordy-shuffle-seed=3 -jar wordy.jar 251/30000 13/150 130/256 23/58 730/1010 -s -r largeadjectives-largeadverbs-largeverbs-posessives-nouns");
        System.err.println(
                "  java -jar wordy.jar --surpriseme");
        System.err.println();
        exit(code);
    }

    static Recipe parseRecipe(String arg)
    {
        List<WordList> all = new ArrayList<>();
        for (String s : arg.split("-"))
        {
            if (s.isEmpty())
            {
                continue;
            }
            WordList wl = find(s);
            if (wl == null)
            {
                printHelpAndExit(recipeParseListFailure(
                        "Unrecognized word list name '" + s + "'.  Recognized values:"));
            }
            all.add(wl);
        }
        if (all.isEmpty())
        {
            printHelpAndExit(recipeParseListFailure(
                    "No word list names found in '" + arg + "'"));
        }
        return recipe(all.toArray(WordList[]::new));
    }

    static String recipeParseListFailure(String msg)
    {
        StringBuilder sb = new StringBuilder(1_024);
        sb.append(msg);
        listNames(sb);
        return sb.toString();
    }

    static StringBuilder listNames(StringBuilder sb)
    {
        Set<String> names = new TreeSet<>();
        for (WordLists wl : WordLists.values())
        {
            if (wl.hidden())
            {
                continue;
            }
            names.add(wl.toString());
        }
        for (String name : names)
        {
            sb.append('\n').append(name);
        }
        return sb;
    }

    static StringBuilder listNamesDetail(StringBuilder sb)
    {
        Map<String, WordLists> listForName = new TreeMap<>();
        for (WordLists wl : values())
        {
            if (wl.hidden())
            {
                continue;
            }
            listForName.put(wl.toString(), wl);
        }
        listForName.forEach((name, list) ->
        {
            sb.append("\n  ")
                    .append(name).append(" (")
                    .append(list.bits()).append(" bits ")
                    .append(nearestPowerOfTwoLessThan(list.size()))
                    .append(" words)");
        });
        return sb;
    }

    static class CommandLineArguments
    {
        final Recipe recipe;
        final List<BoundValue> values;
        final String delimiter;
        final boolean bestFit;
        final boolean verbose;
        final boolean debug;
        final boolean shuffle;

        CommandLineArguments(Recipe recipe, List<BoundValue> values,
                String delimiter,
                boolean bestFit, boolean verbose, boolean debug, boolean shuffle)
        {
            this.recipe = recipe == null
                          ? SENTENCE_LIKE
                          : recipe;
            this.values = values;
            this.delimiter = delimiter == null
                             ? "-"
                             : delimiter;
            this.bestFit = bestFit;
            this.verbose = verbose;
            this.debug = debug;
            this.shuffle = shuffle;
        }

        String delimiter()
        {
            return delimiter == null
                   ? "-"
                   : delimiter;
        }

        int bits()
        {
            int result = 0;
            for (BoundValue bv : values)
            {
                result += bv.bits();
            }
            return result;
        }

        Recipe recipe()
        {
            Recipe result;
            if (bestFit)
            {
                result = best(bits());
            }
            else
                if (recipe != null)
                {
                    result = recipe;
                }
                else
                {
                    result = SENTENCE_LIKE;
                }
            if (verbose)
            {
                System.err.println("Using recipe " + result + " of "
                        + result.bits() + " bits for input of "
                        + bits() + " bits");

                if (bits() > result.bits())
                {
                    System.err.println("Input has more bits " + bits()
                            + ") than recipe can handle (" + recipe.bits()
                            + ") - it will repeat partially or completely.");
                }
            }
            if (debug)
            {
                for (Recipe r : recipes())
                {
                    System.out.println(r + " " + r.bits() + " bits");
                }
            }
            return result;
        }

        BoundValue[] values()
        {
            return values.toArray(BoundValue[]::new);
        }
    }
}
