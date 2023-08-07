#!/bin/bash
# Shortcut script to compile everything.
# Setup environment
BASEDIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
BASEDIR=$(realpath "$BASEDIR")

echo "1. Compiling task..."
cd "$BASEDIR"/zk/task/ || exit
/bin/bash "$BASEDIR"/zk/task/compiletask.sh
echo "2. Compiling client..."
cd "$BASEDIR"/zk/clnt/ || exit
/bin/bash "$BASEDIR"/zk/clnt/compileclnt.sh
echo "3. Compiling server..."
cd "$BASEDIR"/zk/dist/ || exit
/bin/bash "$BASEDIR"/zk/dist/compilesrvr.sh
echo "4. Compiling utils..."
cd "$BASEDIR"/zk/util/ || exit
/bin/bash "$BASEDIR"/zk/util/compileutil.sh
