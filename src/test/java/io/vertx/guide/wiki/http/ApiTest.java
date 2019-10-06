package io.vertx.guide.wiki.http;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.guide.wiki.database.WikiDatabaseVerticle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(VertxUnitRunner.class)
public class ApiTest {

  private Vertx vertx;
  private WebClient webClient;
  private String jwtTokenHeaderValue;

  @Before
  // The HTTP server verticle needs the database verticle to be running, so we need to deploy both in our test Vert.x context
  public void prepare(TestContext context) {
    vertx = Vertx.vertx();

    JsonObject dbConf = new JsonObject()
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true") // 1. We use a different JDBC URL to use an in-memory database for the tests.
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

    vertx.deployVerticle(new AuthInitializerVerticle(),
      new DeploymentOptions().setConfig(dbConf), context.asyncAssertSuccess());

    vertx.deployVerticle(new WikiDatabaseVerticle(),
      new DeploymentOptions().setConfig(dbConf), context.asyncAssertSuccess());

    vertx.deployVerticle(new HttpServerVerticle(), context.asyncAssertSuccess());

    webClient = WebClient.create(vertx, new WebClientOptions()
    .setDefaultHost("localhost")
    .setDefaultPort(8080)
    .setSsl(true) // 1. garante ssl
    .setTrustOptions(new JksOptions().setPath("server-keystore.jks").setPassword("secret"))); // 2. Since the certificate is self-signed, we need to explicitly trust it otherwise the web client connections will fail just like a web browser would.
  }

  @After
  public void finish(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  // The proper test case is a simple scenario where all types of requests are being made. It creates a page, fetches it, updates it then deletes it
  public void play_with_api(TestContext context) {
    Async async = context.async();

    Promise<HttpResponse<String>> tokenPromise = Promise.promise();
    webClient.get("/api/token")
      .putHeader("login", "foo")  // 1. Credentials are passed as headers.
      .putHeader("password", "bar")
      .as(BodyCodec.string())   // 2. The response payload is of text/plain type, so we use that for the body decoding codec.
      .send(tokenPromise);
    Future<HttpResponse<String>> tokenFuture = tokenPromise.future(); // 3. Upon success we complete the tokenRequest future with the token value.

    JsonObject page = new JsonObject()
      .put("name", "Sample")
      .put("markdown", "# A page");
    // (...)

    Future<HttpResponse<JsonObject>> postPageFuture = tokenFuture.compose(tokenResponse -> {
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      jwtTokenHeaderValue = "Bearer " + tokenResponse.body();   // 1. We store the token with the Bearer prefix to the field for the next requests.
      webClient.post("/api/pages")
        .putHeader("Authorization", jwtTokenHeaderValue)  // 2. We pass the token as a header.
        .as(BodyCodec.jsonObject())
        .sendJsonObject(page, promise);
      return promise.future();
    });

    Future<HttpResponse<JsonObject>> getPageFuture = postPageFuture.compose(resp -> {
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      webClient.get("/api/pages")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .send(promise);
      return promise.future();
    });
    // (...)

    Future<HttpResponse<JsonObject>> updatePageFuture = getPageFuture.compose(resp -> {
      JsonArray array = resp.body().getJsonArray("pages");
      context.assertEquals(1, array.size());
      context.assertEquals(0, array.getJsonObject(0).getInteger("id"));
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      JsonObject data = new JsonObject()
        .put("id", 0)
        .put("markdown", "Oh Yeah!");
      webClient.put("/api/pages/0")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .sendJsonObject(data, promise);
      return promise.future();
    });

    Future<HttpResponse<JsonObject>> deletePageFuture = updatePageFuture.compose(resp -> {
      context.assertTrue(resp.body().getBoolean("success"));
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      webClient.delete("/api/pages/0")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .send(promise);
      return promise.future();
    });

    deletePageFuture.setHandler(ar -> {
      if (ar.succeeded()) {
        context.assertTrue(ar.result().body().getBoolean("success"));
        async.complete();
      } else {
        context.fail(ar.cause());
      }
    });

    async.awaitSuccess(5000);
  }
}