# Introduction
This is a simple demonstration of vertx, serving random 12 byte initialisation vectors and (eventually) storing them to a database to avoid serving duplicates.
# Running
Run SBT in root project, and then switch to `project vertx`. Then run `zipmod` to build the verticle as both a module in `target/mods/org.rebeam~vertx_2.10~0.1` and as a zipped module in `target`.

At this point, the verticle can be run from `targets` directory using either `vertx runzip vertx_2.10-0.1.zip` or `vertx runmod org.rebeam~vertx_2.10~0.1`.

Note that "auto-redeploy" is set, so rebuilding the mod using `zipmod` will cause vertx to redeploy the verticle with changes. Note this takes a few seconds to actually complete.

For fully automatic building, also run `~zipmod` in SBT to rebuild the module on source changes.
