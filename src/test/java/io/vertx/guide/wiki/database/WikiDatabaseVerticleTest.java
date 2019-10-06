package io.vertx.guide.wiki.database;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// With that runner, JUnit test and life-cycle methods accept a TestContext argument. 
// This object provides access to basic assertions, a context to store data, and several 
// async-oriented helpers that we will see in this section.
@RunWith(VertxUnitRunner.class) // annotation para usar as features do vertx-unit
public class WikiDatabaseVerticleTest {

  private Vertx vertx;
  private WikiDatabaseService service;

  @Before
  public void prepare(TestContext context) throws InterruptedException {
    vertx = Vertx.vertx();

    JsonObject conf = new JsonObject() // Nós somente sobrescrevemos algumas das configurações do verticle, as outras continuam as default
        .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
        .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

    vertx.deployVerticle(new WikiDatabaseVerticle(), new DeploymentOptions().setConfig(conf),
        context.asyncAssertSuccess(id -> // asyncAssertSuccess is useful to provide a handler that checks for the success of an asynchronous operation. 
        // There is a variant with no arguments, and a variant like this one where we can chain the result to another handler.
        service = WikiDatabaseService.createProxy(vertx, WikiDatabaseVerticle.CONFIG_WIKIDB_QUEUE)));
  }

  @After
  // Cleaning up the Vert.x context is straightforward, and again we use asyncAssertSuccess to ensure that no error was encountered:
  public void finish(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test // (timeout=5000)
  // There is a default (long) timeout for asynchronous test cases, but it can be overridden through the JUnit @Test annotation.
  public void async_behavior(TestContext context) { // TestContext is a parameter provided by the runner.
    Vertx vertx = Vertx.vertx(); // since we are in unit tests, we need to create a Vert.x context.
    context.assertEquals("foo", "foo"); // Here is an example of a basic TestContext assertion.
    Async a1 = context.async(); // We get a first Async object that can later be completed (or failed).
    Async a2 = context.async(3); // This Async object works as a countdown that completes successfully after 3 calls.
    vertx.setTimer(100, n -> a1.complete()); // We complete when the timer fires.
    vertx.setPeriodic(100, n -> a2.countDown()); // Each periodic task tick triggers a countdown. The test passes when all Async objects have completed.
  }

  @Test
  public void crud_operations(TestContext context) {
    Async async = context.async();

    service.createPage("Test", "Some content", context.asyncAssertSuccess(v1 -> {

      service.fetchPage("Test", context.asyncAssertSuccess(json1 -> {
        context.assertTrue(json1.getBoolean("found"));
        context.assertTrue(json1.containsKey("id"));
        context.assertEquals("Some content", json1.getString("rawContent"));

        service.savePage(json1.getInteger("id"), "Yo!", context.asyncAssertSuccess(v2 -> {

          service.fetchAllPages(context.asyncAssertSuccess(array1 -> {
            context.assertEquals(1, array1.size());

            service.fetchPage("Test", context.asyncAssertSuccess(json2 -> {
              context.assertEquals("Yo!", json2.getString("rawContent"));

              service.deletePage(json1.getInteger("id"), v3 -> {

                service.fetchAllPages(context.asyncAssertSuccess(array2 -> {
                  context.assertTrue(array2.isEmpty());
                  async.complete(); // This is where the sole Async eventually completes.
                }));
              });
            }));
          }));
        }));
      }));
    }));
    async.awaitSuccess(5000); // This is an alternative to exiting the test case method and relying on a JUnit timeout. 
    // Here the execution on the test case thread waits until either the Async completes or the timeout period elapses.
  }
}