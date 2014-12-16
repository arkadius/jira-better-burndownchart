package org.github.jiraburn.domain

import java.util.Date

sealed trait Task {
  def taskId: String
  def taskName: String
  def storyPointsWithoutSubTasks: Int
  def parentUserStoryId: String
  protected def state: TaskState

  def isOpened = state == Opened
  def isCompleted = state == Completed

  def finish(parentUserStoryFromInitialScope: Boolean)
            (implicit timestamp: Date): Option[TaskCompleted] =
    if (isOpened)
      Some(doFinish(parentUserStoryFromInitialScope))
    else
      None
  
  def reopen(parentUserStoryFromInitialScope: Boolean)
            (implicit timestamp: Date): Option[TaskReopened] =
    if (isCompleted)
      Some(doReopen(parentUserStoryFromInitialScope))
    else
      None

  private def doFinish(parentUserStoryFromInitialScope: Boolean)
                      (implicit timestamp: Date): TaskCompleted =
    TaskCompleted(taskId, parentUserStoryId, parentUserStoryFromInitialScope, timestamp, storyPointsWithoutSubTasks)
  
  private def doReopen(parentUserStoryFromInitialScope: Boolean)
                      (implicit timestamp: Date): TaskReopened =
    TaskReopened(taskId, parentUserStoryId, parentUserStoryFromInitialScope, timestamp, storyPointsWithoutSubTasks)
}

case class UserStory(taskId: String,
                     taskName: String,
                     optionalStoryPoints: Option[Int],
                     /*private val */technicalTasksWithoutParentId: List[TechnicalTask],
                     state: TaskState) extends Task {

  def technicalTasks: Seq[Task] = technicalTasksWithoutParentId.map(TechnicalTaskWithParentId(_, taskId))
  
  override def storyPointsWithoutSubTasks: Int = {
    val storyPointsOfMine = optionalStoryPoints.getOrElse(0)
    val subTasksStoryPoints = technicalTasks.map(_.storyPointsWithoutSubTasks).sum
    val diff = storyPointsOfMine - subTasksStoryPoints
    Math.max(0, diff)
  }

  override def parentUserStoryId: String = taskId
}

case class TechnicalTaskWithParentId(technical: TechnicalTask, parentUserStoryId: String) extends Task {
  override def taskId: String = technical.taskId

  override def taskName: String = technical.taskName

  override def storyPointsWithoutSubTasks: Int = technical.optionalStoryPoints.getOrElse(0)

  override protected def state: TaskState = technical.state
}

case class TechnicalTask(taskId: String, taskName: String, optionalStoryPoints: Option[Int], state: TaskState)

object UserStory {
  def apply(taskId: String,
            taskName: String,
            optionalStoryPoints: Option[Int],
            technicalTasks: Seq[TechnicalTask]): UserStory = {
    new UserStory(taskId, taskName, optionalStoryPoints, technicalTasks.toList, Opened)
  }
}

object TechnicalTask {
  def apply(taskId: String,
            taskName: String,
            optionalStoryPoints: Option[Int]): TechnicalTask = {
    new TechnicalTask(taskId, taskName, optionalStoryPoints, Opened)
  }
}