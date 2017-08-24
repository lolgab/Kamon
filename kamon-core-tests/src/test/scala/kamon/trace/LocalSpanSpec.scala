/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.trace

import kamon.testkit.{MetricInspection, Reconfigure, TestSpanReporter}
import kamon.util.Registration
import kamon.Kamon
import kamon.trace.Span.{Annotation, TagValue}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}
import org.scalatest.time.SpanSugar._

class LocalSpanSpec extends WordSpec with Matchers with BeforeAndAfterAll with Eventually with OptionValues
    with Reconfigure with MetricInspection {

  "a real span" when {
    "sampled and finished" should {
      "be sent to the Span reporters" in {
        Kamon.buildSpan("test-span")
          .withSpanTag("test", "value")
          .withStartTimestamp(100)
          .start()
          .finish(200)

        eventually(timeout(2 seconds)) {
          val finishedSpan = reporter.nextSpan().value
          finishedSpan.operationName shouldBe("test-span")
          finishedSpan.startTimestampMicros shouldBe 100
          finishedSpan.endTimestampMicros shouldBe 200
          finishedSpan.tags should contain("test" -> TagValue.String("value"))
        }
      }

      "pass all the tags, annotations and baggage to the FinishedSpan instance when started and finished" in {
        Kamon.buildSpan("full-span")
          .withSpanTag("builder-string-tag", "value")
          .withSpanTag("builder-boolean-tag-true", true)
          .withSpanTag("builder-boolean-tag-false", false)
          .withSpanTag("builder-number-tag", 42)
          .withStartTimestamp(100)
          .start()
          .addSpanTag("span-string-tag", "value")
          .addSpanTag("span-boolean-tag-true", true)
          .addSpanTag("span-boolean-tag-false", false)
          .addSpanTag("span-number-tag", 42)
          .annotate("simple-annotation")
          .annotate("regular-annotation", Map("data" -> "something"))
          .annotate(4200, "custom-annotation-1", Map("custom" -> "yes-1"))
          .annotate(Annotation(4201, "custom-annotation-2", Map("custom" -> "yes-2")))
          .setOperationName("fully-populated-span")
          .finish(200)

        eventually(timeout(2 seconds)) {
          val finishedSpan = reporter.nextSpan().value
          finishedSpan.operationName shouldBe ("fully-populated-span")
          finishedSpan.startTimestampMicros shouldBe 100
          finishedSpan.endTimestampMicros shouldBe 200
          finishedSpan.tags should contain allOf(
            "builder-string-tag" -> TagValue.String("value"),
            "builder-boolean-tag-true" -> TagValue.True,
            "builder-boolean-tag-false" -> TagValue.False,
            "builder-number-tag" -> TagValue.Number(42),
            "span-string-tag" -> TagValue.String("value"),
            "span-boolean-tag-true" -> TagValue.True,
            "span-boolean-tag-false" -> TagValue.False,
            "span-number-tag" -> TagValue.Number(42)
          )

          finishedSpan.annotations.length shouldBe (4)
          val annotations = finishedSpan.annotations.groupBy(_.name)
          annotations.keys should contain allOf(
            "simple-annotation",
            "regular-annotation",
            "custom-annotation-1",
            "custom-annotation-2"
          )

          val customAnnotationOne = annotations("custom-annotation-1").head
          customAnnotationOne.timestampMicros shouldBe (4200)
          customAnnotationOne.fields shouldBe (Map("custom" -> "yes-1"))

          val customAnnotationTwo = annotations("custom-annotation-2").head
          customAnnotationTwo.timestampMicros shouldBe (4201)
          customAnnotationTwo.fields shouldBe (Map("custom" -> "yes-2"))
        }
      }
    }
  }

  @volatile var registration: Registration = _
  val reporter = new TestSpanReporter()

  override protected def beforeAll(): Unit = {
    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.addReporter(reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
  }
}
