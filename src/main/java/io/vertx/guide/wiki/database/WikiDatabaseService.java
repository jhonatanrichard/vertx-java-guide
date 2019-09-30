package io.vertx.guide.wiki.database;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.HashMap;

@ProxyGen // The ProxyGen annotation is used to trigger the code generation of a proxy for
          // clients of that service.
@VertxGen

// Parameter types need to be strings, Java primitive types, JSON objects or
// arrays, any enumeration type or a java.util collection (List / Set / Map) of
// the previous types. The only way to support arbitrary Java classes is to have
// them as Vert.x data objects, annotated with @DataObject. The last opportunity
// to pass other types is service reference types.
public interface WikiDatabaseService {

    // The Fluent annotation is optional, but allows fluent interfaces where
    // operations can be chained by returning the service instance. This is mostly
    // useful for the code generator when the service shall be consumed from other
    // JVM languages.
    @Fluent
    WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler);

    // Since services provide asynchronous results, the last argument of a service
    // method needs to be a Handler<AsyncResult<T>> where T is any of the types
    // suitable for code generation as described above.
    @Fluent
    WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler);

    @Fluent
    WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler);

    @GenIgnore
    static WikiDatabaseService create(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries,
            Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        return new WikiDatabaseServiceImpl(dbClient, sqlQueries, readyHandler);
    }

    @GenIgnore
    static WikiDatabaseService createProxy(Vertx vertx, String address) {
        return new WikiDatabaseServiceVertxEBProxy(vertx, address);
    }
}