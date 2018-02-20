package com.softwaremill.sttp.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import brave.Tracing
import brave.sampler.Sampler
import zipkin2.reporter.urlconnection.URLConnectionSender
import zipkin2.reporter.AsyncReporter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.softwaremill.sttp.brave.BraveBackend

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Main extends App {
  // setup akka
  implicit val actorSystem: ActorSystem = ActorSystem("sttp-examples-zipkin")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import actorSystem.dispatcher

  // setup zipkin
  // zipkin must be running: docker run -d -p 9411:9411 openzipkin/zipkin
  val sender = URLConnectionSender.create("http://127.0.0.1:9411/api/v2/spans")
  val reporter = AsyncReporter.create(sender)
  val tracing = Tracing
    .newBuilder()
    .localServiceName("sttp-examples-zipkin")
    .spanReporter(reporter)
    .sampler(Sampler.ALWAYS_SAMPLE)
    .build()
  val tracer = tracing.tracer

  // start the servers
  startServer1()
  startServer2()

  // create the brave/zipkin backend
  implicit val backend: SttpBackend[Future, Nothing] =
    BraveBackend(AkkaHttpBackend.usingActorSystem(actorSystem), tracing)

  // create the parent span
  val span = tracer.newTrace().name("two-requests").start()
  val scope = tracer.withSpanInScope(span)

  // run two requests
  val result = for {
    response1 <- sttp.get(uri"http://localhost:8123/hello1").send()
    response2 <- sttp.post(uri"http://localhost:8124/hello2").body(response1.unsafeBody).send()
  } yield response2.unsafeBody

  result.onComplete { _ =>
    scope.close()
    span.finish()
  }

  println("Result: " + Await.result(result, 10.seconds))

  // shut everything down
  actorSystem.terminate()
  tracing.close()
  reporter.close()
  sender.close()

  def startServer1(): Unit = {
    val routes: Route =
      path("hello1") {
        complete {
          "Hello 1!"
        }
      }

    Await.result(Http().bindAndHandle(routes, "localhost", 8123), 10.seconds)
  }

  def startServer2(): Unit = {
    val routes: Route =
      path("hello2") {
        entity(as[String]) { body =>
          complete {
            body + " & Hello 2!"
          }
        }
      }

    Await.result(Http().bindAndHandle(routes, "localhost", 8124), 10.seconds)
  }
}
