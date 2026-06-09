import sbt.Setting
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "Reverse.*",
    "uk.gov.hmrc.BuildInfo",
    "uk.gov.hmrc.disareturnsbackend.AppInitialiser",
    "uk.gov.hmrc.disareturnsbackend.Module",
    "uk.gov.hmrc.disareturnsbackend.config.AppConfig",
    "uk.gov.hmrc.disareturnsbackend.models.CreateFileUploadRequest",
    "uk.gov.hmrc.disareturnsbackend.models.CreateMonthlyReturnRequest",
    "uk.gov.hmrc.disareturnsbackend.models.CreateMonthlyReturnResponse",
    "uk.gov.hmrc.disareturnsbackend.models.FileUploadWorkItem",
    "uk.gov.hmrc.disareturnsbackend.models.UpdateNilReturnRequest",
    "app.*",
    "prod.*",
    ".*Routes.*",
    "uk.gov.hmrc.disareturnsbackend.testOnly.*",
    "testOnly.*",
    "testOnlyDoNotUseInAppConf.*"
  )

  val settings: Seq[Setting[?]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true
  )
}
