package org.pokemmo.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Shared PostgreSQL access backed by a connection pool. */
public final class Database implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(Database.class);

  private final HikariDataSource dataSource;
  private final DSLContext context;

  public Database(
      String url,
      String user,
      String password
  ) {
    HikariConfig config = new HikariConfig();
    config.setPoolName("openmmo-postgres");
    config.setJdbcUrl(Objects.requireNonNull(url, "url"));
    config.setUsername(Objects.requireNonNull(user, "user"));
    config.setPassword(Objects.requireNonNull(password, "password"));
    config.setMaximumPoolSize(8);
    config.setMinimumIdle(1);
    config.setConnectionTimeout(10_000);
    config.setValidationTimeout(5_000);
    config.setInitializationFailTimeout(-1);
    config.setIdleTimeout(600_000);
    config.setMaxLifetime(1_500_000);
    config.setKeepaliveTime(120_000);
    config.setConnectionTestQuery("SELECT 1");
    config.addDataSourceProperty("tcpKeepAlive", true);
    config.addDataSourceProperty("connectTimeout", 10);
    this.dataSource = new HikariDataSource(config);
    this.context = DSL.using(new DefaultConfiguration()
        .set(new DataSourceConnectionProvider(dataSource))
        .set(SQLDialect.POSTGRES));
    log.info("PostgreSQL 连接池已初始化: poolName={}, poolSize={}",
        config.getPoolName(), config.getMaximumPoolSize());
  }

  public DSLContext ctx() {
    return context;
  }

  @Override
  public void close() {
    dataSource.close();
  }
}
