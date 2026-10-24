#!/usr/bin/env bash
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME="/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home"
fi
exec "$JAVA_HOME/bin/java" -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
