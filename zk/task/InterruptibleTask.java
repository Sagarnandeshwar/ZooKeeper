/**
 * Custom implementation of a task that simulates a lengthy task that actually respond to
 * interruptions and who doesn't stress the CPU. For testing only.
 */
public class InterruptibleTask implements DistTask {

	private long msRemaining;

	public InterruptibleTask(int timeSeconds) {
		this.msRemaining = timeSeconds * 1000L;
	}

	@Override
	public void compute() {
		System.out.println("DistTask: compute : started");
		while (msRemaining > 0 && !Thread.currentThread().isInterrupted()) {
			long start = System.currentTimeMillis();
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				break;
			}
			msRemaining -= System.currentTimeMillis() - start;
		}

		if (Thread.currentThread().isInterrupted()) {
			System.out.println("DistTask: Interrupted!");
		}
		System.out.println("DistTask: compute : finished");
	}

	public boolean finished() {
		return msRemaining <= 0;
	}

	public long getWorkRemaining() {
		return msRemaining;
	}


}
