/*
========================================================================
SchemaCrawler
http://www.schemacrawler.com
Copyright (c) 2000-2018, Sualeh Fatehi <sualeh@hotmail.com>.
All rights reserved.
------------------------------------------------------------------------

SchemaCrawler is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

SchemaCrawler and the accompanying materials are made available under
the terms of the Eclipse Public License v1.0, GNU General Public License
v3 or GNU Lesser General Public License v3.

You may elect to redistribute this code under any of these licenses.

The Eclipse Public License is available at:
http://www.eclipse.org/legal/epl-v10.html

The GNU General Public License v3 and the GNU Lesser General Public
License v3 are available at:
http://www.gnu.org/licenses/

========================================================================
*/
package schemacrawler.integration.test;


import static java.nio.file.Files.createTempFile;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;
import static schemacrawler.test.utility.FileHasContent.classpathResource;
import static schemacrawler.test.utility.FileHasContent.fileResource;
import static schemacrawler.test.utility.FileHasContent.hasSameContentAs;
import static schemacrawler.test.utility.TestUtility.flattenCommandlineArgs;
import static sf.util.Utility.isBlank;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import schemacrawler.Main;
import schemacrawler.schemacrawler.RegularExpressionInclusionRule;
import schemacrawler.schemacrawler.SchemaCrawlerException;
import schemacrawler.schemacrawler.SchemaCrawlerOptions;
import schemacrawler.schemacrawler.SchemaCrawlerOptionsBuilder;
import schemacrawler.schemacrawler.SchemaInfoLevelBuilder;
import schemacrawler.server.postgresql.EmbeddedPostgreSQLWrapper;
import schemacrawler.test.utility.TestWriter;
import schemacrawler.tools.executable.SchemaCrawlerExecutable;
import schemacrawler.tools.options.InfoLevel;
import schemacrawler.tools.text.schema.SchemaTextOptions;
import schemacrawler.tools.text.schema.SchemaTextOptionsBuilder;

public class PostgreSQLDumpTest
  extends BasePostgreSQLTest
{

  @BeforeClass
  public static void checkRun()
  {
    final String property = System.getProperty("complete");
    final boolean runIf = property != null && isBlank(property)
                          || Boolean.parseBoolean(property);
    assumeTrue(runIf);
  }

  private Path dumpFile;

  @Before
  public void createDatabaseDump()
    throws SchemaCrawlerException, SQLException, IOException
  {
    dumpFile = createTempFile("test_postgres_dump", "sql");

    final EmbeddedPostgreSQLWrapper embeddedPostgreSQL = new EmbeddedPostgreSQLWrapper();
    embeddedPostgreSQL.startServer();
    createDataSource(embeddedPostgreSQL.getConnectionUrl(),
                     embeddedPostgreSQL.getUser(),
                     embeddedPostgreSQL.getPassword());
    createDatabase("/postgresql.scripts.txt");

    embeddedPostgreSQL.exportToFile(dumpFile);

    embeddedPostgreSQL.stopServer();
  }

  @Test
  public void testPostgreSQLExecutableWithDump()
    throws Exception
  {
    final SchemaCrawlerOptionsBuilder schemaCrawlerOptionsBuilder = SchemaCrawlerOptionsBuilder
      .builder();
    schemaCrawlerOptionsBuilder
      .withSchemaInfoLevel(SchemaInfoLevelBuilder.maximum())
      .includeSchemas(new RegularExpressionInclusionRule("books"))
      .includeAllSequences().includeAllSynonyms().includeAllRoutines();
    final SchemaCrawlerOptions options = schemaCrawlerOptionsBuilder
      .toOptions();

    final SchemaTextOptionsBuilder textOptionsBuilder = SchemaTextOptionsBuilder
      .builder();
    textOptionsBuilder.portableNames();
    final SchemaTextOptions textOptions = textOptionsBuilder.toOptions();

    final EmbeddedPostgreSQLWrapper postgreSQLDumpLoader = new EmbeddedPostgreSQLWrapper();
    postgreSQLDumpLoader.startServer();
    postgreSQLDumpLoader.loadDatabaseFile(dumpFile);

    final SchemaCrawlerExecutable executable = new SchemaCrawlerExecutable("details");
    executable.setSchemaCrawlerOptions(options);
    executable.setAdditionalConfiguration(SchemaTextOptionsBuilder
      .builder(textOptions).toConfig());
    executable.setConnection(postgreSQLDumpLoader.createDatabaseConnection());

    executeExecutable(executable, "testPostgreSQLExecutableWithDump.txt");
    LOGGER.log(Level.INFO, "Completed PostgreSQL test successfully");
  }

  @Test
  public void testPostgreSQLMainWithDump()
    throws Exception
  {
    final TestWriter testout = new TestWriter();
    try (final TestWriter out = testout;)
    {
      final Map<String, String> argsMap = new HashMap<>();
      argsMap.put("server", "postgresql");
      argsMap.put("database", dumpFile.toString());
      argsMap.put("schemas", "books");
      argsMap.put("routines", ".*");
      argsMap.put("command", "details");
      argsMap.put("infolevel", InfoLevel.maximum.name());
      argsMap.put("outputfile", out.toString());
      // argsMap.put("loglevel", Level.ALL.toString());

      Main.main(flattenCommandlineArgs(argsMap));
    }
    assertThat(fileResource(testout),
               hasSameContentAs(classpathResource("testPostgreSQLMainWithDump.txt")));
  }

}
