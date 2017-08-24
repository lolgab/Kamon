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

import kamon.Kamon.buildSpan
import kamon.testkit.{MetricInspection, Reconfigure}
import org.scalatest.{Matchers, WordSpecLike}

class SpanMetrics extends WordSpecLike with Matchers with MetricInspection with Reconfigure {

  sampleAlways()

  "Span Metrics" should {
    "be recorded for successful execution" in {
      val operation = "span-success"
      val operationTag = "operation" -> operation

      buildSpan(operation)
        .start()
        .finish()

      val histogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, noErrorTag))
      histogram.distribution().count shouldBe 1

      val errorHistogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, errorTag))
      errorHistogram.distribution().count shouldBe 0

    }

    "record correctly error latency and count" in {
      val operation = "span-failure"
      val operationTag = "operation" -> operation

      buildSpan(operation)
        .start()
        .addSpanTag("error", true)
        .finish()

      val histogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, noErrorTag))
      histogram.distribution().count shouldBe 0

      val errorHistogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, errorTag))
      errorHistogram.distribution().count shouldBe 1
    }

    "add a parentOperation tag to the metrics if span metrics scoping is enabled" in {
      val parent = buildSpan("parent").start()
      val parentOperationTag = "parentOperation" -> "parent"

      val operation = "span-with-parent"
      val operationTag = "operation" -> operation

      buildSpan(operation)
        .asChildOf(parent)
        .start()
        .addSpanTag("error", false)
        .finish()

      buildSpan(operation)
        .asChildOf(parent)
        .start()
        .addSpanTag("error", true)
        .finish()

      val histogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, noErrorTag, parentOperationTag))
      histogram.distribution().count shouldBe 1

      val errorHistogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, errorTag, parentOperationTag))
      errorHistogram.distribution().count shouldBe 1
    }

    "not add any parentOperation tag to the metrics if span metrics scoping is disabled" in withoutSpanScopingEnabled {
      val parent = buildSpan("parent").start()
      val parentOperationTag = "parentOperation" -> "parent"

      val operation = "span-with-parent"
      val operationTag = "operation" -> operation

      buildSpan(operation)
        .asChildOf(parent)
        .start()
        .addSpanTag("error", false)
        .finish()

      buildSpan(operation)
        .asChildOf(parent)
        .start()
        .addSpanTag("error", true)
        .finish()

      val histogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, noErrorTag, parentOperationTag))
      histogram.distribution().count shouldBe 0

      val errorHistogram = Span.Metrics.ProcessingTime.refine(Map(operationTag, errorTag, parentOperationTag))
      errorHistogram.distribution().count shouldBe 0
    }
  }

  val errorTag = "error" -> "true"
  val noErrorTag = "error" -> "false"

  private def withoutSpanScopingEnabled[T](f: => T): T = {
    disableSpanMetricScoping()
    val evaluated = f
    enableSpanMetricScoping()
    evaluated
  }
}


