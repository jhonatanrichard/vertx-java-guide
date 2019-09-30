package io.vertx.guide.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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

/* AbstractVerticle provê:
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
  private FreeMarkerTemplateEngine templateEngine;

  private static final String EMPTY_PAGE_MARKDOWN =
  "# A new page\n" +
    "\n" +
    "Feel-free to write in Markdown!\n";

  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class); // create a general-purpose logger from the org.slf4j package:

  /* There are 2 forms of start (and stop) methods: 1 with no argument and 
  1 with a promise object reference. The no-argument variants imply tahat the 
  verticle initialization or house-keeping phases always succeed unless an exception 
  is being thrown. The variants with a promise object provide a more fine-grained approach 
  to eventually signal that operations succeeded or not. Indeed, some initialization or cleanup code may 
  require asynchronous operations, so reporting via a promise object naturally fits with asynchronous idioms. */
  @Override
  public void start(Promise<Void> promise) {
    // By having each method returning a future object, the implementation of the start method becomes a composition:
    Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());

    /* When the future of prepareDatabase completes successfully, then startHttpServer is called and the steps future 
    completes depending of the outcome of the future returned by startHttpServer. startHttpServer is never called 
    if prepareDatabase encounters an error, in which case the steps future is in a failed state and becomes completed
     with the exception describing the error. */
    steps.setHandler(promise);
    steps.setHandler(ar -> {    
      /* ar is of type AsyncResult<Void> 
      AsyncResult<T> is used to pass the result of an asynchronous processing and may 
      either yield a value of type T on success or a failure exception if the processing failed. */
      if(ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
  }

  /* Cada fase abaixo pode falhar: Each phase can fail (e.g., the HTTP server TCP port is already being used), and 
  they should not run in parallel as the web application code first needs the database access to work.
  To make our code cleaner we will define 1 method per phase, and adopt a pattern of returning a future object to notify 
  when each of the phases completes, and whether it did so successfully or not */

  // we need to establish a JDBC database connection, and also make sure that the database schema is in place
  /*private Future<Void> prepareDatabase() {
    Promise<Void> promise = Promise.promise();
    // (...)
    return promise.future();
  }*/

  private Future<Void> prepareDatabase() {
    Promise<Void> promise = Promise.promise();
  
    // createShared creates a shared connection to be shared among verticles known to the vertx instance, which in general is a good thing.
    dbClient = JDBCClient.createShared(vertx, new JsonObject()  
      .put("url", "jdbc:hsqldb:file:db/wiki")  // The JDBC client connection is made by passing a Vert.x JSON object. Here url is the JDBC URL.
      .put("driver_class", "org.hsqldb.jdbcDriver")   // Just like url, driver_class is specific to the JDBC driver being used and points to the driver class.
      .put("max_pool_size", 30));  // max_pool_size is the number of concurrent connections. We chose 30 here, but it is just an arbitrary number.
  
    /* Getting a connection is an asynchronous operation that gives us an AsyncResult<SQLConnection>. It must then be tested to see if the 
    connection could be established or not (AsyncResult is actually a super-interface of Future). */
    dbClient.getConnection(ar -> {    
      if (ar.failed()) {
        LOGGER.error("Could not open a database connection", ar.cause());
        promise.fail(ar.cause());    // If the SQL connection could not be obtained, then the method future is completed to fail with the AsyncResult-provided exception via the cause method.
      } else {
        SQLConnection connection = ar.result();   // The SQLConnection is the result of the successful AsyncResult. We can use it to perform a SQL query.
        connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
          connection.close();   // Before checking whether the SQL query succeeded or not, we must release it by calling close, otherwise the JDBC client connection pool can eventually drain.
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


  //we need to start a HTTP server for the web application
  private Future<Void> startHttpServer() {
    Promise<Void> promise = Promise.promise();
    HttpServer server = vertx.createHttpServer();   // The vertx context object provides methods to create HTTP servers, clients, TCP/UDP servers and clients, etc.
  
    Router router = Router.router(vertx);   // The Router class comes from vertx-web: io.vertx.ext.web.Router.
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler); // Routes have their own handlers, and they can be defined by URL and/or by HTTP method. For short handlers a Java lambda is an option, but for more elaborate handlers it is a good idea to reference private methods instead. Note that URLs can be parametric: /wiki/:page will match a request like /wiki/Hello, in which case a page parameter will be available with value Hello.
    router.post().handler(BodyHandler.create()); // This makes all HTTP POST requests go through a first handler, here io.vertx.ext.web.handler.BodyHandler. This handler automatically decodes the body from the HTTP requests (e.g., form submissions), which can then be manipulated as Vert.x buffer objects
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);
  
    templateEngine = FreeMarkerTemplateEngine.create(vertx);
  
    server
      .requestHandler(router)   // The router object can be used as a HTTP server handler, which then dispatches to other handlers as defined above.
      .listen(8080, ar -> {   // Starting a HTTP server is an asynchronous operation, so an AsyncResult<HttpServer> needs to be checked for success. By the way the 8080 parameter specifies the TCP port to be used by the server.
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

  private void indexHandler(RoutingContext context) {
    dbClient.getConnection(car -> {
      if (car.succeeded()) {
        SQLConnection connection = car.result();
        connection.query(SQL_ALL_PAGES, res -> {
          connection.close();
  
          if (res.succeeded()) {
            List<String> pages = res.result() // 1. SQL query results are being returned as instances of JsonArray and JsonObject.
              .getResults()
              .stream()
              .map(json -> json.getString(0))
              .sorted()
              .collect(Collectors.toList());
  
            context.put("title", "Wiki home"); // 2. The RoutingContext instance can be used to put arbitrary key / value data that is then available from templates, or chained router handlers.
            context.put("pages", pages);
            templateEngine.render(context.data(), "templates/index.ftl", ar -> {   // 3. Rendering a template is an asynchronous operation that leads us to the usual AsyncResult handling pattern.
              if (ar.succeeded()) {
                context.response().putHeader("Content-Type", "text/html");
                context.response().end(ar.result()); // 4. The AsyncResult contains the template rendering as a String in case of success, and we can end the HTTP response stream with the value
              } else {
                context.fail(ar.cause());
              }
            });
  
          } else {
            context.fail(res.cause()); // 5. In case of failure the fail method from RoutingContext provides a sensible way to return a HTTP 500 error to the HTTP client.
          }
        });
      } else {
        context.fail(car.cause());
      }
    });
  }

  private void pageRenderingHandler(RoutingContext context) {
    String page = context.request().getParam("page");   // 1. URL parameters (/wiki/:page here) can be accessed through the context request object.
  
    dbClient.getConnection(car -> {
      if (car.succeeded()) {
  
        SQLConnection connection = car.result();
        connection.queryWithParams(SQL_GET_PAGE, new JsonArray().add(page), fetch -> {  // 2. Passing argument values to SQL queries is done using a JsonArray, with the elements in order of the ? symbols in the SQL query.
          connection.close();
          if (fetch.succeeded()) {
  
            JsonArray row = fetch.result().getResults()
              .stream()
              .findFirst()
              .orElseGet(() -> new JsonArray().add(-1).add(EMPTY_PAGE_MARKDOWN));
            Integer id = row.getInteger(0);
            String rawContent = row.getString(1);
  
            context.put("title", page);
            context.put("id", id);
            context.put("newPage", fetch.result().getResults().size() == 0 ? "yes" : "no");
            context.put("rawContent", rawContent);
            context.put("content", Processor.process(rawContent));  // 3. The Processor class comes from the txtmark Markdown rendering library that we use.
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
            context.fail(fetch.cause());
          }
        });
  
      } else {
        context.fail(car.cause());
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

  private void pageUpdateHandler(RoutingContext context) {
    String id = context.request().getParam("id");   // 1. Form parameters sent through a HTTP POST request are available from the RoutingContext object. Note that without a BodyHandler within the Router configuration chain these values would not be available, and the form submission payload would need to be manually decoded from the HTTP POST request payload.
    String title = context.request().getParam("title");
    String markdown = context.request().getParam("markdown");
    boolean newPage = "yes".equals(context.request().getParam("newPage"));  // 2. We rely on a hidden form field rendered in the page.ftl FreeMarker template to know if we are updating an existing page or saving a new page.
  
    dbClient.getConnection(car -> {
      if (car.succeeded()) {
        SQLConnection connection = car.result();
        String sql = newPage ? SQL_CREATE_PAGE : SQL_SAVE_PAGE;
        JsonArray params = new JsonArray();   // 3. Again, preparing the SQL query with parameters uses a JsonArray to pass values.
        if (newPage) {
          params.add(title).add(markdown);
        } else {
          params.add(markdown).add(id);
        }
        connection.updateWithParams(sql, params, res -> {   // 4. The updateWithParams method is used for insert / update / delete SQL queries.
          connection.close();
          if (res.succeeded()) {
            context.response().setStatusCode(303);    // 5. Upon success, we simply redirect to the page that has been edited.
            context.response().putHeader("Location", "/wiki/" + title);
            context.response().end();
          } else {
            context.fail(res.cause());
          }
        });
      } else {
        context.fail(car.cause());
      }
    });
  }

  // given a wiki entry identifier, it issues a delete SQL query then redirects to the wiki index page
  private void pageDeletionHandler(RoutingContext context) {
    String id = context.request().getParam("id");
    dbClient.getConnection(car -> {
      if (car.succeeded()) {
        SQLConnection connection = car.result();
        connection.updateWithParams(SQL_DELETE_PAGE, new JsonArray().add(id), res -> {
          connection.close();
          if (res.succeeded()) {
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/");
            context.response().end();
          } else {
            context.fail(res.cause());
          }
        });
      } else {
        context.fail(car.cause());
      }
    });
  }

}