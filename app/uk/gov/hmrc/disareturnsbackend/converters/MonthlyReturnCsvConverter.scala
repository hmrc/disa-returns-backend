/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.disareturnsbackend.converters

import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}
import uk.gov.hmrc.disareturnsbackend.validators.fileupload.monthly.MonthlyFileUploadTemplate

import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.inject.*
import scala.util.Using

trait MonthlyReturnCsvConverter {
  def convert(input: Path, output: Path): Unit
}

@Singleton
class MonthlyReturnCsvConverterImpl @Inject() (ndjsonWriter: MonthlyReturnNdjsonWriter)
    extends MonthlyReturnCsvConverter {

  private val settings = {
    val settings = new CsvParserSettings()
    settings.setLineSeparatorDetectionEnabled(true)
    settings
  }

  override def convert(input: Path, output: Path): Unit =
    ndjsonWriter.withOutput(output) { writer =>
      val parser = new CsvParser(settings)
      Using.resource(new FileInputStream(input.toFile)) { stream =>
        parser.beginParsing(new InputStreamReader(stream, StandardCharsets.UTF_8))
        try
          Iterator
            .continually(parser.parseNext())
            .takeWhile(_ != null)
            .drop(MonthlyFileUploadTemplate.csvDataStartRowIndexZeroBased)
            .foreach(row => ndjsonWriter.writeRow(row.toVector, writer))
        finally
          parser.stopParsing()
      }
    }
}
