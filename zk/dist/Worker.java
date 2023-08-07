import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Class representing a worker. See worker.md for more details.
 */
public class Worker {

	private final ZooKeeper zk;
	private String workerZNode;
	private final String processInfo;

	/** The ExecutorService responsible for doing computations. */
	private final ExecutorService executor;
	/** The future of the task the worker is currently executing. */
	private volatile Future<?> taskFuture;

	public Worker(ZooKeeper zk, String processInfo) {
		String sig = Col.CYAN.fg("Worker/constructor") + " : ";
		this.zk = zk;
		this.processInfo = processInfo;
		System.out.println(sig +  "Constructed and ready for init");
		executor = Executors.newSingleThreadExecutor();
	}

	/**
	 * Worker-x initialise by setting an exists() watch on their assignments at the (currently
	 * nonexistent) ZNode "/dist50/assignments/worker-x". Only then can Workers let the Master
	 * know they joined by creating the node "/dist50/workers/worker-x", which the Master has
	 * a watch on. This is to avoid a case where the master immediately assigns a task to the
	 * worker before the worker has had time to set a watch on its assignments.
	 * This method is synchronous.
	 * @throws InterruptedException Failure to create sequential ZNode.
	 * @throws KeeperException Failure to create sequential ZNode.
	 */
	public void init() throws InterruptedException, KeeperException {
		String sig = Col.CYAN.fg("Worker/init") + " : ";

		// Create thread that will listen for commands (debug only)
		System.out.println(sig + "Creating command listener thread");
		new Thread(commandHandler).start();

		/*
		 * Get our ZNode name BEFORE notifying the Master.
		 *
		 * There is a slight chicken-and-egg problem where. To set a watch on its assignments,
		 * worker-x must know ts ID ("x"). To get "x", the worker has to create a sequential node
		 * "worker-?", but if it creates that node in "/dist50/workers", the master will be
		 * notified, and we run in the problem in the method javadoc. To solve this, for workers
		 * to get their ID ("x"), the worker first creates a sequential node in "/dist50/trash"
		 * to get "x" without notifying the master. We could have also used {@link #processInfo},
		 * but it has no uniqueness guarantees as unlikely as it is to actually be a problem.
		 */
		System.out.println(sig + "Creating garbage ZNode to get our worker ID.");
		String tmp = zk.create("/dist50/trash/worker-", processInfo.getBytes(),
			ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
		this.workerZNode = tmp.split("/trash/")[1]; //"worker-???"
		System.out.println(sig + "Initialized with ID [" + this.workerZNode + "]");
		sig = Col.CYAN.bg(workerZNode + "/init") + " : ";


		// Start the assignments "watch-loop" BEFORE letting master know we joined. Synchronous.
		System.out.println(sig + "Creating watch on assignment before announcing presence.");
		setAssignmentWatcher();

		// Create node so that the Master is notified of us joining.
		System.out.println(sig + "Announcing presence to master.");
		String zNode = zk.create("/dist50/workers/" + workerZNode, processInfo.getBytes(),
			ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

		// Something went really wrong if this fails.
		assert (Objects.equals(zNode, workerZNode) && zNode != null);
	}

	/** Allow manual debugging */
	private final Runnable commandHandler = () -> {
		String sig = Col.RED.bg + "Worker/commandHandler" + Col.RESET + " : ";
		Scanner sc = new Scanner(System.in);
		while (!Thread.currentThread().isInterrupted()) {
			try {
				String cmd = sc.nextLine();
				if ("".equalsIgnoreCase(cmd)) continue;
				if ("stop".equalsIgnoreCase(cmd)) System.exit(0); // Runs shutdown hook to disconnect
				else if ("kill".equalsIgnoreCase(cmd)) {
					/*
					 * We are using a single threaded executor to do the computation so that we may
					 * cancel its future and serialize an "unfinished" task object back for debugging
					 * purposes. MCPi doesn't respond to interrupts, so it will continue hogging CPU in
					 * some random thread somewhere but at least control will be restored to the worker
					 * computation thread (ST executor will be unable to process new tasks). Use
					 * TestClient to debug with an interruptible task.
					 */
					if (taskFuture != null && (!taskFuture.isDone() || !taskFuture.isCancelled())) {
						System.out.println(sig + Col.RED.fg("Attempting to interrupt task..."));
						taskFuture.cancel(true);
					}
					else {
						System.out.println(sig + Col.RED.fg("No task to interrupt"));
					}
				}
				else if ("status".equalsIgnoreCase(cmd)) {
					if (taskFuture == null || taskFuture.isDone() || taskFuture.isCancelled())
						System.out.println(sig + "Currently idle");
					else
						System.out.println(sig + "Currently running task");
				}
				else {
					System.out.println(sig + Col.RED.fg("Unknown command \"" + cmd + "\""));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	/* ===================================== ASSIGNMENT MANAGEMENT "LOOP" ===================================== */

	private void setAssignmentWatcher() {
		String sig = Col.MAGENTA.bg(workerZNode + "/setAssignmentWatcher") + " : ";
		try {
			/*
			 * From javadoc: "The watch will be triggered by a successful operation that
			 * creates/delete the node or sets the data on the node." So, we can be safe knowing
			 * that this will trip ONCE the (currently nonexistent) node gets CREATED by the Master.
			 */
			if (DistProcess.LOOP_PRINT) {
				System.out.println(sig + "assignmentWatcher set on "
					+ "\"/dist50/assignments/" + workerZNode + "\"'s existence");
			}
			zk.exists("/dist50/assignments/" + workerZNode, assignmentWatcher);
		} catch (InterruptedException | KeeperException e) {
			System.err.println(sig + Col.RED.bg("An error has occurred while assigning the watch."));
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private final Watcher assignmentWatcher = (event) -> {
		String sig = Col.MAGENTA.bg(workerZNode + "/assignmentWatcher") + " : ";
		if (DistProcess.LOOP_PRINT) System.out.println(sig + "Tripped with > " + event.getType().name());

		switch (event.getType()) {
			case NodeCreated:
				// New assignment: get task from ZNode data, retrieve and do it.
				System.out.println(sig + "A new task was assigned to us. Starting it...");
				startComputation(); // Asynchronous
				break;

			case NodeDeleted:
				// ONLY WE are allowed to delete our own assignment node, this is tripped by the
				// computation thread finishing. There is nothing to do.
//				if (DistProcess.LOOP_PRINT) System.out.println(sig + "Assignment removed.");
				break;

			case None:
				// Client close
				if (event.getState() == Watcher.Event.KeeperState.Closed) {
					System.out.println(sig + Col.RED.fg("Disconnect: terminating assignment \"watcher loop\""));
					return; // Don't renew loop
				}

			default:
				// Something wrong happened
				System.err.printf(sig + Col.RED.bg("An unexpected event has occurred!")
						+ " [type] = %s | [state] = %s | [path] = %s",
					event.getType(), event.getState(), event.getPath());
				throw new RuntimeException();
		}
		setAssignmentWatcher(); // Loop
	};

	private void startComputation() {
		/*
		 * Everything in the computation thread is synchronous.
		 * 1. We are not on the event handler thread, so it's ok if we block or take a long time.
		 * 2. We can't proceed without getting the information, so in terms of time taken, there's
		 *    no difference between blocking or using a callback (since it will be called in the
		 *    same amount of time as it would take it to unblock, and we'd be back on the event
		 *    handler thread).
		 */
		new Thread( () -> {
			String sig = Col.YELLOW.bg(workerZNode + "/computationThread") + " : ";
			System.out.println(sig + "Computation thread starting...");

			// Get which task was assigned to us.
			System.out.println(sig + "Getting assigned task details...");
			String assignedTaskName;
			try {
				byte[] rawTaskLocation = zk.getData("/dist50/assignments/" + workerZNode, false, null);
				assignedTaskName = new String(rawTaskLocation);
				System.out.println(sig + "We were assigned: " + Col.GREEN.fg("[" + assignedTaskName + "]"));

			} catch (KeeperException | InterruptedException e) {
				System.err.println(sig + Col.RED.bg("Error encountered while getting assigned task."));
				e.printStackTrace();
				throw new RuntimeException(e);
			}

			// Deserialize the task and actually compute.
			try {
				System.out.println(sig + "Deserializing task...");
				//getData(String path, boolean watch, AsyncCallback.DataCallback cb, Object ctx) Async version of GetData
				byte[] taskSerial = zk.getData("/dist50/tasks/" + assignedTaskName, false, null);

				// Re-construct our task object.
				ByteArrayInputStream bis = new ByteArrayInputStream(taskSerial);
				ObjectInput in = new ObjectInputStream(bis);
				DistTask dt = (DistTask) in.readObject();

				System.out.println(sig + "Starting computation...");

				// Execute the task. Using an executor so that we can cancel on a Future (for now
				// only manual cancellation by user entering "Kill" in terminal).
				try {
					taskFuture = executor.submit(dt::compute);
					taskFuture.get(); // Block here until either done or cancelled.
					System.out.println(sig + Col.GREEN.bg("Computation done") + ", serializing task.");
				} catch (CancellationException e) {
					System.out.println(sig + Col.RED.bg("Computation cancelled") + ", serializing incomplete task.");
				} catch (ExecutionException e) {
					throw new RuntimeException(e);
				}

				// Serialize our Task object back to a byte array!
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(bos);
				oos.writeObject(dt); oos.flush();
				taskSerial = bos.toByteArray();

				// Store it inside the result node.
				System.out.println(sig + "Storing result in /dist50/tasks/" + assignedTaskName + "/result");
				zk.create("/dist50/tasks/" + assignedTaskName + "/result", taskSerial,
					ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
				//zk.create("/dist50/tasks/"+c+"/result", ("Hello from "+pinfo).getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
				System.out.println(sig + "Result stored.");

			} catch(KeeperException | IOException | InterruptedException | ClassNotFoundException e) {
				System.err.println(sig + Col.RED.bg(" : Error encountered while doing our assigned task."));
				e.printStackTrace();
				throw new RuntimeException(e);
			}

			// Finally, now that we're done, we can remove our own assignment. By this point our
			// assignment watcher should be reinstated.
			try {
				System.out.println(sig + Col.GREEN.fg("Task finished")
					+ ", removing assignment (delete /dist50/assignments/" + workerZNode + ")");
				zk.delete("/dist50/assignments/" + workerZNode, -1);
			} catch (InterruptedException | KeeperException e) {
				System.err.println(sig + Col.RED.fg(" : Error encountered while removing our 'assignment node'."));
				e.printStackTrace();
				throw new RuntimeException(e);
			}

		}).start();
	}

}
