package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

class RegresPerformanceTest extends Simulation {

  // Regres.in API konfigürasyonu
  val httpProtocol = http
    .baseUrl("https://reqres.in/api")
    .acceptHeader("application/json")
    .userAgentHeader("Gatling Regres Performance Test")

  // Kullanıcı veri beslemesi
  val userFeeder = Iterator.continually(Map(
    "name" -> s"User ${Random.alphanumeric.take(5).mkString}",
    "job" -> s"Job ${Random.alphanumeric.take(3).mkString}"
  ))

  // 1. Kullanıcı Listesi Performans Testi
  val userListScenario = scenario("User List Performance")
    .exec(
      http("List Users")
        .get("/users")
        .queryParam("page", Random.nextInt(3) + 1) // Rastgele sayfa
        .check(status.is(200))
        .check(jsonPath("$.page").saveAs("currentPage"))
    )

  // 2. Kullanıcı Oluşturma Yük Testi
  val userCreateScenario = scenario("User Creation Load Test")
    .feed(userFeeder)
    .exec(
      http("Create User")
        .post("/users")
        .body(StringBody("""{"name": "${name}", "job": "${job}"}"""))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("createdUserId"))
    )

  // 3. Kullanıcı Güncelleme Stres Testi
  val userUpdateScenario = scenario("User Update Stress Test")
    .feed(userFeeder)
    .exec(
      http("Update User")
        .put("/users/2")
        .body(StringBody("""{"name": "${name}", "job": "${job}"}"""))
        .check(status.is(200))
    )

  // 4. Kullanıcı Silme Test Senaryosu
  val userDeleteScenario = scenario("User Delete Test")
    .exec(
      http("Delete User")
        .delete("/users/2")
        .check(status.is(204))
    )

  // 5. Kayıt ve Giriş Entegrasyon Testi
  val registrationScenario = scenario("Registration Integration Test")
    .exec(
      http("Register User")
        .post("/register")
        .body(StringBody("""{"email": "eve.holt@reqres.in", "password": "pistol"}"""))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("authToken"))
    )
    .exec(
      http("Login User")
        .post("/login")
        .body(StringBody("""{"email": "eve.holt@reqres.in", "password": "pistol"}"""))
        .check(status.is(200))
    )

  // Yük Yapılandırması
  setUp(
    userListScenario.inject(
      rampUsers(50).during(30.seconds),
      constantUsersPerSec(20).during(1.minute)
    ),
    userCreateScenario.inject(
      heavisideUsers(100).during(1.minute)
    ),
    userUpdateScenario.inject(
      rampUsersPerSec(10).to(50).during(2.minutes)
    ),
    userDeleteScenario.inject(
      atOnceUsers(20)
    ),
    registrationScenario.inject(
      constantUsersPerSec(10).during(1.minute)
    )
  ).protocols(httpProtocol)
    .maxDuration(10.minutes)
    .assertions(
      global.responseTime.max.lte(2000),
      global.successfulRequests.percent.gte(95),
      details("Create User").failedRequests.count.lte(10)
    )
}