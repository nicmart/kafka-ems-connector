/*
 * Copyright 2022 Celonis SE
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
import org.testcontainers.containers.Network
import org.testcontainers.containers.{ ToxiproxyContainer => JavaToxiproxyContainer }

trait MockServerContainerPerSuite extends BeforeAndAfterAll with Eventually { this: TestSuite =>

  protected val log: Logger = LoggerFactory.getLogger(getClass)

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(1, Minute))

  val network: Network = Network.newNetwork()

  lazy val mockServerContainer: MockServerContainer =
    MockServerContainer().withNetwork(network)

  lazy val toxiproxyContainer: ToxiproxyContainer =
    ToxiproxyContainer("mockserver.celonis.cloud").withNetwork(network)

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
