#!/usr/bin/env bash
set -Eeuo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
jar="$root/gradle/wrapper/gradle-wrapper.jar"
printf '%s  %s\n' 81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f "$jar" | sha256sum -c -
java_cmd=java
if [[ -n ${JAVA_HOME:-} ]]; then java_cmd="$JAVA_HOME/bin/java"; fi
exec "$java_cmd" -Dorg.gradle.appname=gradlew -classpath "$jar" org.gradle.wrapper.GradleWrapperMain "$@"
