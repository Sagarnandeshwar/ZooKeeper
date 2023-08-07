/**
 * Custom launcher for the DistClient that submits an {@link InterruptibleTask} instead of the
 * standard non-interruptible MCPi task. Basically copy-pasted main().
 */
public class TestClient {

	public static void main(String[] args) throws Exception {
		int ttl = Integer.parseInt(args[0]);
		InterruptibleTask task = new InterruptibleTask(ttl);

		DistClient dt = new DistClient(System.getenv("ZKSERVER"), task);

		dt.startClient();
		synchronized(dt) {
			try {
				dt.wait();
			} catch (InterruptedException ignored) {}
		}

		System.out.println("\n\n\n\n\n");
		task = (InterruptibleTask) dt.getDistTask();
		if (task.finished()) {
			System.out.printf("\u001B[32mTask entirely finished in %d seconds\n", ttl);
		}
		else {
			System.out.printf("\u001B[31mTask interrupted with %.2f seconds remaining\n", task.getWorkRemaining() / 1000D);
		}

	}

}
