# Master

## Data structures
The Master keeps track of all tasks it has ever seen in a set `processedTasks`. This is used to check for _new_ tasks at each invocation of the new task watcher-loop callback.

The currently pending (queued but not assigned yet) tasks are stored in a queue `pendingTasks`, Once they are assigned, they leave the queue and move onto the Map. __The queue ensures FIFO task assignment__

The workers and their assignments are stored in a `Map<Worker, Assignment>`. If `Assignment` is null, we consider the worker to be idle. We use this map to both detect _new_ workers and to detect changes in assignments (worker completes task).

All updates / iterations through the data structures are synchronized.

## Initialisation
The Master initialises by starting all 3 watcher-loops (calls to `getChildren()`). The first invocation to the callbacks will build the Master's internal data structures. This way, the Master can handle a system where workers or tasks were already present.

## Assignment rounds
In an assignment round, we try to assign as many tasks from the queue as possible, stopping when either we run out of tasks or idle workers. An assignment round is only triggered under 3 situations:
* A new task is added (in case there are idle workers)
* A new worker is added (in case there are pending tasks)
* A worker becomes idle (in case there are pending tasks)

## New worker watcher-loop
We watch for new workers by watching for changes to children of __/dist50/workers__. Once the watcher trips, we call `getChildren()` and compute the difference between the Map (`workers`) and the children to determine which workers _just joined_/left. Workers that joined are then added in the Map with null value (meaning idle). If there are new workers, we initiate an assignment round.

## New task watcher-loop
We watch for new tasks by watching for changes in children of __/dist50/tasks__. Once the watcher trips, we call `getChildren()` and compute the difference between the previously seen tasks (`processedTasks`) and the children to determine which tasks are _new_ so that we can try to enqueue them in `pendingTasks`. If there are new tasks, we initiate an assignment round.

## Assignments change watcher-loop
Assignments (__/dist50/assignments/worker-xxxx__) are ONLY changed from two places:
1. A node (assignment) is added by the master to assign a task to a worker.
   * __Ignored by the master__ (There will be no difference between the Map and the children)
2. A node (assignment) is deleted by the worker's computation thread.

We watch for workers who have completed their tasks (and deleted their assignment-node) by listening to children of __/dist50/assignments__. Once the watcher trips, we call `getChildren()` and compute the difference between the assignment map (`workers`) and the children. If a worker is marked __busy__ in the Map (`value == null`) but isn't in the children (meaning idle), then it must mean it just finished and became idle and we update the Map.

If there are newly idle workers, we initiate an assignment round.
