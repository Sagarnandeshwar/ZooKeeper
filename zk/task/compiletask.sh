#!/bin/bash

# Make everything relative to current file location
project_root=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )/../..
project_root=$(realpath "$project_root")

cd "$project_root"/zk/task || exit
javac ./*.java
