package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.PreparedStatement


class V003__UpdateUsers extends BaseJavaMigration{


  @throws[Exception]
  override def migrate(context: Context): Unit = {
    try {
      val statement = context.getConnection.prepareStatement("INSERT INTO test_user (name) VALUES ('Obelix')")
      try statement.execute
      finally if (statement != null) statement.close()
    }
  }

}
