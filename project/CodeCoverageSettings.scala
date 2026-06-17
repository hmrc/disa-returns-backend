import sbt.Setting
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    ".*\\$anon.*",
    "Reverse.*",
    "app.*",
    "prod.*",
    ".*Routes.*",
    "testOnly.*",
    "testOnlyDoNotUseInAppConf.*",
    "uk.gov.hmrc.BuildInfo",
    "uk.gov.hmrc.disareturnsbackend.AppInitialiser",
    "uk.gov.hmrc.disareturnsbackend.config.AppConfig",
    "uk.gov.hmrc.disareturnsbackend.models.CreateFileUploadRequest",
    "uk.gov.hmrc.disareturnsbackend.models.CreateMonthlyReturnRequest",
    "uk.gov.hmrc.disareturnsbackend.models.CreateMonthlyReturnResponse",
    "uk.gov.hmrc.disareturnsbackend.models.FileUpload",
    "uk.gov.hmrc.disareturnsbackend.models.FileUploadValidationResult",
    "uk.gov.hmrc.disareturnsbackend.models.MonthlyReturnFileUploadWorkItem",
    "uk.gov.hmrc.disareturnsbackend.models.UpdateNilReturnRequest",
    "uk.gov.hmrc.disareturnsbackend.models.UpscanDetails",
    "uk.gov.hmrc.disareturnsbackend.models.UpscanFailure",
    "uk.gov.hmrc.disareturnsbackend.models.UpscanFailureDetails",
    "uk.gov.hmrc.disareturnsbackend.models.UpscanSuccess",
    "uk.gov.hmrc.disareturnsbackend.Module",
    "uk.gov.hmrc.disareturnsbackend.testOnly.*"
  )

  val settings: Seq[Setting[?]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
