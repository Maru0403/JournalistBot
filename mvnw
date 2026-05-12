#!/bin/sh
# Maven Wrapper Script
# Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.

if [ -z "$JAVA_HOME" ] ; then
  JAVACMD=`which java`
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-"$(pwd)"}"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
DOWNLOAD_URL=$(grep 'wrapperUrl' "$MAVEN_WRAPPER_PROPERTIES" | sed 's/.*=//')

if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
  echo "Downloading Maven wrapper jar..."
  curl -o "$MAVEN_WRAPPER_JAR" "$DOWNLOAD_URL" 2>/dev/null || \
  wget -O "$MAVEN_WRAPPER_JAR" "$DOWNLOAD_URL" 2>/dev/null
fi

exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$MAVEN_WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
