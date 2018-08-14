package com.k_int.hibernate.dialects.postgres;

import org.hibernate.dialect.PostgreSQL94Dialect;

public class KIPostgres94Dialect extends PostgreSQL94Dialect {

  public KIPostgres94Dialect() {
    super();
    registerFunction("trgm_match", new PostgreSQLTrigramFunction());
  }
}
