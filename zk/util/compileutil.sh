#!/bin/bash

# Make everything relative to current file location
project_root=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )/../..
project_root=$(realpath "$project_root")
# Setup environment
source "$project_root"/zkEnsemble.sh

if [[ -z "$ZOOBINDIR" ]]
then
    echo "Error!! ZOOBINDIR is not set" 1>&2
    exit 1
fi

# Detect shell type
case "$(uname)" in
    CYGWIN*|MINGW*) cygwin=true ;;
    *) cygwin=false ;;
esac

taskDependency="$project_root"/zk/task
cd "$project_root"/zk/util || exit
if $cygwin; then
    # Windows paths and classpath format
    taskDependency=$( cygpath -wp "$taskDependency" )
    #echo javac -cp "$CLASSPATH;$taskDependency;.;" *.java
    javac -cp "$CLASSPATH;$taskDependency;.;" ./*.java
else
    # Linux paths and classpath format
    #echo javac -cp "$CLASSPATH:$taskDependency:." *.java
    javac -cp "$CLASSPATH:$taskDependency:.:" ./*.java
fi
