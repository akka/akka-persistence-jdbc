import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.headerSettings
import sbt.*
import sbt.Keys.*

object IntegrationTests {

  def settings: Seq[Def.Setting[_]] =
    Seq(
      publish / skip := true,
      doc / sources := Seq.empty,
      Test / fork := true
    )

}
