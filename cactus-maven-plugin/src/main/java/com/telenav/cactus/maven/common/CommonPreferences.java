package com.telenav.cactus.maven.common;

import com.telenav.cactus.cactus.preferences.Preference.StringPreference;

/**
 *
 * @author Tim Boudreau
 */
public enum CommonPreferences implements StringPreference
{

    DEVELOPMENT_BRANCH(CactusCommonPropertyNames.DEFAULT_DEVELOPMENT_BRANCH),
    STABLE_BRANCH(CactusCommonPropertyNames.DEFAULT_STABLE_BRANCH);

    private final String defaultValue;

    CommonPreferences(String def)
    {
        defaultValue = def;
    }

    @Override
    public String defaultValue()
    {
        return defaultValue;
    }

}
