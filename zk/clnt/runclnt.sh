#!/bin/bash

# Make everything relative to current file location
project_root=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )/../..
project_root=$(realpath "$project_root")
# Setup environment
source "$project_root"/zkEnsemble.sh

if [[ -z "$ZOOBINDIR" ]]; then
	echo "Error!! ZOOBINDIR is not set" 1>&2
	exit 1
fi

if [[ $# -lt 1 ]]; then
    echo "usage: runclnt.sh <taskMagnitude> <client launcher (optional)>"
    exit 1
fi

# Detect shell type
case "$(uname)" in
    CYGWIN*|MINGW*) cygwin=true ;;
    *) cygwin=false ;;
esac

# Decide on which DistClient to run
client_launcher="DistClient"
if [[ $# -eq 2 ]]; then
    client_launcher="$2"
fi

# Final prompt before starting
echo "About to start client with:"
echo "  environment = $(uname)"
echo "  launcher = $client_launcher"
echo "  taskMagnitude = $1"
read -r -n 1 -p $"Start with these parameters (y/n)? " userInput
echo
if [[ "$userInput" != "y" && "$userInput" != "Y" ]]; then
  echo "Cancelled"
  exit 1
fi

taskDependency="$project_root"/zk/task
cd "$project_root"/zk/clnt || exit
if $cygwin; then
    # Windows paths and classpath format
    taskDependency=$( cygpath -wp "$taskDependency" )
    #echo java -cp "$CLASSPATH;$taskDependency;.;" "$client_launcher" "$1"
    java -cp "$CLASSPATH;$taskDependency;.;" "$client_launcher" "$1"
else
    # Linux paths and classpath format
    #echo java -cp "$CLASSPATH:$taskDependency:.:" "$client_launcher" "$1"
    java -cp "$CLASSPATH:$taskDependency:.:" "$client_launcher" "$1"
fi
