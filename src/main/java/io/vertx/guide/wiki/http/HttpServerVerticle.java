package io.vertx.guide.wiki.http;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import io.vertx.guide.wiki.database.WikiDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static io.vertx.guide.wiki.DatabaseConstants.*;

public class HttpServerVerticle extends AbstractVerticle {

  // We expose public constants for the verticle configuration parameters:
  // the HTTP port number and the name of the event bus destination to post
  // messages to the database verticle.
  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class); // create a general-purpose
                                                                                          // logger
  // from the org.slf4j package:

  private FreeMarkerTemplateEngine templateEngine;

  private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n" + "\n" + "Feel-free to write in Markdown!\n";

  private WikiDatabaseService dbService;

  private WebClient webClient;

  
  @Override
  public void start(Promise<Void> promise) throws Exception {

    String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");
    dbService = WikiDatabaseService.createProxy(vertx, wikiDbQueue);

    webClient = WebClient.create(vertx, new WebClientOptions()
      .setSsl(true)
      .setUserAgent("vert-x3"));

    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setKeyStoreOptions(new JksOptions()
        .setPath("server-keystore.jks")
        .setPassword("secret")));

    JDBCClient dbClient = JDBCClient.createShared(vertx, new JsonObject()
      .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, DEFAULT_WIKIDB_JDBC_URL))
      .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, DEFAULT_WIKIDB_JDBC_DRIVER_CLASS))
      .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, DEFAULT_JDBC_MAX_POOL_SIZE)));

    JDBCAuth auth = JDBCAuth.create(vertx, dbClient);

    Router router = Router.router(vertx);

    router.route().handler(CookieHandler.create());
    router.route().handler(BodyHandler.create());
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    router.route().handler(UserSessionHandler.create(auth));  // 1. We install a user session handler for all routes.

    AuthHandler authHandler = RedirectAuthHandler.create(auth, "/login"); // 2. This automatically redirects requests to /login when there is no user session for the request.
    router.route("/").handler(authHandler);  // 3. We install authHandler for all routes where authentication is required.
    router.route("/wiki/*").handler(authHandler);
    router.route("/action/*").handler(authHandler);

    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post("/action/save").handler(this::pageUpdateHandler);
    router.post("/action/create").handler(this::pageCreateHandler);
    router.get("/action/backup").handler(this::backupHandler);
    router.post("/action/delete").handler(this::pageDeletionHandler);

    router.get("/login").handler(this::loginHandler);
    router.post("/login-auth").handler(FormLoginHandler.create(auth));  // 1. FormLoginHandler is a helper for processing login submission requests. By default it expects the HTTP POST request to have: username as the login, password as the password, and return_url as the URL to redirect to upon success.

    router.get("/logout").handler(context -> {
      context.clearUser();  // 2. Logging out a user is a simple as clearing it from the current RoutingContext.
      context.response()
        .setStatusCode(302)
        .putHeader("Location", "/")
        .end();
    });

    Router apiRouter = Router.router(vertx);

    JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
      .setKeyStore(new KeyStoreOptions()
        .setPath("keystore.jceks")
        .setType("jceks")
        .setPassword("secret")));

    apiRouter.route().handler(JWTAuthHandler.create(jwtAuth, "/api/token"));

    apiRouter.get("/token").handler(context -> {

      JsonObject creds = new JsonObject()
        .put("username", context.request().getHeader("login"))
        .put("password", context.request().getHeader("password"));
      auth.authenticate(creds, authResult -> {  // 1. We expect login and password information to have been passed through HTTP request headers, and we authenticate using the authentication provider of the previous section.

        if (authResult.succeeded()) {
          User user = authResult.result();
          user.isAuthorized("create", canCreate -> {  // 2. Upon success we can query for roles.
            user.isAuthorized("delete", canDelete -> {
              user.isAuthorized("update", canUpdate -> {

                String token = jwtAuth.generateToken( // 3. We generate a token with username, canCreate, canDelete and canUpdate claims.
                  new JsonObject()
                    .put("username", context.request().getHeader("login"))
                    .put("canCreate", canCreate.succeeded() && canCreate.result())
                    .put("canDelete", canDelete.succeeded() && canDelete.result())
                    .put("canUpdate", canUpdate.succeeded() && canUpdate.result()),
                  new JWTOptions()
                    .setSubject("Wiki API")
                    .setIssuer("Vert.x"));
                context.response().putHeader("Content-Type", "text/plain").end(token);
              });
            });
          });
        } else {
          context.fail(401);
        }
      });
    });

    apiRouter.get("/pages").handler(this::apiRoot);
    apiRouter.get("/pages/:id").handler(this::apiGetPage);
    apiRouter.post().handler(BodyHandler.create());
    apiRouter.post("/pages").handler(this::apiCreatePage);
    apiRouter.put().handler(BodyHandler.create());
    apiRouter.put("/pages/:id").handler(this::apiUpdatePage);
    apiRouter.delete("/pages/:id").handler(this::apiDeletePage);
    router.mountSubRouter("/api", apiRouter);

    templateEngine = FreeMarkerTemplateEngine.create(vertx);

    int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
    server
      .requestHandler(router)
      .listen(portNumber, ar -> {
        if (ar.succeeded()) {
          LOGGER.info("HTTP server running on port " + portNumber);
          promise.complete();
        } else {
          LOGGER.error("Could not start a HTTP server", ar.cause());
          promise.fail(ar.cause());
        }
      });
  }

  private void apiDeletePage(RoutingContext context) {
    if (context.user().principal().getBoolean("canDelete", false)) {
      int id = Integer.valueOf(context.request().getParam("id"));
      dbService.deletePage(id, reply -> {
        handleSimpleDbReply(context, reply);
      });
    } else {
      context.fail(401);
    }
  }

    // The handleSimpleDbReply method is a helper for finishing the request processing:
    private void handleSimpleDbReply(RoutingContext context, AsyncResult<Void> reply) {
      if (reply.succeeded()) {
        context.response().setStatusCode(200);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(new JsonObject().put("success", true).encode());
      } else {
        context.response().setStatusCode(500);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(new JsonObject()
          .put("success", false)
          .put("error", reply.cause().getMessage()).encode());
      }
    }

    private void apiUpdatePage(RoutingContext context) {
      int id = Integer.valueOf(context.request().getParam("id"));
      JsonObject page = context.getBodyAsJson();
      if (!validateJsonPageDocument(context, page, "markdown")) {
        return;
      }
      dbService.savePage(id, page.getString("markdown"), reply -> {
        handleSimpleDbReply(context, reply);
      });
    }

      // The following validateJsonPageDocument method is a helper for doing validation and early error reporting, 
  // so that the remainder of the processing assume the presence of certain JSON entries:
  private boolean validateJsonPageDocument(RoutingContext context, JsonObject page, String... expectedKeys) {
    if (!Arrays.stream(expectedKeys).allMatch(page::containsKey)) {
      LOGGER.error("Bad page creation JSON payload: " + page.encodePrettily() + " from " + context.request().remoteAddress());
      context.response().setStatusCode(400);
      context.response().putHeader("Content-Type", "application/json");
      context.response().end(new JsonObject()
        .put("success", false)
        .put("error", "Bad request payload").encode());
      return false;
    }
    return true;
  }


  private void apiCreatePage(RoutingContext context) {
    JsonObject page = context.getBodyAsJson();
    if (!validateJsonPageDocument(context, page, "name", "markdown")) {
      return;
    }
    dbService.createPage(page.getString("name"), page.getString("markdown"), reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(201);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(new JsonObject().put("success", true).encode());
      } else {
        context.response().setStatusCode(500);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(new JsonObject()
          .put("success", false)
          .put("error", reply.cause().getMessage()).encode());
      }
    });
  }

  private void apiGetPage(RoutingContext context) {
    int id = Integer.valueOf(context.request().getParam("id"));
    dbService.fetchPageById(id, reply -> {
      JsonObject response = new JsonObject();
      if (reply.succeeded()) {
        JsonObject dbObject = reply.result();
        if (dbObject.getBoolean("found")) {
          JsonObject payload = new JsonObject()
            .put("name", dbObject.getString("name"))
            .put("id", dbObject.getInteger("id"))
            .put("markdown", dbObject.getString("content"))
            .put("html", Processor.process(dbObject.getString("content")));
          response
            .put("success", true)
            .put("page", payload);
          context.response().setStatusCode(200);
        } else {
          context.response().setStatusCode(404);
          response
            .put("success", false)
            .put("error", "There is no page with ID " + id);
        }
      } else {
        response
          .put("success", false)
          .put("error", reply.cause().getMessage());
        context.response().setStatusCode(500);
      }
      context.response().putHeader("Content-Type", "application/json");
      context.response().end(response.encode());
    });
  }




  private void apiRoot(RoutingContext context) {
    dbService.fetchAllPagesData(reply -> {
      JsonObject response = new JsonObject();
      if (reply.succeeded()) {
        List<JsonObject> pages = reply.result()
          .stream()
          .map(obj -> new JsonObject()
            .put("id", obj.getInteger("ID"))  // 1. Nós apenas remapeameamos os resultados do database em page information entry objects.
            .put("name", obj.getString("NAME")))
          .collect(Collectors.toList());
        response
          .put("success", true)
          .put("pages", pages); // 2. The resulting JSON array becomes the value for the pages key in the response payload.
        context.response().setStatusCode(200);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(response.encode()); // 3. JsonObject#encode() gives a compact String representation of the JSON data.
      } else {
        response
          .put("success", false)
          .put("error", reply.cause().getMessage());
        context.response().setStatusCode(500);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(response.encode());
      }
    });
  }

  private void indexHandler(RoutingContext context) {
    context.user().isAuthorized("create", res -> {  // 1. This is how a permission query is made. Note that this is an asynchronous operation.
      boolean canCreatePage = res.succeeded() && res.result();  // 2. We use the result to…​
      dbService.fetchAllPages(reply -> {
        if (reply.succeeded()) {
          context.put("title", "Wiki home");
          context.put("pages", reply.result().getList());
          context.put("canCreatePage", canCreatePage);  // 3. …​leverage it in the HTML template.
          context.put("username", context.user().principal().getString("username"));  // 4. We also have access to the user login.
          templateEngine.render(context.data(), "templates/index.ftl", ar -> {
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
    });
  }

  private void pageRenderingHandler(RoutingContext context) {
    String requestedPage = context.request().getParam("page");
    dbService.fetchPage(requestedPage, reply -> {
      if (reply.succeeded()) {

        JsonObject payLoad = reply.result();
        boolean found = payLoad.getBoolean("found");
        String rawContent = payLoad.getString("rawContent", EMPTY_PAGE_MARKDOWN);
        context.put("title", requestedPage);
        context.put("id", payLoad.getInteger("id", -1));
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

  private void loginHandler(RoutingContext context) {
    context.put("title", "Login");
    templateEngine.render(context.data(), "templates/login.ftl", ar -> {
      if (ar.succeeded()) {
        context.response().putHeader("Content-Type", "text/html");
        context.response().end(ar.result());
      } else {
        context.fail(ar.cause());
      }
    });
  }

  private void pageUpdateHandler(RoutingContext context) {
    String title = context.request().getParam("title");

    Handler<AsyncResult<Void>> handler = reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/wiki/" + title);
        context.response().end();
      } else {
        context.fail(reply.cause());
      }
    };

    String markdown = context.request().getParam("markdown");
    if ("yes".equals(context.request().getParam("newPage"))) {
      dbService.createPage(title, markdown, handler);
    } else {
      dbService.savePage(Integer.valueOf(context.request().getParam("id")), markdown, handler);
    }
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
    context.user().isAuthorized("delete", res -> {
      if (res.succeeded() && res.result()) {
  
        dbService.deletePage(Integer.valueOf(context.request().getParam("id")), reply -> {
          if (reply.succeeded()) {
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/");
            context.response().end();
          } else {
            context.fail(reply.cause());
          }
        });
      } else {
         context.response().setStatusCode(403).end();
      }
    });
  }



  private void backupHandler(RoutingContext context) {
    dbService.fetchAllPagesData(reply -> {
      if (reply.succeeded()) {
  
        JsonArray filesObject = new JsonArray();
        JsonObject payload = new JsonObject() // 1. the snippet creation request payload is a JSON document as outlined in the service API documentation.
          .put("files", filesObject)
          .put("language", "plaintext")
          .put("title", "vertx-wiki-backup")
          .put("public", true);
  
        reply
          .result()
          .forEach(page -> {
            JsonObject fileObject = new JsonObject(); // 2. Each file is an entry under the files object of the payload, with a title and content.
            fileObject.put("name", page.getString("NAME"));
            fileObject.put("content", page.getString("CONTENT"));
            filesObject.add(fileObject);
          });
  
        webClient.post(443, "snippets.glot.io", "/snippets") // 3. the web client needs to issue a POST request on port 443 (HTTPS), and the path must be /snippets.
          .putHeader("Content-Type", "application/json")
          .as(BodyCodec.jsonObject()) // 4. the BodyCodec class provides a helper to specify that the response will be directly converted to a Vert.x JsonObject instance. It is also possible to use BodyCodec#json(Class<T>) and the JSON data will be mapped to a Java object of type T (this uses Jackson data mapping under the hood).
          .sendJsonObject(payload, ar -> {  // 5. sendJsonObject is a helper for triggering the HTTP request with a JSON payload.
            if (ar.succeeded()) {
              HttpResponse<JsonObject> response = ar.result();
              if (response.statusCode() == 200) {
                String url = "https://glot.io/snippets/" + response.body().getString("id");
                context.put("backup_gist_url", url);  // 6. upon success we can get the snippet identifier, and construct a URL to the user-friendly web representation.
                indexHandler(context);
              } else {
                StringBuilder message = new StringBuilder()
                  .append("Could not backup the wiki: ")
                  .append(response.statusMessage());
                JsonObject body = response.body();
                if (body != null) {
                  message.append(System.getProperty("line.separator"))
                    .append(body.encodePrettily());
                }
                LOGGER.error(message.toString());
                context.fail(502);
              }
            } else {
              Throwable err = ar.cause();
              LOGGER.error("HTTP Client error", err);
              context.fail(err);
            }
        });
  
      } else {
        context.fail(reply.cause());
      }
    });
  }
}