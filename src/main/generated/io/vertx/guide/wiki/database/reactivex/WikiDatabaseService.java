/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.guide.wiki.database.reactivex;

import java.util.Map;
import io.reactivex.Observable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonArray;
import java.util.List;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;


@io.vertx.lang.rx.RxGen(io.vertx.guide.wiki.database.WikiDatabaseService.class)
public class WikiDatabaseService {

  @Override
  public String toString() {
    return delegate.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    WikiDatabaseService that = (WikiDatabaseService) o;
    return delegate.equals(that.delegate);
  }
  
  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  public static final io.vertx.lang.rx.TypeArg<WikiDatabaseService> __TYPE_ARG = new io.vertx.lang.rx.TypeArg<>(    obj -> new WikiDatabaseService((io.vertx.guide.wiki.database.WikiDatabaseService) obj),
    WikiDatabaseService::getDelegate
  );

  private final io.vertx.guide.wiki.database.WikiDatabaseService delegate;
  
  public WikiDatabaseService(io.vertx.guide.wiki.database.WikiDatabaseService delegate) {
    this.delegate = delegate;
  }

  public io.vertx.guide.wiki.database.WikiDatabaseService getDelegate() {
    return delegate;
  }

  public io.vertx.guide.wiki.database.reactivex.WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) { 
    delegate.fetchAllPages(resultHandler);
    return this;
  }

  public Single<JsonArray> rxFetchAllPages() { 
    return io.vertx.reactivex.impl.AsyncResultSingle.toSingle(handler -> {
      fetchAllPages(handler);
    });
  }

  public io.vertx.guide.wiki.database.reactivex.WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.fetchPage(name, resultHandler);
    return this;
  }

  public Single<JsonObject> rxFetchPage(String name) { 
    return io.vertx.reactivex.impl.AsyncResultSingle.toSingle(handler -> {
      fetchPage(name, handler);
    });
  }

  public io.vertx.guide.wiki.database.reactivex.WikiDatabaseService fetchPageById(int id, Handler<AsyncResult<JsonObject>> resultHandler) { 
    delegate.fetchPageById(id, resultHandler);
    return this;
  }

  public Single<JsonObject> rxFetchPageById(int id) { 
    return io.vertx.reactivex.impl.AsyncResultSingle.toSingle(handler -> {
      fetchPageById(id, handler);
    });
  }

  public io.vertx.guide.wiki.database.reactivex.WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) { 
    delegate.createPage(title, markdown, resultHandler);
    return this;
  }

  public Completable rxCreatePage(String title, String markdown) { 
    return io.vertx.reactivex.impl.AsyncResultCompletable.toCompletable(handler -> {
      createPage(title, markdown, handler);
    });
  }

  public io.vertx.guide.wiki.database.reactivex.WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) { 
    delegate.savePage(id, markdown, resultHandler);
    return this;
  }

  public Completable rxSavePage(int id, String markdown) { 
    return io.vertx.reactivex.impl.AsyncResultCompletable.toCompletable(handler -> {
      savePage(id, markdown, handler);
    });
  }

  public io.vertx.guide.wiki.database.reactivex.WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) { 
    delegate.deletePage(id, resultHandler);
    return this;
  }

  public Completable rxDeletePage(int id) { 
    return io.vertx.reactivex.impl.AsyncResultCompletable.toCompletable(handler -> {
      deletePage(id, handler);
    });
  }

  public io.vertx.guide.wiki.database.reactivex.WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>> resultHandler) { 
    delegate.fetchAllPagesData(resultHandler);
    return this;
  }

  public Single<List<JsonObject>> rxFetchAllPagesData() { 
    return io.vertx.reactivex.impl.AsyncResultSingle.toSingle(handler -> {
      fetchAllPagesData(handler);
    });
  }


  public static  WikiDatabaseService newInstance(io.vertx.guide.wiki.database.WikiDatabaseService arg) {
    return arg != null ? new WikiDatabaseService(arg) : null;
  }
}
