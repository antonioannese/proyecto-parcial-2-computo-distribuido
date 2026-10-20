#!/bin/zsh
# Localiza el JDK y define ROOT, JAVA y JAVAC.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ROOT

if [ -x /opt/homebrew/opt/openjdk/bin/java ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk"
elif [ -x /usr/local/opt/openjdk/bin/java ]; then
  export JAVA_HOME="/usr/local/opt/openjdk"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  :
else
  unset JAVA_HOME
fi

if [ -n "${JAVA_HOME:-}" ]; then
  JAVA="$JAVA_HOME/bin/java"
  JAVAC="$JAVA_HOME/bin/javac"
else
  JAVA="$(command -v java || true)"
  JAVAC="$(command -v javac || true)"
fi

if [ -z "$JAVA" ] || [ -z "$JAVAC" ] || [ ! -x "$JAVA" ] || [ ! -x "$JAVAC" ]; then
  echo "No se encontró un JDK. Instale Java 11 o superior e inténtelo de nuevo." >&2
  exit 1
fi

export JAVA JAVAC
