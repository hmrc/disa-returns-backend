import sbt.*

object AppDependencies {

  private val bootstrapVersion         = "10.7.0"
  private val hmrcMongoVersion         = "2.12.0"
  private val objectStoreClientVersion = "2.6.0"
  private val poiVersion               = "5.5.1"
  private val univocityVersion         = "2.9.1"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-work-item-repo-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-30"       % objectStoreClientVersion,
    "com.univocity"            % "univocity-parsers"                 % univocityVersion,
    "org.apache.poi"           % "poi-ooxml"                         % poiVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test
  )

  val it: Seq[Nothing] = Seq.empty
}
