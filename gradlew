#!/bin/sh
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then PRG="$link"
  else PRG=`dirname "$PRG"`"/$link"; fi
done
APP_HOME="`cd "$(dirname "$PRG")" && pwd -P`"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
if [ -n "$JAVA_HOME" ]; then JAVACMD="$JAVA_HOME/bin/java"
else JAVACMD="java"; fi
eval set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \"-Dorg.gradle.appname=gradlew\" -classpath "\"$CLASSPATH\"" org.gradle.wrapper.GradleWrapperMain '"$@"'
exec "$JAVACMD" "$@"
