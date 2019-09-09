package io.vertx.guide.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

/* AbstractVerticle provê:
- life-cycle start and stop methods to override,
- a protected field called vertx that references the Vert.x environment where the verticle is being deployed,
- an accessor to some configuration object that allows passing external configuration to a verticle.
*/ 
public class MainVerticle extends AbstractVerticle {

  /* There are 2 forms of start (and stop) methods: 1 with no argument and 
  1 with a promise object reference. The no-argument variants imply that the 
  verticle initialization or house-keeping phases always succeed unless an exception 
  is being thrown. The variants with a promise object provide a more fine-grained approach 
  to eventually signal that operations succeeded or not. Indeed, some initialization or cleanup code may 
  require asynchronous operations, so reporting via a promise object naturally fits with asynchronous idioms. */
  @Override
  public void start(Promise<Void> promise) {
    promise.complete();
  }

}