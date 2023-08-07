/*
Copyright
All materials provided to the students as part of this course is the property of respective authors.
Publishing them to third-party (including websites) is prohibited. Students may save it for their
personal use, indefinitely, including personal cloud storage spaces. Further, no assessments
published as part of this course may be shared with anyone else. Violators of this copyright
infringement may face legal actions in addition to the University disciplinary proceedings.
©2022, Joseph D’Silva
*/
import java.io.*;

// To get the name of the host.
import java.net.*;

//To get the process id.
import java.lang.management.*;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.KeeperException.*;

/**
 * Main entry point for a server process. This class mostly serves as a bootstrap for both Master
 * and Worker. It determines whether this process is a master or a worker and creates either a
 * Master or a Worker class and finally initializes all the "watcher loops" for both.
 */
public class DistProcess {
	static final boolean LOOP_PRINT = System.getenv("LOOP_PRINT_50") != null;

	ZooKeeper zk;
	String zkServer;
	String processInfo;
	boolean isMaster = false;
	boolean initialized = false;

	/**
	 * Creates an instance of the distributed process with information regarding itself and
	 * the rest of the ZK ensemble.
	 * @param zkHosts Comma separated list of ZK servers.
	 */
	DistProcess(String zkHosts) {
		String sig = "Bootstrap/constructor : ";
		this.zkServer = zkHosts;
		this.processInfo = ManagementFactory.getRuntimeMXBean().getName(); // Process name
		System.out.println(sig + "ZK Connection information : " + zkServer);
		System.out.println(sig + "Process information : " + processInfo);
	}

	/**
	 * Connects the DistProcess to the zookeeper ensemble and sets a watch that will be invoked
	 * during any state change (aka connected).
	 */
	void startProcess() throws IOException {
		String sig = "Bootstrap/startProcess : ";
		System.out.println(sig + "Attempt to connect and set watcher");
		zk = new ZooKeeper(zkServer, 100000, connectionWatcher); // Connect to ZK & goto connectedWatcher
	}

	/**
	 * Watcher that will be invoked when a connection is established to the ZK ensemble.
	 * @see #startProcess() Coming from there.
	 */
	private final Watcher connectionWatcher = (event) -> {
		String sig = "Bootstrap/connectionWatcher : ";

		System.out.println(sig + "Tripped with : " + event);

		// Connection successful, do our initialization stuff.
		if (event.getType() == Watcher.Event.EventType.None) {
			if (event.getPath() == null && event.getState() == Watcher.Event.KeeperState.SyncConnected && !initialized) {
				System.out.println(sig + "Connection successful. Attempt to get role.");
				decideRole();
				initialized = true;
			}
		}
		// Connection FAILED
		else {
			System.err.println(sig + "Did not successfully connect.");
		}
	};

	/**
	 * Assign a role for this process by attempting to become master. After this method's execution,
	 * {@link #isMaster} will be set appropriately.
	 */
	private void decideRole() {
		String sig = "Bootstrap/decideRole : ";
		try {
			System.out.println(sig + "Running for master...");
			runForMaster(); // Raises NodeExistsException if master node already exists.
			isMaster = true;
		}
		catch (NodeExistsException nee) {
			isMaster = false;
		}
		catch (UnknownHostException | KeeperException | InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		System.out.println(sig + Col.GREEN.fg("I will be functioning as " + (isMaster ? "master" : "worker")));

		/*
		* ASSIGNED MASTER
		* Initialise all "watcher loops". Enter into these loops by also running their callback
		* methods, which will build the Master's internal data structures to reflect the state of
		* the ZK (any pre-existing tasks / workers).
		*/
		if (isMaster) {
			Master m = new Master(zk);
			m.init();
		}
		/*
		* ASSIGNED WORKER:
		* Initialise all "watcher loops".
		*/
		else {
			try {
				Worker w = new Worker(zk, processInfo);
				w.init();
			} catch (InterruptedException | KeeperException e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * Try to run for master by creating a "master" ephemeral node. If this method returns without
	 * throwing anything, then this DistProcess instance has been reserved the role of Master. This
	 * method is synchronous.
	 * @throws KeeperException If we fail to become master (we become a worker).
	 */
	private void runForMaster() throws UnknownHostException, KeeperException, InterruptedException {
		/*
		* Try to create an ephemeral node to be the master, put the hostname and pid of this process
		* as the data. This is an example of Synchronous API invocation as the function waits for
		* the execution and no callback is involved.
		*/
		zk.create("/dist50/master", processInfo.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
	}

	/**
	 * Closes the connection to the ZK ensemble. Preferrable to timing out since we may want to
	 * relaunch servers immediately after shutting down a previous testing session.
	 */
	public void disconnect() throws InterruptedException {
		zk.close();
	}

	public static void main(String[] args) throws Exception {
		//Create a new process
		//Read the ZooKeeper ensemble information from the environment variable.
		DistProcess dt = new DistProcess(System.getenv("ZKSERVER"));

		// Add shutdown hook that will actually disconnect instead of waiting for timeout.
		Runtime.getRuntime().addShutdownHook(new Thread( () -> {
			String sig = Col.RED.bg("ShutdownHook") + " : ";
			try {
				System.out.println(sig + Col.GREEN.fg("Closing connection."));
				dt.disconnect();
			} catch (InterruptedException e) {
				System.out.println(sig + Col.RED.fg("Error while closing connection."));
				throw new RuntimeException(e);
			}
		}));

		// Start the thread proper.
		dt.startProcess();

		//TODO Replace this with an approach that will make sure that the process is up and running forever.
		Thread.sleep(Long.MAX_VALUE);
		synchronized (dt) {
			// Somehow this program is still running 290 million years in the future
			// Permanently block (dt.notify() is never called);
			dt.wait();
		}
	}

}
