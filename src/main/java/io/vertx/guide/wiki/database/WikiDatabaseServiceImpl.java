
package io.vertx.guide.wiki.database;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.SingleHelper;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.sql.SQLClientHelper;

import java.util.HashMap;
import java.util.List;

class WikiDatabaseServiceImpl implements WikiDatabaseService {

  private final HashMap<SqlQuery, String> sqlQueries;
  private final JDBCClient dbClient;

  WikiDatabaseServiceImpl(io.vertx.ext.jdbc.JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
    this.dbClient = new JDBCClient(dbClient);
    this.sqlQueries = sqlQueries;

    SQLClientHelper.usingConnectionSingle(this.dbClient, conn -> conn // 1. borrows a connection from the pool and provides it in a callback.
      .rxExecute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE)) // 2. returns a Single generated from execution of queries and transformation of results.
      .andThen(Single.just(this)))
      .subscribe(SingleHelper.toObserver(readyHandler));
  }

  @Override
  public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
    dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES))
      .flatMapPublisher(res -> {  // 1. With flatMapPublisher we will create a Flowable from the item emitted by the Single<Result>.
        List<JsonArray> results = res.getResults();
        return Flowable.fromIterable(results); // 2. fromIterable converts the database results Iterable into a Flowable emitting the database row items.
      })
      .map(json -> json.getString(0)) // 3. Since we only need the page name we can map each JsonObject row to the first column
      .sorted() // 4. The client expects the data to be sorted in alphabetical order.
      .collect(JsonArray::new, JsonArray::add) // 5. The event bus service reply consists in a single JsonArray. collect creates a new one with JsonArray::new and later adds items as they are emitted with JsonArray::add.
      .subscribe(SingleHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
    dbClient.rxQueryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(name))
      .map(result -> {
        if (result.getNumRows() > 0) {
          JsonArray row = result.getResults().get(0);
          return new JsonObject()
            .put("found", true)
            .put("id", row.getInteger(0))
            .put("rawContent", row.getString(1));
        } else {
          return new JsonObject().put("found", false);
        }
      })
      .subscribe(SingleHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService fetchPageById(int id, Handler<AsyncResult<JsonObject>> resultHandler) {
    String query = sqlQueries.get(SqlQuery.GET_PAGE_BY_ID);
    JsonArray params = new JsonArray().add(id);
    Single<ResultSet> resultSet = dbClient.rxQueryWithParams(query, params);
    resultSet
      .map(result -> {
        if (result.getNumRows() > 0) {
          JsonObject row = result.getRows().get(0);
          return new JsonObject()
            .put("found", true)
            .put("id", row.getInteger("ID"))
            .put("name", row.getString("NAME"))
            .put("content", row.getString("CONTENT"));
        } else {
          return new JsonObject().put("found", false);
        }
      })
      .subscribe(SingleHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), new JsonArray().add(title).add(markdown))
      .ignoreElement()
      .subscribe(CompletableHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), new JsonArray().add(markdown).add(id))
      .ignoreElement()
      .subscribe(CompletableHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
    JsonArray data = new JsonArray().add(id);
    dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data)
      .ignoreElement()
      .subscribe(CompletableHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>> resultHandler) { // 1. fetchAllPagesData is an asynchronous service proxy operation, defined with a Handler<AsyncResult<List<JsonObject>>> callback.
    dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES_DATA))
      .map(ResultSet::getRows)
      .subscribe(SingleHelper.toObserver(resultHandler));  // 2. The toObserver method adapts the resultHandler to a SingleObserver<List<JsonObject>>, so that the handler is invoked when the list of rows is emitted.
    return this;
  }
}