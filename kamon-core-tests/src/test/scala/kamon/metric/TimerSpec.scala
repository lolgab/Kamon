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

package kamon.metric

import kamon.Kamon
import org.scalatest.{Matchers, WordSpec}


class TimerSpec extends WordSpec with Matchers {
  import TimerTestHelper._

  "a Timer" should {
    "record the duration between calls to .start() and .stop() in the StartedTimer" in {
      val timer = Kamon.timer("timer-spec")
      timer.start().stop()
      timer.start().stop()
      timer.start().stop()

      timer.distribution().count shouldBe(3)
    }

    "ensure that a started timer can only be stopped once" in {
      val timer = Kamon.timer("timer-spec")
      val startedTimer = timer.start()
      startedTimer.stop()
      startedTimer.stop()
      startedTimer.stop()

      timer.distribution().count shouldBe(1)
    }


    "allow to record values and produce distributions as Histograms do" in {
      val timer = Kamon.timer("test-timer")
      timer.record(100)
      timer.record(150, 998)
      timer.record(200)

      val distribution = timer.distribution()
      distribution.min shouldBe(100)
      distribution.max shouldBe(200)
      distribution.count shouldBe(1000)
      distribution.buckets.length shouldBe 3
      distribution.buckets.map(b => (b.value, b.frequency)) should contain.allOf(
        (100 -> 1),
        (150 -> 998),
        (200 -> 1)
      )

      val emptyDistribution = timer.distribution()
      emptyDistribution.min shouldBe(0)
      emptyDistribution.max shouldBe(0)
      emptyDistribution.count shouldBe(0)
      emptyDistribution.buckets.length shouldBe 0
    }
  }
}

object TimerTestHelper {

  implicit class HistogramMetricSyntax(histogram: Histogram) {
    def distribution(resetState: Boolean = true): Distribution = histogram  match {
      case h: AtomicHdrHistogram  => h.snapshot(resetState).distribution
      case h: HdrHistogram        => h.snapshot(resetState).distribution
    }
  }

  implicit class TimerMetricSyntax(metric: TimerMetric) {
    def distribution(resetState: Boolean = true): Distribution =
      metric.refine(Map.empty[String, String]) match {
        case t: TimerImpl     => t.histogram.distribution(resetState)
      }
  }
}
