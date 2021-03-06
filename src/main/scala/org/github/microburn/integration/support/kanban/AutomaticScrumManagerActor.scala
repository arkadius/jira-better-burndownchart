/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.github.microburn.integration.support.kanban

import java.io.File

import net.liftweb.actor.LAFuture
import org.github.microburn.domain.{RepeatPeriod, AutomaticManagementMode, ScrumManagementMode}
import org.github.microburn.integration.Integration
import org.github.microburn.repository.LastSprintRestartRepository
import org.github.microburn.util.concurrent.JobRepeatingActor
import org.github.microburn.util.logging.Slf4jLogging
import org.joda.time.DateTime

import scala.concurrent.duration.FiniteDuration

class AutomaticScrumManagerActor(scrumSimulator: ScrumSimulatorActor,
                                 restartPeriod: RepeatPeriod,
                                 repo: LastSprintRestartRepository,
                                 protected val tickPeriod: FiniteDuration) extends JobRepeatingActor with Slf4jLogging  {
  import org.github.microburn.util.concurrent.ActorEnrichments._
  import org.github.microburn.util.concurrent.FutureEnrichments._

  override protected val jobDescription: String = "scheduling next sprint restart"

  private val nextRestartComputer = new NextRestartComputer(restartPeriod)

  private var scheduledRestart: NextRestart = computeNext(repo.loadLastSprintRestart, new DateTime())

  override protected def prepareFutureOfJob(timestamp: DateTime): LAFuture[_] = {
    if (timestamp.isBefore(scheduledRestart.date)) {
      LAFuture(() => Unit)
    } else {
      val nextRestart = computeNext(Some(scheduledRestart.date), timestamp)
      val start = StartSprint(scheduledRestart.periodName, scheduledRestart.date.toDate, nextRestart.date.toDate)
      scheduledRestart = nextRestart
      repo.saveLastSprintRestart(timestamp)
      for {
        _ <- (scrumSimulator ?? FinishCurrentActiveSprint).withLoggingFinished("finished sprint id: " + _)
        startResult <- (scrumSimulator ?? start).withLoggingFinished("started sprint id: " + _)
      } yield startResult
    }
  }

  private def computeNext(optionalLastRestart: Option[DateTime], currentDate: DateTime): NextRestart = {
    val next = nextRestartComputer.compute(optionalLastRestart, currentDate)
    info(s"Scheduled: $next")
    next
  }
}

object AutomaticScrumManagerActor {
  def optionallyPrepareAutomaticScrumManager(scrumManagementMode: ScrumManagementMode,
                                             integration: Integration,
                                             projectRoot: File,
                                             tickPeriod: FiniteDuration): Option[AutomaticScrumManagerActor] = {
    integration match {
      case scrumSimulation: ScrumSimulation =>
        scrumManagementMode match {
          case auto: AutomaticManagementMode =>
            Some(prepare(projectRoot, tickPeriod, scrumSimulation.scrumSimulator, auto.restartPeriod))
          case otherMode =>
            None
        }
      case notSimulating =>
        scrumManagementMode match {
          case auto: AutomaticManagementMode =>
            throw new IllegalArgumentException("Automatic management mode is not available for integration without scrum simulation")
          case otherMode =>
            None
        }
    }
  }

  private def prepare(projectRoot: File,
                      tickPeriod: FiniteDuration,
                      scrumSimulator: ScrumSimulatorActor,
                      restartPeriod: RepeatPeriod): AutomaticScrumManagerActor = {
    val repo = LastSprintRestartRepository(projectRoot)
    new AutomaticScrumManagerActor(scrumSimulator, restartPeriod, repo, tickPeriod)
  }
}