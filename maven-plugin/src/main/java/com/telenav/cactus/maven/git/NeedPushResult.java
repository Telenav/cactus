package com.telenav.cactus.maven.git;

/**
 *
 * @author Tim Boudreau
 */
public enum NeedPushResult
{
    NOT_ON_A_BRANCH, REMOTE_BRANCH_DOES_NOT_EXIST, YES, NO;

    static NeedPushResult of(boolean result)
    {
        return result ? YES : NO;
    }

    public boolean canBePushed()
    {
        return this == YES || this == REMOTE_BRANCH_DOES_NOT_EXIST;
    }

    public boolean needCreateBranch()
    {
        return this == REMOTE_BRANCH_DOES_NOT_EXIST;
    }

}
