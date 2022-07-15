#!/bin/bash

mvn -P sign-artifacts -P attach-jars javadoc clean deploy
