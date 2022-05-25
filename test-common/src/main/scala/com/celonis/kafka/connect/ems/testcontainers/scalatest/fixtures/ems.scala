package com.celonis.kafka.connect.ems.testcontainers.scalatest.fixtures

import org.mockserver.client.MockServerClient
import org.mockserver.model.{HttpRequest, HttpResponse}

object ems {

  def withMockResponse(
    request:  HttpRequest,
    response: HttpResponse,
  )(testCode: => Any,
  )(
    implicit
    mockServerClient: MockServerClient,
  ): Unit =
    try {
      mockServerClient.when(request).respond(response)
      val _ = testCode
    } finally {
      val _ = mockServerClient.reset()
    }
}