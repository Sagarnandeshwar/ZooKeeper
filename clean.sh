#!/bin/bash

# Make everything relative to current file location
project_root=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
project_root=$(realpath "$project_root")

# Delete all binaries
rm -f "$project_root"/zk/clnt/*.class
rm -f "$project_root"/zk/dist/*.class
rm -f "$project_root"/zk/task/*.class
rm -f "$project_root"/zk/util/*.class
