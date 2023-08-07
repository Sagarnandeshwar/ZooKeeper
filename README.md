# COMP512 P3

## Create these files yourself:
`zkEnsemble.sh`
 * Edit content to your current configuration. All other sh files depend on it.
 * To be put in the root directory.
 * Ignored by git for portability
```bash
#!/bin/bash

# (Local) Zookeeper binary folder and environment
if [[ -z "$ZOOBINDIR" ]]
then
    export ZOOBINDIR=~/Desktop/apache-zookeeper-3.6.2-bin/bin # [CHANGE THIS]
    . "$ZOOBINDIR"/zkEnv.sh
fi

# Hostnames of the ZK ensemble nodes
# [remove node1/2 if running standalone ZK at home]
node1="lab2-XX"
node2="lab2-YY"
node3="lab2-ZZ"

# Addresses of the ZK ensemble nodes
# [remove node1/2 if running standalone ZK at home]
node1Address="$node1.cs.mcgill.ca"
node2Address="$node2.cs.mcgill.ca"
node3Address="$node3.cs.mcgill.ca"

# Ports of the ZK ensemble nodes
# [remove node1/2 if running standalone ZK at home]
node1Port="21850"
node2Port="21850"
node3Port="21850"

# [remove node1/2-related if running standalone ZK at home]
export nodesList="$node1,$node2,$node3"
export ZKSERVER="$node1Address:$node1Port,$node2Address:$node2Port,$node3Address:$node3Port"

# Unset this to remove prints relating to watcher loops.
export LOOP_PRINT_50="true"

```

## Scripts
Unless otherwise noted, all scripts can be run from anywhere and depend on `zkEnsemble.sh`
being setup correctly to function.

| command | description |
| --- | --- |
| `clean.sh` | Remove all binaries. Doesn't depend on `zkEnsemble.sh` |
| `compileAll.sh` | Compiles Task, Client, Server and Util. |
| `resetZK.sh` | __(Run this before launching any servers)__ Launches a helper program that will attempt to reset the ZK configuration to the initial state. |
| `runServer.sh` | Starts a server that will connect to the configuration in `zkEnsemble.sh` |
| `runClient.sh` | `runClient.sh <task_magnitude> <launcher>` Start a client that will connect to the configuration in `zkEnsemble.sh`.`<launcher>` is optional, it allows using a custom client launcher. |
| `startZK.sh` | Start the ZK node on this computer. Only call this if relevant. __ZK folder and conf must be setup correctly__ |
| `stopZK.sh` | Stops the ZK node on this computer. Only call this if relevant. __ZK folder and conf must be setup correctly__ |
| `zkCli.sh` | Opens a terminal to the CLI of the ZK ensemble specified in `zkEnsemble.sh` |

### Technicalities
Scripts were made to work on linux and windows (CYGWIN/MINGW64). I'm not a mac user so idk what's
going wrong, but they don't work on Yiwei's mac for whatever reason. If you're on Windows and use
__Git Bash__, you have to run git bash in __MINGW64__ mode and __NOT__ the default __SYSE2__ mode
(launch bash from _%PROGRAMFILES%/Git/bin/bash.exe_ instead of the usual
_%PROGRAMFILES%/Git/usr/bin/bash.exe_). IJ already launches bash in MINGW64 mode, but the "Open
in Git Bash" context menu and Windows Terminal launch in SYSE2 by default.

## Setting up a standalone ZK ensemble locally for testing
On the host (single) computer, follow the instructions on page 1 and 2 of the setup pdf __BUT__:

_Step 3_. Only create one data folder, with host name as folder name (`$(hostname)`). \
_Step 5_. Only change `clientPort=21850` and `dataDir=./data`. \
_Step 6_. Unnecessary

For start and shutdown, just use the helper scripts (given that `zkEnsemble.sh` is set up correctly).
