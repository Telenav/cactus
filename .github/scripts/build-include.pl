#!/usr/bin/perl

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

use strict;
use warnings;

my $WORKSPACE = `pwd`;
$WORKSPACE =~ s/\n+//g;

my $KIVAKIT_HOME = "$WORKSPACE/kivakit";
my $MESAKIT_HOME = "$WORKSPACE/mesakit";

$ENV{$KIVAKIT_HOME} = $KIVAKIT_HOME;
$ENV{$MESAKIT_HOME} = $MESAKIT_HOME;

#
# Print the given text to the console
#

sub say
{
    my ($text) = @_;

    print "===> $text\n";
    return $text;
}

#
# Returns true if the given value is not defined or is the empty string
#

sub is_empty
{
    my ($value) = @_;

    return !defined $value || $value eq "";
}

#
# Runs the given command and returns true if is is successful
#

sub run
{
    my ($command) = @_;

    say("Executing: $command");
    return !system($command);
}

#
# The name of the given repository
#
#     https://github.com/Telenav/kivakit ==> kivakit
#

sub repository_name
{
    my ($repository) = @_;

    if ($repository =~ m/([^\/]+)$/)
    {
        return $1;
    }

    die "Not a valid repository: $repository";
}

#
# Returns true if the given branch of the given repository exists
#

sub branch_exists
{
    my ($repository, $branch) = @_;

    clone_branch($repository, $branch);

    my $repository_name = repository_name($repository);
    my $folder = "$WORKSPACE/$repository_name";
    if (-d "$folder")
    {
        my $output = `cd $folder && git branch --list $branch`;
        if (is_empty($output))
        {
            say("Branch $repository:$branch does not exist: $output");
        }
        return $output;
    }
    return 0;
}

#
# Checks the given repository branch to ensure that it exists in the given repository
#

sub check_branch
{
    my ($repository, $branch) = @_;

    die "Must supply a branch for repository $repository" if is_empty($branch);
    die "Branch does not exist: $repository:$branch" if (!branch_exists($repository, $branch));

    say("Validated $repository:$branch");

    return $branch;
}

#
# Checks the build type (either "package" or "publish")
#

sub check_build_type
{
    my ($build_type) = @_;

    die "Must supply build type (package or publish)" if (is_empty($build_type));
    die "Unrecognized build type: $build_type" if (!($build_type eq "package" || $build_type eq "publish"));

    return $build_type;
}

#
# Check the given repository (it must start with https://github.com/)
#

sub check_repository
{
    my ($repository) = @_;

    die "Must supply a repository" if ($repository eq "");
    die "Must supply a github repository URL" if (index($repository, "https://github.com/") != 0);

    return $repository;
}

#
# Gets the name of a branch from the GitHub "ref":
#
#     refs/heads/develop ==> develop
#     refs/heads/feature/lasers ==> feature/lasers
#     refs/pull/13/merge ==> pull/13
#

sub reference_to_branch
{
    my ($reference) = @_;

    if (is_empty($reference))
    {
        return $reference;
    }

    my $branch = $reference;
    $branch =~ s!refs/heads/!!;

    if ($branch =~ m!.*/(\d+)/.*!)
    {
        $branch = "pull/$1";
    }

    return $branch;
}

sub show_github_variables
{
    say("GITHUB_REF = $ENV{'GITHUB_REF'}");
    say("GITHUB_BASE_REF = $ENV{'GITHUB_BASE_REF'}") if !is_empty($ENV{'GITHUB_BASE_REF'});
    say("GITHUB_HEAD_REF = $ENV{'GITHUB_HEAD_REF'}") if !is_empty($ENV{'GITHUB_HEAD_REF'});
}

#
# Returns the branch that our action is building (pull request, feature branch, master or develop)
#

sub build_branch
{
    return reference_to_branch($ENV{'GITHUB_REF'});
}

#
# Returns the branch that our action's branch is based on (will ultimately merge to)
#

sub destination_branch
{
    return reference_to_branch($ENV{'GITHUB_BASE_REF'});
}

#
# Returns the pull request branch we are building (if any)
#

sub pull_request_branch
{
    return reference_to_branch($ENV{'GITHUB_HEAD_REF'});
}

#
# Returns true if our action is building a pull request
#

sub is_pull_request
{
    my ($branch) = @_;

    return index($branch, "pull") == 0;
}

#
# Returns the branch that should be used for the given repository and type
#

sub branch
{
    my ($repository, $repository_type) = @_;
    my $is_pull_request = is_pull_request(build_branch());

    # If the repository is a dependency (it's not the branch our action is building),
    if ($repository_type eq "dependency")
    {
        # and we're working on a pull request,
        if ($is_pull_request != 0)
        {
            # and there's a matching pull request branch in this dependent repository,
            if (branch_exists($repository, pull_request_branch()))
            {
                # then return that branch,
                return pull_request_branch();
            }
            else
            {
                # otherwise, return the destination branch for the pull request.
                return check_branch($repository, destination_branch());
            }
        }
        # If we're working on a normal branch,
        else
        {
            # and a matching build branch exists in this dependent repository,
            if (branch_exists($repository, build_branch()))
            {
                # then return that branch.
                return build_branch();
            }
            else
            {
                # otherwise, return the "develop" branch as a default.
                return check_branch($repository, "develop");
            }
        }
    }
    # If the repository is the one we're building,
    elsif ($repository_type eq "build")
    {
        # and it's a pull request,
        if ($is_pull_request != 0)
        {
            # then return that branch,
            return check_branch($repository, pull_request_branch());
        }

        # otherwise return the build branch.
        return check_branch($repository, build_branch());
    }
    else
    {
        die "Unrecognized repository type: $repository_type";
    }
}

#
# Install the pom.xml file in the current folder
#

sub install_pom
{
    my ($folder) = @_;

    say("Installing super POM in $folder");
    die "Cannot install super pom" if !run("cd $folder && mvn --batch-mode --no-transfer-progress clean install");
}

sub clone_branch
{
    my ($repository, $branch) = @_;
    my $repository_name = repository_name($repository);

    if (!-d "$WORKSPACE/$repository_name")
    {
        say("Cloning $repository:$branch");
        return run("cd $WORKSPACE && git clone --branch $branch --quiet $repository");
    }

    say("Branch $repository:$branch already exists");
    return 1;
}

#
# Clones the given repository and branch
#

sub clone
{
    my ($repository, $repository_type) = @_;
    check_repository($repository);
    my $repository_name = repository_name($repository);
    my $branch = branch($repository, $repository_type);

    # If the branch is a pull request,
    if (is_pull_request($branch))
    {
        # then check out the branch as a pull request,
        if (!branch_exists($repository, "pull-request"))
        {
            die "Cannot clone pull request $branch" if !run("cd $WORKSPACE/$repository_name && git fetch origin '$branch/head:pull-request' && git checkout pull-request");
        }
    }
    else
    {
        # otherwise, simply check out the branch.
        if (!branch_exists($repository, $branch))
        {
            die "Cannot checkout $repository:$branch" if !run("cd $WORKSPACE/$repository_name && git checkout $branch");
        }
    }
}

#
# Build project
#

sub build
{
    my ($build_type, $folder) = @_;
    my $passphrase = $ENV{"secrets.OSSRH_GPG_SECRET_KEY_PASSWORD"};
    check_build_type($build_type);

    say("Building $folder ($build_type)");

    if ($build_type eq "package")
    {
        die "Package build failed" if !run("cd $folder && mvn -Dmaven.javadoc.skip=true -DKIVAKIT_DEBUG='!Debug' -P shade -P tools --no-transfer-progress --batch-mode clean install");
    }
    elsif ($build_type eq "publish")
    {
        die "Publish failed" if !run("cd $folder && mvn -P attach-jars -P sign-artifacts -P shade -P tools --no-transfer-progress --batch-mode -Dgpg.passphrase='$passphrase' clean deploy");
    }
    else
    {
        die "Unrecognized build type: $build_type"
    }
}

sub build_kivakit
{
    my ($build_type) = @_;
    check_build_type($build_type);

    install_pom("$KIVAKIT_HOME/superpom");
    build($build_type, "$KIVAKIT_HOME");
}

sub build_kivakit_extensions
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/kivakit-extensions");
}

sub build_kivakit_examples
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/kivakit-examples");
}

sub build_lexakai
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/lexakai");
}

sub build_lexakai_annotations
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/lexakai-annotations");
}

sub build_mesakit
{
    my ($build_type) = @_;
    check_build_type($build_type);

    say("Installing shape file reader");
    die "Unable to install shape file reader" if !run("mvn install:install-file -Dfile='$WORKSPACE/mesakit/mesakit-map/geography/libraries/shapefilereader-1.0.jar' -DgroupId=org.nocrala -DartifactId=shapefilereader -Dversion=1.0 -Dpackaging=jar");

    install_pom("$MESAKIT_HOME/superpom");
    build($build_type, $MESAKIT_HOME);
}

sub build_mesakit_extensions
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/mesakit-extensions");
}

sub build_mesakit_examples
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/mesakit-examples");
}

