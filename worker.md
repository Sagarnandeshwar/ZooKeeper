# Worker
## Initialisation
See comments in `Worker#Worker(ZooKeeper, String)` and `Worker#init()`. Copy-pasted below:

`Worker-xxxx` initialises by setting an __exists() watch__ on their assignments at the (currently nonexistent) __/dist50/assignments/worker-xxxx__, hereby called "assignment-node". __Only then__ can Workers let the Master know they joined by creating the node __/dist50/workers/worker-xxxx__, which the Master has a watch on. This is to avoid a case where the master immediately assigns a task to the worker before the worker has had time to set a watch on its assignments.

There is a slight chicken-and-egg problem where. To set a watch on its assignments, `worker-xxxx` must know ts ID (`xxxx`). To get `xxxx`, the worker has to create a sequential node "worker-?", but if it creates that node in __/dist50/workers__, the master will be notified, and we run in the problem in the above paragraph. To solve this, to get `xxxx`, the worker first creates a sequential node in __/dist50/trash__ to get `xxxx` without notifying the master. We could have also used `processInfo`, but it has no uniqueness guarantees as unlikely as it is to actually be a problem.

## Assignment Watcher Loop
Assignments (__/dist50/assignments/worker-xxxx__) are ONLY changed from two places: 
1. A node (assignment) is added by the master to assign a task to a worker. 
2. A node (assignment) is deleted by the worker's computation thread.
   * __Ignored by the worker__ (delete event) 

So, get its assignments, workers have a watcher-loop on its own assignment-node. When the Master assigns it something, it launches a new __computation thread__ that will take care of the computation.

### Computation Thread
All operations are synchronous as there's no point in making them asynchronous.

Gets the task by getting the assignment-node's data, retrieve the task by reading the data of the task node, deserialize, compute, serialize and write. Once done, the computation thread will take care of removing the worker's assignment node, which will be detected by the Master who will know the worker is idle again.
