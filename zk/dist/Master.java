import org.apache.zookeeper.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class representing a Master. See master.md for more details.
 */
public class Master {
	private final ZooKeeper zk;
	/** Set of ALL tasks that have been "processed" (queued and are either waiting or finished). */
	private final Set<String> processedTasks = new HashSet<>();
	/** Queue of all tasks that are currently WAITING but have NOT been assigned to a worker yet. */
	private final Deque<String> pendingTasks = new ConcurrentLinkedDeque<>();
	/** Keeps track of all workers and their assigned tasks (local). */
	private final Map<String, String> workerMap = new HashMap<>();

	public Master(ZooKeeper zk) {
		String sig = Col.B_CYAN.fg("Master/constructor") + " : ";
		this.zk = zk;
		System.out.println(sig + "Constructed and ready for init.");
	}

	/**
	 * Initialises the Master by getting a list of all current workers and tasks in the ZK ensemble
	 * (in case they were already there). Basically starts each of the "watcher loops".
	 */
	public void init() {
		String sig = Col.B_CYAN.fg("Master/init : ");
		System.out.println(sig + "Initializing...");
		new Thread(commandHandler).start();
		// Initialise list of workers (they WON'T fail and won't shut down unless ordered to)
		getWorkers(); // Async to not block
		getTasks(); // Async to not block
		getAssignments(); // Async to not block
		System.out.println(sig + "Initialization done.");
	}

	private final Runnable commandHandler = () -> {
		String sig = Col.RED.fg("Worker/commandListener") + " : ";
		Scanner sc = new Scanner(System.in);

		while (!Thread.currentThread().isInterrupted()) {
			try {
				String cmd = sc.nextLine();
				if ("".equalsIgnoreCase(cmd)) continue;
				if ("stop".equalsIgnoreCase(cmd)) System.exit(0); // Runs shutdown hook to disconnect
					// List all currently pending tasks
				else if ("ls tasks".equalsIgnoreCase(cmd)) {
					synchronized (this) {
						System.out.println("Currently pending tasks queue: [");
						for (String task : pendingTasks) System.out.printf("  [%s]\n", task);
						System.out.println("]");
					}
				}
				// List all current assignments
				else if ("ls workers".equalsIgnoreCase(cmd)) {
					synchronized (this) {
						System.out.println("Current worker assignments: [");
						for (Map.Entry<String, String> worker : workerMap.entrySet()) {
							System.out.printf("  [%s] -> %s\n", worker.getKey(),
								worker.getValue() == null
									? Col.GREEN.fg("Idle")
									: "[" + Col.BLUE.fg(worker.getValue()) + "]");
						}
						System.out.println("]");
					}
				}
				else {
					System.out.println(sig + Col.RED.fg("Unknown command \"" + cmd + "\""));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	/* ===================================== NEW WORKER MANAGEMENT "LOOP" ===================================== */

	private void getWorkers() {
		String sig = Col.MAGENTA.fg("Master/getWorkers") + " : ";
		zk.getChildren("/dist50/workers", workersChangeWatcher, workersChangeCB, null);
		if (DistProcess.LOOP_PRINT) System.out.println(sig + "workersChangeWatcher and workersChangeCB set");
	}

	/** Watcher-loop on /dist50/workers allows handling new workers connecting. */
	private final Watcher workersChangeWatcher = (event) -> {
		String sig = Col.MAGENTA.fg("Master/workersChangeWatcher") + " : ";
		if (DistProcess.LOOP_PRINT) System.out.println(sig + "Tripped with > " + event.getType());

		switch (event.getType()) {
			case NodeChildrenChanged: // Potential new worker, allow callback and watcher loop
				getWorkers(); // Renew loop
				break;
			case None: // Connection closed?
				if (event.getState() == Watcher.Event.KeeperState.Closed) {
					System.out.println(sig + Col.RED.fg("Disconnect: terminating workers \"watcher loop\""));
					return; // Don't renew loop
				}
			default: // Something wrong happened
				System.err.printf(sig + Col.RED.bg("An unexpected event has occurred!")
						+ " [type] = %s | [state] = %s | [path] = %s\n",
					event.getType(), event.getState(), event.getPath());
				throw new RuntimeException();
		}

	};

	/**
	 * Upon a change to the workers list, compare internal worker map and ZK workers (children) to
	 * compute which workers left and which or joined. If there are any changes, we initiate a
	 * round of task assignment.
	 */
	private final AsyncCallback.ChildrenCallback workersChangeCB = (rc, path, ctx, children) -> {
		String sig = Col.MAGENTA.bg("Master/workersChangeCB") + " : ";
		System.out.println(sig + "Callback with > " + KeeperException.Code.get(rc) + " : " + path + " : " + ctx + " : " + children);

		// Connection dropped or some other error
		if (KeeperException.Code.get(rc) != KeeperException.Code.OK)
			System.err.println(sig + Col.RED.bg("Error occurred! " + KeeperException.Code.get(rc).name()));

		int added = 0;
		AtomicInteger removed = new AtomicInteger();
		synchronized (this) {
			System.out.println(sig + "Processing worker changes...");

			// Add all new untracked workers.
			for (String worker : children) {
				if (!workerMap.containsKey(worker)) {
					System.out.println(sig + Col.GREEN.fg("  Added worker [" + worker + "]"));
					workerMap.put(worker, null); // No tasks currently assigned.
					added++;
				}
			}

			// Remove all workers that have left (NOT IN REQUIREMENTS but here for easier testing)
			if (children.size() < workerMap.size()) {
				Set<String> childrenSet = new HashSet<>(children);
				workerMap.entrySet().removeIf( (entry) -> {
					if (!childrenSet.contains(entry.getKey())) {
						String task = entry.getValue();
						System.out.println(sig + Col.RED.fg +
							"  Removing [" + entry.getKey() + "]" + (task != null
							? " with assigned task [" + task + "]" : "") + Col.RESET);
						if (task != null)
							System.out.println(sig + "  Aborted task re-enqueuing would happen here (not implemented)");
						removed.getAndIncrement();
						return true;
					}
					return false;
				});
			}
		}

		if (added != 0)
			System.out.println(sig + Col.GREEN.fg(added + " new workers added: start assignment round"));
		if (removed.get() != 0)
			System.out.println(sig + Col.RED.fg(removed + " workers removed: start assignment round"));

		if (added == 0 && removed.get() == 0)
			System.out.println(sig + Col.YELLOW.fg("No changes"));
		else {
			// Only launch task assignment round if there are changes.
			assignmentRound();
		}
	};

	/* ===================================== NEW TASK MANAGEMENT "LOOP" ===================================== */

	void getTasks() {
		String sig = Col.GRAY.fg("Master/getTasks") + " : ";
		zk.getChildren("/dist50/tasks", tasksChangeWatcher, tasksChangeCB, null);
		if (DistProcess.LOOP_PRINT) System.out.println(sig + "taskChangeWatcher and taskChangeCB set");
	}

	/** Watcher-loop on /dist50/tasks allows handling new tasks being submitted. */
	private final Watcher tasksChangeWatcher = (event) -> {
		String sig = Col.GRAY.fg("Master/tasksChangeWatcher") + " : ";
		if (DistProcess.LOOP_PRINT) System.out.println(sig + "Tripped with > " + event.getType());

		switch (event.getType()) {
			case NodeChildrenChanged: // Potential new task, allow callback and watcher loop
				getTasks(); // Renew loop
				break;
			case None: // Connection closed?
				if (event.getState() == Watcher.Event.KeeperState.Closed) {
					System.out.println(sig + Col.RED.fg("Disconnect: terminating tasks \"watcher loop\""));
					return; // Don't renew loop
				}
			default: // Something wrong happened
				System.err.printf(sig + Col.RED.bg("An unexpected event has occurred!")
						+ " [type] = %s | [state] = %s | [path] = %s\n",
					event.getType(), event.getState(), event.getPath());
				throw new RuntimeException();
		}
	};

	/**
	 * At each change in tasks, we compare all tasks (children) to a set of all the tasks that
	 * we already "processed" (enqueued at any point) to see which tasks are NEW. Then, add them
	 * to the set of processed tasks and enqueue them.
	 * If the callback is from a client removing its task because it finished, this iteration does
	 * nothing.
	 */
	private final AsyncCallback.ChildrenCallback tasksChangeCB = (rc, path, ctx, children) -> {
		String sig = Col.GRAY.bg("Master/tasksChangeCB") + " : ";
		System.out.println(sig + "Callback with > " + KeeperException.Code.get(rc) + " : " + path + " : " + ctx + " : " + children);

		// Connection dropped or some other error
		if (KeeperException.Code.get(rc) != KeeperException.Code.OK) {
			System.err.println(sig + Col.RED.bg("Error occurred! " + KeeperException.Code.get(rc).name()));
		}

		AtomicInteger newTasks = new AtomicInteger();
		synchronized (this) {
			System.out.println(sig + "Processing task changes...");
			// Add all unseen tasks to the queue
			children.forEach((x) -> {
				if (!processedTasks.contains(x)) {
					System.out.println(sig + Col.GREEN.fg("  Queuing task [" + x +"]"));
					processedTasks.add(x);
					pendingTasks.add(x);
					newTasks.getAndIncrement();
				}
			});
		}
		if (newTasks.get() != 0) {
			System.out.println(sig + Col.GREEN.fg(newTasks.get() + " new tasks enqueued: start assignment round."));
			assignmentRound();
		}
		else {
			System.out.println(sig + Col.YELLOW.fg("No new tasks"));
		}
	};

	/* ===================================== ASSIGNMENT MANAGEMENT "LOOP" ===================================== */

	private void getAssignments() {
		String sig = Col.CYAN.fg("Master/getAssignments") + " : ";
		zk.getChildren("/dist50/assignments", assignmentsChangeWatcher, assignmentsChangeCB, null);
		if (DistProcess.LOOP_PRINT) System.out.println(sig + "assignmentsChangeWatcher and assignmentsChangeCB set");
	}

	/** Watcher-loop /dist50/assignments allow handling tasks being completed */
	private final Watcher assignmentsChangeWatcher = (event) -> {
		String sig = Col.CYAN.fg("Master/assignmentsChangeWatcher") + " : ";
		if (DistProcess.LOOP_PRINT) System.out.println(sig + "Tripped with > " + event.getType());

		switch (event.getType()) {
			case NodeChildrenChanged: // Potential new assignment, allow callback and watcher loop
				getAssignments(); // Renew loop
				break;
			case None: // Connection closed?
				if (event.getState() == Watcher.Event.KeeperState.Closed) {
					System.out.println(sig + Col.RED.fg("Disconnect: terminating assignments \"watcher loop\""));
					return; // Don't renew loop
				}
			default: // Something wrong happened
				System.err.printf(sig + Col.RED.bg("An unexpected event has occurred!")
						+ " [type] = %s | [state] = %s | [path] = %s\n",
					event.getType(), event.getState(), event.getPath());
				throw new RuntimeException();
		}

		// Connection dropped or some other error
		if (event.getType() != Watcher.Event.EventType.NodeChildrenChanged) {
			System.err.println(sig + Col.RED.bg + "An error has occurred! " + event.getType());
			throw new RuntimeException();
		}
	};

	/**
	 * Upon a change in the assignments list, compare internal worker assignment map with the list
	 * of assigned workers (children) to determine which workers became free. Recall that upon
	 * completing a task, workers remove their own assignments.
	 * This callback could have been caused by the Master adding a new assignment. In that case,
	 * do nothing as the Master will have already updated the internal map before creating the
	 * assignment in ZK. To prevent cluttering, prints statements accumulated until the end and
	 * only actually printed if enabled to do so.
	 */
	private final AsyncCallback.ChildrenCallback assignmentsChangeCB = (rc, path, ctx, children) -> {
		String sigColor = "/sigCol/";
		String sig = sigColor + ("Master/assignmentsChangeCB") + Col.RESET + " : ";
		String msg = "";
		msg += (sig + "Callback with > " + KeeperException.Code.get(rc) + " : " + path + " : " + ctx + " : " + children + "\n");
		/*
		 * The children are the only nodes that are assigned anything. All other workers are idle.
		 * So, use children list to update our local assignment Map.
		 */
		msg += (sig + "Updating internal assignment map...\n");
		int newlyIdle = 0;
		Set<String> assignments = new HashSet<>(children);
		synchronized (this) {
			for (Map.Entry<String, String> worker : workerMap.entrySet()) {
				// Worker is "idle" according to ZK
				if (!assignments.contains(worker.getKey())) {
					// Yet, worker is marked as "busy" internally
					// ==> JUST BECAME IDLE (it removed itself from assignments)
					// So, we update our internal worker map entry.
					if (worker.getValue() != null) {
						msg += String.format(sig + Col.GREEN.fg("  Worker [%s] finished [%s] and became idle.\n")
							, worker.getKey(), worker.getValue());
						worker.setValue(null);
						newlyIdle++;
					}
				}
				// Worker is "busy" according to ZK
				else {
					// Yet, worker is marked as "idle" internally
					// ==> IMPOSSIBLE
					if (worker.getValue() == null) {
						// Worker is not idle, but we saved that it was. This should not happen as
						// we set the worker to assigned in our local map before actually assigning
						// by creating the ZK key.
						msg += String.format(sig + Col.RED.bg("  Mismatch between ZK assignments"
							+ "and local assignments map for worker [%s]\n"), worker.getKey());
						msg += ("ZK assignments are: " + assignments + "\n");
						msg += ("Map assignments are: " + workerMap.keySet() + "\n");
						System.out.println(msg);
						throw new RuntimeException();
					}
					// Otherwise ZK and Map agreement, nothing to do.
				}
			}
		}

		if (newlyIdle > 0) {
			msg += (sig + Col.GREEN.fg(newlyIdle + " workers have become idle.")
				+ " Launching assignment round.\n");
			try {msg = msg.replaceAll(sigColor, Col.CYAN.bg);} catch (Exception ignored) {};
			System.out.print(msg);
			assignmentRound();
		}
		else {
			// Most likely the callback was invoked because of Master adding a new assignment (or
			// master initialisation). Do nothing and either don't print or reduce its intensity
			// for easier comprehension and reducing clutter. Bold is reserved for "important"
			// events.
			msg += (sig + Col.YELLOW.fg("(Likely tripped by new assignment)")
				+ ". No newly idle workers.\n");
			try {msg = msg.replaceAll(sigColor, Col.CYAN.fg);} catch (Exception ignored) {}; // Reduce colour intensity
			if (DistProcess.LOOP_PRINT) System.out.print(msg); // Reduce clutter
		}
	};

	/* ===================================== Update Assignments ===================================== */

	private synchronized void assignmentRound() {
		String sig = Col.YELLOW.bg("Master/assignmentRound") + " : ";
		
		// Immediately exit if there are no pending tasks
		if (pendingTasks.size() == 0) {
			System.out.println(sig + Col.B_YELLOW.fg("There are no pending tasks."));
			return;
		}

		// Immediately exit if there are no available workers
		if (workerMap.entrySet().stream().noneMatch( (e) -> e.getValue() == null )) {
			System.out.println(sig + Col.B_YELLOW.fg("There are no available workers."));
			return;
		}

		// Try to assign as many tasks as possible.
		while (!pendingTasks.isEmpty()) {
			String nextPending = pendingTasks.peekFirst();
			System.out.println(sig + "Attempt to find idle worker for task [" + nextPending + "].");


			// Find a free worker
			Optional<String> freeWorker = workerMap.entrySet().stream()
				.filter(e -> e.getValue() == null)
				.map(Map.Entry::getKey)
				.findFirst();
			if (freeWorker.isEmpty()) {
				System.err.println(sig + Col.YELLOW.fg("  Ran out of free workers! Stopping round."));
				break;
			}
			System.out.println(sig + Col.GREEN.fg("  Found free worker [" + freeWorker.get() + "]"));

			// Assign the task to the free worker
			// Locally mark worker as BUSY first, so that we don't get confused next assignment
			// change CB iteration
			workerMap.put(freeWorker.get(), nextPending);
			pendingTasks.pop();
			// Now, let the worker know (this will trigger assignment CB).
			zk.create("/dist50/assignments/" + freeWorker.get(), nextPending.getBytes(),
				ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, assignmentCreationCB, null);
		}
	}

	/** Here to detect errors. */
	private final AsyncCallback.StringCallback assignmentCreationCB = (rc, path, ctx, name) -> {
		String sig = Col.YELLOW.bg("Master/assignmentCreateCB") + " : ";
		if (KeeperException.Code.get(rc) != KeeperException.Code.OK) {
			// Only print stuff if things go wrong.
			System.out.println(sig + "Callback with > " + KeeperException.Code.get(rc) + " : " + path + " : " + ctx + " : " + name);
			System.out.println(sig + Col.RED.bg("Failed to create ZNode"));
			throw new RuntimeException();
		}
	};

}

