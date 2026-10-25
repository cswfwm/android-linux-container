#!/bin/sh
# Gradle Wrapper Script
PRGDIR=`dirname "$0"`
GRADLE_WRAPPER_JAR="$PRGDIR/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
    echo "Gradle wrapper jar not found!"
    exit 1
fi

exec java -Xmx1g -Xms1g -jar "$GRADLE_WRAPPER_JAR" "$@"