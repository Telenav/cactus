package com.telenav.tdk.build.metadata;

/**
 * @author jonathanl (shibo)
 */
class BuildName
{
    private static final String[] nouns = new String[]
            {
                    "monkey", "gorilla", "tornado", "rhino", "rabbit", "dog", "turtle", "goat", "dinosaur",
                    "shark", "snake", "bunny", "marmot", "star", "alpaca", "panda", "hamster",
                    "hedgehog", "kangaroo", "crocodile", "duckling", "hippo", "dolphin", "owl", "seal",
                    "piglet", "penguin", "truck", "sneakers", "dracula", "trebuchet", "chameleon", "lizard",
                    "donkey", "koala", "otter", "cat", "wombat", "beachball", "capybara", "buffalo",
                    "frog", "mouse", "telephone", "laptop", "toaster", "waffle", "bobblehead", "crayon",
                    "sunglasses", "light-bulb", "water-wings", "shoes", "bongos", "goldfish", "legos", "tulips",
                    "dune-buggy", "torpedo", "rocket", "diorama", "beanbag", "radio", "banana"
            };

    private static final String[] adjectives = new String[]
            {
                    "blue", "sparkling", "orange", "puffy", "beryllium", "plutonium", "mango", "cobalt", "purple",
                    "tungsten", "yellow", "happy", "transparent", "pink", "aqua", "lavender", "alabaster", "laughing",
                    "lemon", "tangerine", "golden", "silver", "bronze", "amber", "ruby", "goldenrod", "khaki", "violet",
                    "lime", "steel", "red", "ceramic", "platinum", "carbon", "navy", "stretchy", "nickel", "copper",
                    "funky", "aluminum", "tungsten", "chrome", "lead", "radium", "zinc", "iron", "charcoal", "titanium",
                    "angry", "chocolate", "turquoise", "cerulean", "apricot", "green", "maroon", "blasé",
                    "grumpy", "cornflower", "chartreuse", "neon", "mustard", "rubber", "paper", "plastic"
            };

    /**
     * @return The name for the given build number, like "sparkling piglet"
     */
    static String name(final int buildNumber)
    {
        final var noun = nouns[buildNumber % nouns.length];
        final var adjective = adjectives[(buildNumber / nouns.length) % adjectives.length];
        return adjective + " " + noun;
    }
}
