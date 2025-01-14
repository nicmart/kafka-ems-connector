package com.celonis.kafka.connect.transform

import com.celonis.kafka.connect.ems.config.EmsSinkConfigConstants._
import com.celonis.kafka.connect.ems.config.PropertiesHelper
import cats.syntax.either._
import cats.instances.option._
import cats.syntax.apply._

case class FlattenerConfig(
  discardCollections: Boolean                                = false,
  jsonBlobChunks:     Option[FlattenerConfig.JsonBlobChunks] = None,
)

object FlattenerConfig {
  case class JsonBlobChunks(chunks: Int, fallbackVarcharLength: Int)

  def extract(props: Map[String, _], fallbackVarcharLength: Option[Int]): Either[String, Option[FlattenerConfig]] = {
    import PropertiesHelper._
    (getBoolean(props, FLATTENER_ENABLE_KEY).getOrElse(false),
     getBoolean(props, FLATTENER_DISCARD_COLLECTIONS_KEY).getOrElse(false),
     getInt(props, FLATTENER_JSONBLOB_CHUNKS_KEY),
     fallbackVarcharLength,
    ) match {
      case (false, true, _, _) =>
        requiredKeyMissingErrorMsg(FLATTENER_ENABLE_KEY)(FLATTENER_DISCARD_COLLECTIONS_KEY).asLeft
      case (false, _, Some(_), _) =>
        requiredKeyMissingErrorMsg(FLATTENER_ENABLE_KEY)(FLATTENER_JSONBLOB_CHUNKS_KEY).asLeft
      case (_, _, Some(_), None) =>
        requiredKeyMissingErrorMsg(FALLBACK_VARCHAR_LENGTH_KEY)(FLATTENER_JSONBLOB_CHUNKS_KEY).asLeft
      case (true, discardCollections, maybeNumChunks, _) =>
        Some(FlattenerConfig(
          discardCollections,
          (maybeNumChunks, fallbackVarcharLength).mapN(JsonBlobChunks),
        )).asRight
      case _ =>
        None.asRight
    }
  }

  private def requiredKeyMissingErrorMsg(missingKey: String)(key: String) =
    s"Configuration key $key was supplied without setting required key $missingKey . Please supply a value for both keys."

}
