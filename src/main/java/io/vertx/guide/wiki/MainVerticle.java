package io.vertx.guide.wiki;

import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.reactivex.core.AbstractVerticle;

/* AbstractVerticle prove:
- métodos de ciclo de vida start and stop para serem sobrescritos,
- um campo protected chamado vertx que referencia o ambiente Vert.x em que o verticle está sendo implantado
/ um accessor para um objeto de algumas configuracoes que permite passar configuracao externa a um verticle
*/
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> promise) throws Exception {

    Single<String> dbVerticleDeployment = vertx.rxDeployVerticle(
      "io.vertx.guide.wiki.database.WikiDatabaseVerticle");

    dbVerticleDeployment
      .flatMap(id -> { // 1. The flatMap operator applies the function to the result of dbVerticleDeployment. Here it schedules the deployment of the HttpServerVerticle.

        Single<String> httpVerticleDeployment = vertx.rxDeployVerticle(
          "io.vertx.guide.wiki.http.HttpServerVerticle",
          new DeploymentOptions().setInstances(2));

        return httpVerticleDeployment;
      })
      .flatMap(id -> vertx.rxDeployVerticle("io.vertx.guide.wiki.http.AuthInitializerVerticle")) // 2. We use the shorter lambda form here.
      .subscribe(id -> promise.complete(), promise::fail); // 3. Operations start when subscribing. On success or on error, the MainVerticle start future is either completed or failed.
  }
}