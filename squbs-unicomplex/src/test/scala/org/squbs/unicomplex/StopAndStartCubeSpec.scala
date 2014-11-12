/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.unicomplex

import java.util.concurrent.TimeUnit

import akka.actor.{ActorIdentity, Identify, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop

import scala.util.Try
import scala.concurrent.duration._

/**
 * Created by zhuwang on 9/24/14.
 */

object StopAndStartCubeSpec {
  val dummyJarsDir = "src/test/resources/classpaths"

  val classPaths = Array(
    "DummyCube",
    "DummyCubeSvc",
    "DummySvc"
  ) map (dummyJarsDir + "/" + _)

  import scala.collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "StopAndStartCubeSpec",
      "squbs." + JMX.prefixConfig -> Boolean.box(true),
      "default-listener.bind-port" -> org.squbs.nextPort
    )
  )

  val boot = UnicomplexBoot(mapConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class StopAndStartCubeSpec extends TestKit(StopAndStartCubeSpec.boot.actorSystem)
with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  implicit val timeout: akka.util.Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (10 seconds)

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Unicomplex" should "be able to stop a cube" in {
    Unicomplex(system).uniActor ! StopCube("DummyCube")
    within(10 seconds) {
      expectMsg(Ack)
    }
    system.actorSelection("/user/DummyCube") ! Identify("hello")
    within(10 seconds) {
      val id = expectMsgType[ActorIdentity]
      id.ref should be(None)
    }
  }

  "Unicomplex" should "not be able to stop a stopped cube" in {
    Unicomplex(system).uniActor ! StopCube("DummyCube")
    expectNoMsg
  }

  "Unicomplex" should "be able to start a cube" in {
    Unicomplex(system).uniActor ! StartCube("DummyCube")
    within(10 seconds) {
      expectMsg(Ack)
    }
    system.actorSelection("/user/DummyCube") ! Identify("hello")
    within(10 seconds) {
      val id = expectMsgType[ActorIdentity]
      id.ref should not be (None)
    }
  }

  "Unicomplex" should "not be able to start a running cube" in {
    Unicomplex(system).uniActor ! StartCube("DummyCube")
    expectNoMsg
  }

}
