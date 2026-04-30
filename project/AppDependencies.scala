import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  private val bootstrapVersion = "10.5.0"
  private val hmrcMongoVersion = "2.11.0"
  private val univocityVersion = "2.9.1"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-work-item-repo-play-30" % hmrcMongoVersion,
    "com.univocity" % "univocity-parsers" % univocityVersion
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test
  )

  val it = Seq.empty
}
