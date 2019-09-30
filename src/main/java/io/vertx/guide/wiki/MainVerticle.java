package io.vertx.guide.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.guide.wiki.database.WikiDatabaseVerticle;

/* AbstractVerticle prove:
- life-cycle start and stop methods to override,
- a protected field called vertx that references the Vert.x environment where the verticle is being deployed,
- an accessor to some configuration object that allows passing external configuration to a verticle.
*/
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> promise) throws Exception {

    // 1. Deploying a verticle is an asynchronous operation, so we need a Future for that. 
    // The String parametric type is because a verticle gets an identifier when successfully deployed.
    Promise<String> dbVerticleDeployment = Promise.promise();
    // 2. One option is to create a verticle instance with new, and pass the object reference to the deploy method. 
    // The completer return value is a handler that simply completes its future.
    vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment);

    // 3. Sequential composition with compose allows to run one asynchronous operation after the other. 
    // When the initial future completes successfully, the composition function is invoked.
    dbVerticleDeployment.future().compose(id -> {  

      Promise<String> httpVerticleDeployment = Promise.promise();
      vertx.deployVerticle(
        "io.vertx.guide.wiki.http.HttpServerVerticle",  // 4. A class name as a string is also an option to specify a verticle to deploy. For other JVM languages string-based conventions allow a module / script to be specified.
        new DeploymentOptions().setInstances(2),    // 5. The DeploymentOption class allows to specify a number of parameters and especially the number of instances to deploy.
        httpVerticleDeployment);

      return httpVerticleDeployment.future();  // 6. The composition function returns the next future. Its completion will trigger the completion of the composite operation.

    }).setHandler(ar -> {   // 7. We define a handler that eventually completes the MainVerticle start future.
      if (ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
  }
}