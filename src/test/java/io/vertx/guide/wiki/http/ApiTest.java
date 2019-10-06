package io.vertx.guide.wiki.http;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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

  @Before
  // The HTTP server verticle needs the database verticle to be running, so we need to deploy both in our test Vert.x context
  public void prepare(TestContext context) {
    vertx = Vertx.vertx();

    JsonObject dbConf = new JsonObject()
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true") // 1. We use a different JDBC URL to use an in-memory database for the tests.
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

    vertx.deployVerticle(new WikiDatabaseVerticle(),
      new DeploymentOptions().setConfig(dbConf), context.asyncAssertSuccess());

    vertx.deployVerticle(new HttpServerVerticle(), context.asyncAssertSuccess());

    webClient = WebClient.create(vertx, new WebClientOptions()
      .setDefaultHost("localhost")
      .setDefaultPort(8080));
  }

  @After
  public void finish(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  // The proper test case is a simple scenario where all types of requests are being made. It creates a page, fetches it, updates it then deletes it
  public void play_with_api(TestContext context) {
    Async async = context.async();

    JsonObject page = new JsonObject()
      .put("name", "Sample")
      .put("markdown", "# A page");

    Promise<HttpResponse<JsonObject>> postPagePromise = Promise.promise();
    webClient.post("/api/pages")
      .as(BodyCodec.jsonObject())
      .sendJsonObject(page, postPagePromise);

    Future<HttpResponse<JsonObject>> getPageFuture = postPagePromise.future().compose(resp -> {
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      webClient.get("/api/pages")
        .as(BodyCodec.jsonObject())
        .send(promise);
      return promise.future();
    });

    Future<HttpResponse<JsonObject>> updatePageFuture = getPageFuture.compose(resp -> {
      JsonArray array = resp.body().getJsonArray("pages");
      context.assertEquals(1, array.size());
      context.assertEquals(0, array.getJsonObject(0).getInteger("id"));
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      JsonObject data = new JsonObject()
        .put("id", 0)
        .put("markdown", "Oh Yeah!");
      webClient.put("/api/pages/0")
        .as(BodyCodec.jsonObject())
        .sendJsonObject(data, promise);
      return promise.future();
    });

    Future<HttpResponse<JsonObject>> deletePageFuture = updatePageFuture.compose(resp -> {
      context.assertTrue(resp.body().getBoolean("success"));
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      webClient.delete("/api/pages/0")
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