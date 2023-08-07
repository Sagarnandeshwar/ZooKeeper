#!/bin/bash
# Shortcut script to start the instance of zookeeper on this PC.

# Setup environment
project_root=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
project_root=$(realpath "$project_root")
source "$project_root"/zkEnsemble.sh

cd "$ZOOBINDIR"/../"$(hostname)" || exit
/bin/bash "$ZOOBINDIR"/zkServer.sh stop zoo-base.cfg
