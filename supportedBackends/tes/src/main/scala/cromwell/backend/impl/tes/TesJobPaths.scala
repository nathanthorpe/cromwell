package cromwell.backend.impl.tes

import com.typesafe.config.Config
import cromwell.backend.io.{JobPaths, WorkflowPaths}
import cromwell.backend.{BackendJobDescriptorKey, BackendWorkflowDescriptor}
import cromwell.core.path._
import cromwell.filesystems.blob.BlobPath

object TesJobPaths {
  def apply(jobKey: BackendJobDescriptorKey,
  workflowDescriptor: BackendWorkflowDescriptor,
  config: Config) = {
    val workflowPaths = TesWorkflowPaths(workflowDescriptor, config, WorkflowPaths.DefaultPathBuilders)
    new TesJobPaths(workflowPaths, jobKey)
  }
}

case class TesJobPaths private[tes] (override val workflowPaths: TesWorkflowPaths,
                                     jobKey: BackendJobDescriptorKey,
                                     override val isCallCacheCopyAttempt: Boolean = false) extends JobPaths {

  import JobPaths._

  override lazy val callExecutionRoot = {
    callRoot.resolve("execution")
  }
  val callDockerRoot = callPathBuilder(workflowPaths.dockerWorkflowRoot, jobKey, isCallCacheCopyAttempt)
  val callExecutionDockerRoot = callDockerRoot.resolve("execution")
  val callInputsDockerRoot = callDockerRoot.resolve("inputs")
  val callInputsRoot = callRoot.resolve("inputs")

  /*
   * tesTaskRoot: The Azure TES implementation allows us to specify a working directory that it should use for its own files.
   * Once the task finishes, this directory will contain stderr.txt, stdout.txt, and some other misc files.
   * Unlike other paths we provide, this one is expected as a path relative to the root of the storage container.
   * We provide it as a k/v pair where the key is "internal_path_prefix" and the value is the relative path string.
   * This is not a standard TES feature, but rather related to the Azure TES implementation that Terra uses.
   * While passing it outside of terra won't do any harm, we could consider making this optional and/or configurable.
   */
  private val taskFullPath = callRoot./("tes_task")
  val tesTaskRoot : String = taskFullPath match {
    case blob: BlobPath => blob.pathWithoutContainer
    case anyOtherPath: Path => anyOtherPath.pathAsString
  }

  // Like above: Nothing should rely on these files existing, since only the Azure TES implementation will actually create them.
  // Used to send the Azure TES log paths to the frontend.
  val azureLogPathsForMetadata : Map[String, Any] = Map(
    "tes_stdout" -> taskFullPath./("stdout.txt").pathAsString,
    "tes_stderr" -> taskFullPath./("stderr.txt").pathAsString
  )

  // Given an output path, return a path localized to the storage file system
  def storageOutput(path: String): String = {
    callExecutionRoot.resolve(path).toString
  }

  // Given an output path, return a path localized to the container file system
  def containerOutput(cwd: Path, path: String): String = containerExec(cwd, path)

  // TODO this could be used to create a separate directory for outputs e.g.
  // callDockerRoot.resolve("outputs").resolve(name).toString

  // Given an file name, return a path localized to the container's execution directory
  def containerExec(cwd: Path, path: String): String = {
    cwd.resolve(path).toString
  }

  override def forCallCacheCopyAttempts: JobPaths = this.copy(isCallCacheCopyAttempt = true)
}
