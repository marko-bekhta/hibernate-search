/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.util.impl.integrationtest.mapper.orm;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.utility.DockerImageName;

/*
 * Suppress "Resource leak: '<unassigned Closeable value>' is never closed". Testcontainers take care of closing
 * these resources in the end.
 */
@SuppressWarnings("resource")
public final class DatabaseContainer {

	private static final Duration REGULAR_TIMEOUT = Duration.ofMinutes( 5 );
	private static final Duration EXTENDED_TIMEOUT = Duration.ofMinutes( 10 );

	private DatabaseContainer() {
	}

	private static final SupportedDatabase DATABASE;
	private static final JdbcDatabaseContainer<?> DATABASE_CONTAINER;


	static {
		String name = System.getProperty( "org.hibernate.search.integrationtest.orm.database.image.name", "" );
		String tag = System.getProperty( "org.hibernate.search.integrationtest.orm.database.image.tag" );
		DATABASE = SupportedDatabase.from( name );

		DATABASE_CONTAINER = DATABASE.container( name, tag );
	}


	public static Configuration configuration() {
		if ( !SupportedDatabase.H2.equals( DATABASE ) ) {
			DATABASE_CONTAINER.start();
		}
		Configuration configuration = DATABASE.configuration( DATABASE_CONTAINER );
		configuration.addAsSystemProperties();
		return configuration;
	}

	private enum SupportedDatabase {
		H2 {
			@Override
			String dialect() {
				return org.hibernate.dialect.H2Dialect.class.getName();
			}

			@Override
			Configuration configuration(JdbcDatabaseContainer<?> container) {
				return new Configuration(
						dialect(),
						"org.h2.Driver",
						"jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1",
						"sa",
						"sa",
						""
				);
			}

			@Override
			<SELF extends JdbcDatabaseContainer<SELF>> JdbcDatabaseContainer<SELF> container(String name, String tag) {
				return null;
			}
		},
		POSTGRES {
			@Override
			String dialect() {
				return org.hibernate.dialect.PostgreSQLDialect.class.getName();
			}

			@Override
			<SELF extends JdbcDatabaseContainer<SELF>> JdbcDatabaseContainer<SELF> container(String name, String tag) {
				return new JdbcDatabaseContainer<SELF>( DockerImageName.parse( name ).withTag( tag ) ) {
					@Override
					public String getDriverClassName() {
						return "org.postgresql.Driver";
					}

					@Override
					public String getJdbcUrl() {
						return "jdbc:postgresql://" + getHost() + ":" + getMappedPort( 5432 ) + "/hibernate_orm_test";
					}

					@Override
					public String getUsername() {
						return "hibernate_orm_test";
					}

					@Override
					public String getPassword() {
						return "hibernate_orm_test";
					}

					@Override
					protected String getTestQueryString() {
						return "select 1";
					}
				}
						.withExposedPorts( 5432 )
						.withEnv( "POSTGRES_USER", "hibernate_orm_test" )
						.withEnv( "POSTGRES_PASSWORD", "hibernate_orm_test" )
						.withEnv( "POSTGRES_DB", "hibernate_orm_test" )
						.withStartupTimeout( REGULAR_TIMEOUT )
						.withReuse( true );
			}
		},
		MARIADB {
			@Override
			String dialect() {
				return org.hibernate.dialect.MariaDBDialect.class.getName();
			}

			@Override
			<SELF extends JdbcDatabaseContainer<SELF>> JdbcDatabaseContainer<SELF> container(String name, String tag) {
				return new JdbcDatabaseContainer<SELF>( DockerImageName.parse( name ).withTag( tag ) ) {
					@Override
					public String getDriverClassName() {
						return "org.mariadb.jdbc.Driver";
					}

					@Override
					public String getJdbcUrl() {
						return "jdbc:mariadb://" + getHost() + ":" + getMappedPort( 3306 ) + "/hibernate_orm_test";
					}

					@Override
					public String getUsername() {
						return "hibernate_orm_test";
					}

					@Override
					public String getPassword() {
						return "hibernate_orm_test";
					}

					@Override
					protected String getTestQueryString() {
						return "select 1";
					}
				}
						.withExposedPorts( 3306 )
						.withCommand( "--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci" )
						.withEnv( "MYSQL_USER", "hibernate_orm_test" )
						.withEnv( "MYSQL_PASSWORD", "hibernate_orm_test" )
						.withEnv( "MYSQL_DATABASE", "hibernate_orm_test" )
						.withEnv( "MYSQL_RANDOM_ROOT_PASSWORD", "true" )
						.withTmpFs( Collections.singletonMap( "/var/lib/mysql", "" ) )
						.withStartupTimeout( REGULAR_TIMEOUT )
						.withReuse( true );
			}
		},
		MYSQL {
			@Override
			String dialect() {
				return org.hibernate.dialect.MySQLDialect.class.getName();
			}

			@Override
			<SELF extends JdbcDatabaseContainer<SELF>> JdbcDatabaseContainer<SELF> container(String name, String tag) {
				return new JdbcDatabaseContainer<SELF>( DockerImageName.parse( name ).withTag( tag ) ) {
					@Override
					public String getDriverClassName() {
						return "com.mysql.jdbc.Driver";
					}

					@Override
					public String getJdbcUrl() {
						return "jdbc:mysql://" + getHost() + ":" + getMappedPort( 3306 ) + "/hibernate_orm_test";
					}

					@Override
					public String getUsername() {
						return "hibernate_orm_test";
					}

					@Override
					public String getPassword() {
						return "hibernate_orm_test";
					}

					@Override
					protected String getTestQueryString() {
						return "select 1";
					}
				}
						.withExposedPorts( 3306 )
						.withCommand( "--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci" )
						.withEnv( "MYSQL_USER", "hibernate_orm_test" )
						.withEnv( "MYSQL_PASSWORD", "hibernate_orm_test" )
						.withEnv( "MYSQL_DATABASE", "hibernate_orm_test" )
						.withEnv( "MYSQL_RANDOM_ROOT_PASSWORD", "true" )
						.withTmpFs( Collections.singletonMap( "/var/lib/mysql", "" ) )
						.withStartupTimeout( REGULAR_TIMEOUT )
						.withReuse( true );
			}
		},
		DB2 {
			@Override
			String dialect() {
				return org.hibernate.dialect.DB2Dialect.class.getName();
			}

			@Override
			<SELF extends JdbcDatabaseContainer<SELF>> JdbcDatabaseContainer<SELF> container(String name, String tag) {
				return new JdbcDatabaseContainer<SELF>( DockerImageName.parse( name ).withTag( tag ) ) {
					@Override
					public String getDriverClassName() {
						return "com.ibm.db2.jcc.DB2Driver";
					}

					@Override
					public String getJdbcUrl() {
						return "jdbc:db2://" + getHost() + ":" + getMappedPort( 50000 ) + "/hreact";
					}

					@Override
					public String getUsername() {
						return "hreact";
					}

					@Override
					public String getPassword() {
						return "hreact";
					}

					@Override
					protected String getTestQueryString() {
						return "SELECT 1 FROM SYSIBM.SYSDUMMY1";
					}
				}
						.withExposedPorts( 50000 )
						.withNetworkMode( "bridge" )
						.withEnv( "DB2INSTANCE", "hreact" )
						.withEnv( "DB2INST1_PASSWORD", "hreact" )
						.withEnv( "DBNAME", "hreact" )
						.withEnv( "LICENSE", "accept" )
						// These help the DB2 container start faster
						.withEnv( "AUTOCONFIG", "false" )
						.withEnv( "ARCHIVE_LOGS", "false" )
						.withEnv( "PERSISTENT_HOME", "false" )
						// because it takes ages to start and select query will just get tired to retry
						// this is a JDBCContainer specific setting
						.withStartupTimeoutSeconds( 600 )
						.withStartupTimeout( EXTENDED_TIMEOUT )
						.withReuse( true );
			}
		},
		ORACLE {
			@Override
			String dialect() {
				return org.hibernate.dialect.OracleDialect.class.getName();
			}

			@Override
			<SELF extends JdbcDatabaseContainer<SELF>> JdbcDatabaseContainer<SELF> container(String name, String tag) {
				return new JdbcDatabaseContainer<SELF>( DockerImageName.parse( name ).withTag( tag ) ) {
					@Override
					public String getDriverClassName() {
						return "oracle.jdbc.OracleDriver";
					}

					@Override
					public String getJdbcUrl() {
						return "jdbc:oracle:thin:@" + getHost() + ":" + getMappedPort( 1521 ) + "/XE";
					}

					@Override
					public String getUsername() {
						return "SYSTEM";
					}

					@Override
					public String getPassword() {
						return "hibernate_orm_test";
					}

					@Override
					protected String getTestQueryString() {
						return "select 1 from dual";
					}
				}
						.withExposedPorts( 1521 )
						.withEnv( "ORACLE_PASSWORD", "hibernate_orm_test" )
						.withStartupTimeout( EXTENDED_TIMEOUT )
						.withReuse( true );
			}
		},
		MSSQL {
			@Override
			String dialect() {
				return org.hibernate.dialect.SQLServerDialect.class.getName();
			}

			@Override
			<SELF extends JdbcDatabaseContainer<SELF>> JdbcDatabaseContainer<SELF> container(String name, String tag) {
				return new JdbcDatabaseContainer<SELF>( DockerImageName.parse( name ).withTag( tag ) ) {
					@Override
					public String getDriverClassName() {
						return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
					}

					@Override
					public String getJdbcUrl() {
						return "jdbc:sqlserver://" + getHost() + ":" + getMappedPort( 1433 ) + ";databaseName=tempdb";
					}

					@Override
					public String getUsername() {
						return "SA";
					}

					@Override
					public String getPassword() {
						return "ActuallyRequired11Complexity";
					}

					@Override
					protected String getTestQueryString() {
						return "select 1";
					}
				}
						.withExposedPorts( 1433 )
						.withEnv( "ACCEPT_EULA", "Y" )
						.withEnv( "SA_PASSWORD", "ActuallyRequired11Complexity" )
						.withStartupTimeout( EXTENDED_TIMEOUT )
						.withReuse( true );
			}
		},
		COCKROACHDB {
			@Override
			String dialect() {
				return org.hibernate.dialect.CockroachDialect.class.getName();
			}

			@Override
			<SELF extends JdbcDatabaseContainer<SELF>> JdbcDatabaseContainer<SELF> container(String name, String tag) {
				return new JdbcDatabaseContainer<SELF>( DockerImageName.parse( name ).withTag( tag ) ) {
					@Override
					public String getDriverClassName() {
						return "org.postgresql.Driver";
					}

					@Override
					public String getJdbcUrl() {
						return "jdbc:postgresql://" + getHost() + ":" + getMappedPort( 26257 ) + "/defaultdb?sslmode=disable";
					}

					@Override
					public String getUsername() {
						return "root";
					}

					@Override
					public String getPassword() {
						return "";
					}

					@Override
					protected String getTestQueryString() {
						return "select 1";
					}
				}
						.withExposedPorts( 26257 )
						.withCommand( "start-single-node --insecure" )
						.withStartupTimeout( REGULAR_TIMEOUT )
						.withReuse( true );
			}
		};

		Configuration configuration(JdbcDatabaseContainer<?> container) {
			return new Configuration(
					dialect(),
					container.getDriverClassName(),
					container.getJdbcUrl(),
					container.getUsername(),
					container.getPassword(),
					""
			);
		}

		abstract String dialect();

		abstract <SELF extends JdbcDatabaseContainer<SELF>> JdbcDatabaseContainer<SELF> container(String name, String tag);

		static SupportedDatabase from(String name) {
			for ( SupportedDatabase database : values() ) {
				if ( name.toLowerCase( Locale.ROOT ).contains( database.name().toLowerCase( Locale.ROOT ) ) ) {
					return database;
				}
			}
			throw new IllegalStateException( "Unsupported database requested: " + name );
		}
	}

	public static class Configuration {
		private final String dialect;
		private final String driver;
		private final String url;
		private final String user;
		private final String pass;
		private final String isolation;

		public Configuration(String dialect, String driver, String url, String user, String pass, String isolation) {
			this.dialect = dialect;
			this.driver = driver;
			this.url = url;
			this.user = user;
			this.pass = pass;
			this.isolation = isolation;
		}

		private void addAsSystemProperties() {
			System.setProperty( "hibernate.dialect", this.dialect );
			System.setProperty( "hibernate.connection.driver_class", this.driver );
			System.setProperty( "hibernate.connection.url", this.url );
			System.setProperty( "hibernate.connection.username", this.user );
			System.setProperty( "hibernate.connection.password", this.pass );
			System.setProperty( "hibernate.connection.isolation", this.isolation );
		}
	}

}