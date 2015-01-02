package org.github.microburn.rest

import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction._
import org.github.microburn.service.SprintColumnsHistoryProvider

class RestRoutes(columnsHistoryProvider: SprintColumnsHistoryProvider) extends RestHelper {

  serve {
    case JsonGet("history" ::Nil, req) =>
      val sprintId = req.param("sprintId").openOr(throw new IllegalArgumentException("sprintId must be provided"))
      columnsHistoryProvider.columnsHistory(sprintId).map(_.map(decompose))
  }

}