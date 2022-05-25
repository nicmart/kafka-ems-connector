/*
 * Copyright 2017-2022 Celonis Ltd
 */
package com.celonis.kafka.connect.ems.testcontainers.scalatest

import com.celonis.kafka.connect.ems.testcontainers.MockServerContainer
import com.celonis.kafka.connect.ems.testcontainers.ToxiproxyContainer
import org.mockserver.client.MockServerClient
import org.scalatest.BeforeAndAfterAll
import org.scalatest.TestSuite
import org.scalatest.concurrent.Eventually
import org.scalatest.time.Minute
import org.scalatest.time.Span
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.Network
import org.testcontainers.containers.{ ToxiproxyContainer => JavaToxiproxyContainer }

trait MockServerContainerPerSuite extends BeforeAndAfterAll with Eventually { this: TestSuite =>

  protected val log: Logger = LoggerFactory.getLogger(getClass)

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(1, Minute))

  val network: Network = Network.newNetwork()

  lazy val mockServerContainer: MockServerContainer =
    MockServerContainer().withNetwork(network).withLogConsumer(new Slf4jLogConsumer(log))

  lazy val toxiproxyContainer: ToxiproxyContainer =
    ToxiproxyContainer("mockserver.celonis.cloud").withNetwork(network).withLogConsumer(new Slf4jLogConsumer(log))

  lazy val proxyServerUrl: String = s"https://${toxiproxyContainer.networkAlias}:${proxy.getOriginalProxyPort}"

  implicit lazy val proxy: JavaToxiproxyContainer.ContainerProxy =
    toxiproxyContainer.proxy(mockServerContainer.container, mockServerContainer.port)

  implicit lazy val mockServerClient: MockServerClient = mockServerContainer.hostNetwork.mockServerClient

  override def beforeAll(): Unit = {
    mockServerContainer.start()
    toxiproxyContainer.start()
    super.beforeAll()
  }

  override def afterAll(): Unit =
    try super.afterAll()
    finally {
      toxiproxyContainer.stop()
      mockServerContainer.stop()
    }
}
