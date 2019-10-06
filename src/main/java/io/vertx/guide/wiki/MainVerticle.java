package io.vertx.guide.wiki;

import io.vertx.core.*;
import io.vertx.guide.wiki.database.WikiDatabaseVerticle;
import io.vertx.guide.wiki.http.AuthInitializerVerticle;

/* AbstractVerticle prove:
- métodos de ciclo de vida start and stop para serem sobrescritos,
- um campo protected chamado vertx que referencia o ambiente Vert.x em que o verticle está sendo implantado
/ um accessor para um objeto de algumas configuracoes que permite passar configuracao externa a um verticle
*/
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> promise) throws Exception {

    // 1. Deploying a verticle is an asynchronous operation, so we need a Future for that. 
    // The String parametric type is because a verticle gets an identifier when successfully deployed.
    Promise<String> dbDeploymentPromise = Promise.promise();
    // 2. One option is to create a verticle instance with new, and pass the object reference to the deploy method. 
    // The completer return value is a handler that simply completes its future.
    vertx.deployVerticle(new WikiDatabaseVerticle(), dbDeploymentPromise);

    Future<String> authDeploymentFuture = dbDeploymentPromise.future().compose(id -> {
      Promise<String> deployPromise = Promise.promise();
      vertx.deployVerticle(new AuthInitializerVerticle(), deployPromise);
      return deployPromise.future();
    });

    authDeploymentFuture.compose(id -> {
      Promise<String> deployPromise = Promise.promise();
      vertx.deployVerticle("io.vertx.guides.wiki.http.HttpServerVerticle", new DeploymentOptions().setInstances(2), deployPromise);
      return deployPromise.future();
    });

    authDeploymentFuture.setHandler(ar -> {
      if (ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
  }
}