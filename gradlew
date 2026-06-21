#!/usr/bin/env sh

# -----------------------------------------------------------------------------
# Minimal Gradle wrapper launcher.
# NOTE: This launcher requires gradle/wrapper/gradle-wrapper.jar to exist.
# -----------------------------------------------------------------------------

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: Missing $WRAPPER_JAR"
  echo "Restore it by running: gradle wrapper"
  exit 1
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="java"
fi

exec "$JAVA_CMD" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
