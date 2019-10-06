package io.vertx.guide.wiki;

// Interface compartilhada entre arquivos de database e no servidor http
public interface DatabaseConstants {

  String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
  String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class";
  String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";

  String DEFAULT_WIKIDB_JDBC_URL = "jdbc:hsqldb:file:db/wiki";
  String DEFAULT_WIKIDB_JDBC_DRIVER_CLASS = "org.hsqldb.jdbcDriver";
  int DEFAULT_JDBC_MAX_POOL_SIZE = 30;
}