#!/bin/sh
# Gradle wrapper. Запускает Gradle 8.9 из дистрибутива, указанного
# в gradle/wrapper/gradle-wrapper.properties.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
exec java -Xmx64m -Xms64m -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
