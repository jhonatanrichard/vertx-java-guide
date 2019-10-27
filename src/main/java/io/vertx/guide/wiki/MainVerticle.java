package io.vertx.guide.wiki;

import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
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

    Single<String> dbVerticleDeployment = vertx.rxDeployVerticle("io.vertx.guide.wiki.database.WikiDatabaseVerticle");

    DeploymentOptions opts = new DeploymentOptions().setInstances(2);
    dbVerticleDeployment
      .flatMap(id -> vertx.rxDeployVerticle("io.vertx.guide.wiki.http.HttpServerVerticle", opts))
      .subscribe(id -> promise.complete(), promise::fail);
  }
}