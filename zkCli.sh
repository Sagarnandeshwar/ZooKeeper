#!/bin/bash
# Shortcut command to launch the CLI to the zk ensemble specified in zkEnsemble.sh

# Setup environment
project_root=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
project_root=$(realpath "$project_root")
source "$project_root"/zkEnsemble.sh

# Launch the CLI
echo "$ZOOBINDIR"/zkCli.sh -server "$ZKSERVER"
/bin/bash "$ZOOBINDIR"/zkCli.sh -server "$ZKSERVER"
