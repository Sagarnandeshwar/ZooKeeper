import org.apache.zookeeper.*;

public class ZKReset {

	private static ZooKeeper zk;

	public static void main(String[] args) throws Exception {
		Thread.currentThread().setName("Main");
		String sig = Thread.currentThread().getName() + " : ";
		System.out.println(sig + "Connecting...");
		zk = new ZooKeeper(System.getenv("ZKSERVER"), 10000, connectionWatcher);

		Runtime.getRuntime().addShutdownHook(new Thread( () -> {
			try {
				System.out.println("ShutdownHook : Closing connection.");
				zk.close();
				synchronized (zk) { // In case ctrl + c
					zk.notifyAll();
				}
			} catch (InterruptedException e) {
				System.out.println("ShutdownHook : Error while closing connection.");
				throw new RuntimeException(e);
			}
		}));

		System.out.println(sig + "Started to wait...");
		synchronized (zk) {
			zk.wait();
		}
		System.out.println(sig + "Finished, closing connection");
		// Run shutdown hook
	}

	private static final Watcher connectionWatcher = (event) -> {
		String sig = Thread.currentThread().getName() + " : ";
		System.out.println(event.getType());
		if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
			System.out.println(sig + "Connected. Deleting everything and rebuilding...");
			new Thread( () -> {
				Thread.currentThread().setName("ResetThread");
				String sig1 = Thread.currentThread().getName() + " : ";
				deleteEverything();
				rebuild();
				System.out.println(sig1 + "Everything done, unblocking main.");
				synchronized (zk) {
					zk.notifyAll();
				}
			}).start();

		}
		else {
			System.out.println(sig + "Error while connecting.");
		}
	};

	private static void deleteEverything() {
		String sig = Thread.currentThread().getName() + " : ";
		try {
			if (zk.exists("/dist50", null) != null) {
				System.out.println(sig + "Attempt to recursively delete '/dist50'...");
				ZKUtil.deleteRecursive(zk, "/dist50");
				System.out.println(sig + "Done");
			}
			else {
				System.out.println(sig + "'/dist50' DNE, skipping to rebuild. ");
			}
		} catch (KeeperException e) {
			if (e.code() != KeeperException.Code.NONODE) {
				System.out.println("Error deleting node '/dist50'");
				e.printStackTrace();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void rebuild() {
		String sig = Thread.currentThread().getName() + " : ";
		System.out.println(sig + "Rebuilding...");
		try {
			System.out.println(sig + "Creating '/dist50'...");
			zk.create("/dist50", "Root ZNode".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

			System.out.println(sig + "Creating '/dist50/workers'...");
			zk.create("/dist50/workers", "Contains ephemeral worker ZNodes (exists -> worker online)".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

			System.out.println(sig + "Creating '/dist50/tasks'...");
			zk.create("/dist50/tasks", "Contains tasks ZNodes (exists -> task pending)".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

			System.out.println(sig + "Creating '/dist50/assignments'...");
			zk.create("/dist50/assignments", "Contains assignment ZNodes (exists -> assignment in effect)".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

			System.out.println(sig + "Creating '/dist50/trash'...");
			zk.create("/dist50/trash", "Used by workers to get a sequential ID without alerting the Master".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

			System.out.println(sig + "Rebuilding done.");
		} catch (InterruptedException | KeeperException e) {
			System.out.println(sig + "Error while rebuilding '/dist50'");
			e.printStackTrace();
		}
	}

}
