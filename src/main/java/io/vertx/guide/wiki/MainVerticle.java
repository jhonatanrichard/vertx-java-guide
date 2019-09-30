package io.vertx.guide.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/* AbstractVerticle prove:
- life-cycle start and stop methods to override,
- a protected field called vertx that references the Vert.x environment where the verticle is being deployed,
- an accessor to some configuration object that allows passing external configuration to a verticle.
*/
public class MainVerticle extends AbstractVerticle {

  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";





  private JDBCClient dbClient; // serves as the connection to the database






  

  /*
   * Cada fase abaixo pode falhar: Each phase can fail (e.g., the HTTP server TCP
   * port is already being used), and they should not run in parallel as the web
   * application code first needs the database access to work. To make our code
   * cleaner we will define 1 method per phase, and adopt a pattern of returning a
   * future object to notify when each of the phases completes, and whether it did
   * so successfully or not
   */

  // we need to establish a JDBC database connection, and also make sure that the
  // database schema is in place
  /*
   * private Future<Void> prepareDatabase() { Promise<Void> promise =
   * Promise.promise(); // (...) return promise.future(); }
   */

  private Future<Void> prepareDatabase() {
    Promise<Void> promise = Promise.promise();

    // createShared creates a shared connection to be shared among verticles known
    // to the vertx instance, which in general is a good thing.
    dbClient = JDBCClient.createShared(vertx, new JsonObject().put("url", "jdbc:hsqldb:file:db/wiki") // The JDBC client
                                                                                                      // connection is
                                                                                                      // made by passing
                                                                                                      // a Vert.x JSON
                                                                                                      // object. Here
                                                                                                      // url is the JDBC
                                                                                                      // URL.
        .put("driver_class", "org.hsqldb.jdbcDriver") // Just like url, driver_class is specific to the JDBC driver
                                                      // being used and points to the driver class.
        .put("max_pool_size", 30)); // max_pool_size is the number of concurrent connections. We chose 30 here, but
                                    // it is just an arbitrary number.

    /*
     * Getting a connection is an asynchronous operation that gives us an
     * AsyncResult<SQLConnection>. It must then be tested to see if the connection
     * could be established or not (AsyncResult is actually a super-interface of
     * Future).
     */
    dbClient.getConnection(ar -> {
      if (ar.failed()) {
        LOGGER.error("Could not open a database connection", ar.cause());
        promise.fail(ar.cause()); // If the SQL connection could not be obtained, then the method future is
                                  // completed to fail with the AsyncResult-provided exception via the cause
                                  // method.
      } else {
        SQLConnection connection = ar.result(); // The SQLConnection is the result of the successful AsyncResult. We can
                                                // use it to perform a SQL query.
        connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
          connection.close(); // Before checking whether the SQL query succeeded or not, we must release it by
                              // calling close, otherwise the JDBC client connection pool can eventually
                              // drain.
          if (create.failed()) {
            LOGGER.error("Database preparation error", create.cause());
            promise.fail(create.cause());
          } else {
            promise.complete(); // We complete the method future object with a success.
          }
        });
      }
    });

    return promise.future();
  }

  // we need to start a HTTP server for the web application
  private Future<Void> startHttpServer() {
    Promise<Void> promise = Promise.promise();
    HttpServer server = vertx.createHttpServer(); // The vertx context object provides methods to create HTTP servers,
                                                  // clients, TCP/UDP servers and clients, etc.

    Router router = Router.router(vertx); // The Router class comes from vertx-web: io.vertx.ext.web.Router.
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler); // Routes have their own handlers, and they can be
                                                                   // defined by URL and/or by HTTP method. For short
                                                                   // handlers a Java lambda is an option, but for more
                                                                   // elaborate handlers it is a good idea to reference
                                                                   // private methods instead. Note that URLs can be
                                                                   // parametric: /wiki/:page will match a request like
                                                                   // /wiki/Hello, in which case a page parameter will
                                                                   // be available with value Hello.
    router.post().handler(BodyHandler.create()); // This makes all HTTP POST requests go through a first handler, here
                                                 // io.vertx.ext.web.handler.BodyHandler. This handler automatically
                                                 // decodes the body from the HTTP requests (e.g., form submissions),
                                                 // which can then be manipulated as Vert.x buffer objects
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);

    templateEngine = FreeMarkerTemplateEngine.create(vertx);

    server.requestHandler(router) // The router object can be used as a HTTP server handler, which then dispatches
                                  // to other handlers as defined above.
        .listen(8080, ar -> { // Starting a HTTP server is an asynchronous operation, so an
                              // AsyncResult<HttpServer> needs to be checked for success. By the way the 8080
                              // parameter specifies the TCP port to be used by the server.
          if (ar.succeeded()) {
            LOGGER.info("HTTP server running on port 8080");
            promise.complete();
          } else {
            LOGGER.error("Could not start a HTTP server", ar.cause());
            promise.fail(ar.cause());
          }
        });

    return promise.future();
  }

  

  private void pageRenderingHandler(RoutingContext context) {

    String requestedPage = context.request().getParam("page");
    JsonObject request = new JsonObject().put("page", requestedPage);

    DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-page");
    vertx.eventBus().request(wikiDbQueue, request, options, reply -> {

      if (reply.succeeded()) {
        JsonObject body = (JsonObject) reply.result().body();

        boolean found = body.getBoolean("found");
        String rawContent = body.getString("rawContent", EMPTY_PAGE_MARKDOWN);
        context.put("title", requestedPage);
        context.put("id", body.getInteger("id", -1));
        context.put("newPage", found ? "no" : "yes");
        context.put("rawContent", rawContent);
        context.put("content", Processor.process(rawContent));
        context.put("timestamp", new Date().toString());

        templateEngine.render(context.data(), "templates/page.ftl", ar -> {
          if (ar.succeeded()) {
            context.response().putHeader("Content-Type", "text/html");
            context.response().end(ar.result());
          } else {
            context.fail(ar.cause());
          }
        });

      } else {
        context.fail(reply.cause());
      }
    });
  }

  private void pageUpdateHandler(RoutingContext context) {

    String title = context.request().getParam("title");
    JsonObject request = new JsonObject()
      .put("id", context.request().getParam("id"))
      .put("title", title)
      .put("markdown", context.request().getParam("markdown"));
  
    DeliveryOptions options = new DeliveryOptions();
    if ("yes".equals(context.request().getParam("newPage"))) {
      options.addHeader("action", "create-page");
    } else {
      options.addHeader("action", "save-page");
    }
  
    vertx.eventBus().request(wikiDbQueue, request, options, reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/wiki/" + title);
        context.response().end();
      } else {
        context.fail(reply.cause());
      }
    });
  }

  private void pageCreateHandler(RoutingContext context) {
    String pageName = context.request().getParam("name");
    String location = "/wiki/" + pageName;
    if (pageName == null || pageName.isEmpty()) {
      location = "/";
    }
    context.response().setStatusCode(303);
    context.response().putHeader("Location", location);
    context.response().end();
  }

  private void pageDeletionHandler(RoutingContext context) {
    String id = context.request().getParam("id");
    JsonObject request = new JsonObject().put("id", id);
    DeliveryOptions options = new DeliveryOptions().addHeader("action", "delete-page");
    vertx.eventBus().request(wikiDbQueue, request, options, reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/");
        context.response().end();
      } else {
        context.fail(reply.cause());
      }
    });
  }

}