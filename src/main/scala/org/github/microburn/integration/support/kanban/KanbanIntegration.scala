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

import java.util.Date

import net.liftweb.actor.LAFuture
import org.github.microburn.domain.MajorSprintDetails
import org.github.microburn.domain.actors.{ProjectActor, UpdateSprint}
import org.github.microburn.integration.Integration

import scala.concurrent.duration.FiniteDuration

class KanbanIntegration(protected val boardStateProvider: BoardStateProvider,
                        protected val initializationTimeout: FiniteDuration)
                       (protected val projectActor: ProjectActor)
  extends Integration
  with ScrumSimulation {

  import org.github.microburn.util.concurrent.FutureEnrichments._
  import org.github.microburn.util.concurrent.ActorEnrichments._

  override def updateProject(implicit timestamp: Date): LAFuture[_] = {
    for {
      fetchedCurrentSprintsBoardState <- (scrumSimulator ?? FetchCurrentSprintsBoardState)
        .mapTo[Option[FetchedBoardState]]
        .withLoggingFinished { state => s"fetched sprint state: ${state.map(_.toString)}"  }
      updateResult <- fetchedCurrentSprintsBoardState.map { fetchedState =>
        projectActor ?? UpdateSprint(fetchedState.sprintId, fetchedState.userStories, fetchedState.details, timestamp)
      }.toFutureOfOption
    } yield updateResult
  }
}