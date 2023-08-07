#!/bin/bash
# Shortcut script to launch an instance of the client.
project_root=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
project_root=$(realpath "$project_root")

/bin/bash "$project_root"/zk/clnt/runclnt.sh "$@"
