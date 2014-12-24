package org.github.jiraburn.domain

import java.util.Date

import scalaz._
import Scalaz._

sealed trait Task { self =>
  def taskId: String
  def parentUserStoryId: String
  def isTechnicalTask: Boolean

  def taskName: String
  def optionalStoryPoints: Option[Int]
  def status: Int

  def taskAdded(implicit timestamp: Date): Seq[TaskAdded]
  def storyPointsWithoutSubTasks: Int
  def boardColumnIndex(implicit config: ProjectConfig): Int = config.boardColumnIndex(status)
}

case class UserStory(taskId: String,
                     taskName: String,
                     optionalStoryPoints: Option[Int],
                     technicalTasksWithoutParentId: Set[TechnicalTask],
                     status: Int) extends Task with ComparableWith[UserStory] with HavingNestedTasks[TechnicalTaskWithParentId] {
  override type Self = UserStory

  protected val nestedTasks: Set[TechnicalTaskWithParentId] = technicalTasksWithoutParentId.map(TechnicalTaskWithParentId(_, taskId))

  override def parentUserStoryId: String = taskId

  override def isTechnicalTask: Boolean = false

  override def taskAdded(implicit timestamp: Date): Seq[TaskAdded] = flattenTasks.map(TaskAdded.apply)

  def add(technical: TechnicalTask): UserStory = {
    require(!taskById.contains(technical.taskId))
    copy(technicalTasksWithoutParentId = technicalTasksWithoutParentId + technical)
  }

  def remove(taskId: String): UserStory = {
    require(taskById.contains(taskId))
    copy(technicalTasksWithoutParentId = technicalTasksWithoutParentId.filterNot(_.taskId == taskId))
  }

  def update(taskId: String)(updateTechnical: TechnicalTask => TechnicalTask): UserStory = {
    val updated = updateTechnical(taskById(taskId).technical)
    copy(technicalTasksWithoutParentId = technicalTasksWithoutParentId.filterNot(_.taskId == taskId) + updated)
  }

  override def diff(other: Self)(implicit timestamp: Date): Seq[TaskEvent] = {
    selfDiff(other) ++ super.diff(other)
  }

  def flattenTasks: List[Task] = this :: nestedTasks.toList

  override def storyPointsWithoutSubTasks: Int = {
    val storyPointsOfMine = optionalStoryPoints.getOrElse(0)
    val diff = storyPointsOfMine - nestedTasksStoryPointsSum
    Math.max(0, diff)
  }

  override def toString: String = {
    s"""UserStory(id = $taskId, sp = $optionalStoryPoints, st = $status, name = $taskName
       |${technicalTasksWithoutParentId.map(_.toString).mkString(",\n")}
       |)""".stripMargin
  }

}

case class TechnicalTaskWithParentId(technical: TechnicalTask,
                                     parentUserStoryId: String) extends Task with ComparableWith[TechnicalTaskWithParentId] {
  override def taskId: String = technical.taskId
  override def taskName: String = technical.taskName
  override def optionalStoryPoints: Option[Int] = technical.optionalStoryPoints
  override def status: Int = technical.status

  override def isTechnicalTask: Boolean = true
  override def storyPointsWithoutSubTasks: Int = technical.optionalStoryPoints.getOrElse(0)

  override def taskAdded(implicit timestamp: Date): Seq[TaskAdded] = Seq(TaskAdded(this))

  override def diff(other: TechnicalTaskWithParentId)(implicit timestamp: Date): Seq[TaskEvent] = {
    selfDiff(other)
  }
}

trait ComparableWith[Self <: Task with ComparableWith[_]] { self: Task =>
  def diff(other: Self)(implicit timestamp: Date): Seq[TaskEvent]

  protected def selfDiff(other: Self)(implicit timestamp: Date): Seq[TaskEvent] = {
    (other.taskName != this.taskName ||
      other.optionalStoryPoints != this.optionalStoryPoints ||
      other.status != this.status).option(TaskUpdated(other)).toSeq
  }
}

case class TechnicalTask(taskId: String,
                         taskName: String,
                         optionalStoryPoints: Option[Int],
                         status: Int) {
  override def toString: String = {
    s"  Technical(id = $taskId, sp = $optionalStoryPoints, st = $status, name = $taskName)"
  }
}

object UserStory {
  def apply(added: TaskAdded): UserStory = {
    require(!added.isTechnicalTask, s"Invalid event $added")
    require(added.parentUserStoryId == added.taskId, s"Invalid event $added")
    UserStory(
      taskId = added.taskId,
      taskName = added.taskName,
      optionalStoryPoints = added.optionalStoryPoints,
      technicalTasksWithoutParentId = Set.empty,
      status = added.status)
  }
}

object TechnicalTask {
  def apply(added: TaskAdded): TechnicalTask = {
    require(added.isTechnicalTask, s"Invalid event $added")
    TechnicalTask(
      taskId = added.taskId,
      taskName = added.taskName,
      optionalStoryPoints = added.optionalStoryPoints,
      status = added.status)
  }
}