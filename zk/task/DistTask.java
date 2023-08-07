/*
Copyright
All materials provided to the students as part of this course is the property of respective authors. Publishing them to third-party (including websites) is prohibited. Students may save it for their personal use, indefinitely, including personal cloud storage spaces. Further, no assessments published as part of this course may be shared with anyone else. Violators of this copyright infringement may face legal actions in addition to the University disciplinary proceedings.
©2022, Joseph D’Silva
*/
// You should not have to do anything in this directory.
import java.io.Serializable; // we need to be able to serialize the task to save it to ZK.
// Need to be implemented by as task that needs to be sent to the dist. platform.
public interface DistTask extends Serializable
{
	// This is the function that will be invoked by the workers of the dist platform.
	public void compute();
}
