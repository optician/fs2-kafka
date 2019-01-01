/*
 * Copyright 2018-2019 OVO Energy Limited
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

package fs2.kafka

import cats.Show
import cats.syntax.show._
import fs2.kafka.internal.instances._
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition

/**
  * [[CommittableOffset]] represents an [[offsetAndMetadata]] for a
  * [[topicPartition]], along with the ability to commit that offset
  * to Kafka with [[commit]]. Note that offsets are normally committed
  * in batches for performance reasons. Sinks like [[commitBatch]] and
  * [[commitBatchWithin]] use [[CommittableOffsetBatch]] to commit the
  * offsets in batches.<br>
  * <br>
  * While normally not necessary, [[CommittableOffset#apply]] can be
  * used to create a new instance.
  */
sealed abstract class CommittableOffset[F[_]] {

  /**
    * The topic and partition for which [[offsetAndMetadata]]
    * can be committed using [[commit]].
    */
  def topicPartition: TopicPartition

  /**
    * The offset and metadata for the [[topicPartition]], which
    * can be committed using [[commit]].
    */
  def offsetAndMetadata: OffsetAndMetadata

  /**
    * The [[topicPartition]] and [[offsetAndMetadata]] as a `Map`.
    * This is provided for convenience and is always guaranteed to
    * be equivalent to the following.
    *
    * {{{
    * Map(topicPartition -> offsetAndMetadata)
    * }}}
    */
  def offsets: Map[TopicPartition, OffsetAndMetadata]

  /**
    * The [[CommittableOffset]] as a [[CommittableOffsetBatch]].
    */
  def batch: CommittableOffsetBatch[F]

  /**
    * Commits the [[offsetAndMetadata]] for the [[topicPartition]] to
    * Kafka. Note that offsets are normally committed in batches for
    * performance reasons. Prefer to use sinks like [[commitBatch]]
    * or [[commitBatchWithin]], or [[CommittableOffsetBatch]] for
    * that reason.
    */
  def commit: F[Unit]

  /**
    * The commit function we are using in [[commit]] to commit the
    * [[offsetAndMetadata]] for the [[topicPartition]]. Is used to
    * help achieve better performance when batching offsets.
    */
  private[kafka] def commitOffsets: Map[TopicPartition, OffsetAndMetadata] => F[Unit]
}

object CommittableOffset {

  /**
    * Creates a new [[CommittableOffset]] with the specified `topicPartition`
    * and `offsetAndMetadata`, along with `commit`, describing how to commit
    * an arbitrary `Map` of topic-partition offsets.
    */
  def apply[F[_]](
    topicPartition: TopicPartition,
    offsetAndMetadata: OffsetAndMetadata,
    commit: Map[TopicPartition, OffsetAndMetadata] => F[Unit]
  ): CommittableOffset[F] = {
    val _topicPartition = topicPartition
    val _offsetAndMetadata = offsetAndMetadata
    val _commit = commit

    new CommittableOffset[F] {
      override val topicPartition: TopicPartition =
        _topicPartition

      override val offsetAndMetadata: OffsetAndMetadata =
        _offsetAndMetadata

      override def offsets: Map[TopicPartition, OffsetAndMetadata] =
        Map(_topicPartition -> _offsetAndMetadata)

      override def batch: CommittableOffsetBatch[F] =
        CommittableOffsetBatch(offsets, _commit)

      override def commit: F[Unit] =
        _commit(offsets)

      override val commitOffsets: Map[TopicPartition, OffsetAndMetadata] => F[Unit] =
        _commit

      override def toString: String =
        Show[CommittableOffset[F]].show(this)
    }
  }

  implicit def committableOffsetShow[F[_]]: Show[CommittableOffset[F]] =
    Show.show(co => show"CommittableOffset(${co.topicPartition} -> ${co.offsetAndMetadata})")
}
