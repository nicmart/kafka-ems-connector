package com.celonis.kafka.connect.ems.converter

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.apache.kafka.connect.data.Schema
import org.apache.kafka.connect.data.SchemaAndValue
import org.apache.kafka.connect.errors.DataException
import org.apache.kafka.connect.storage.Converter

import java.util
import scala.util.Try

final class XmlConverter extends Converter {
  private val mapper        = new XmlMapper()
  private val typeReference = new TypeReference[Any]() {}

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = ()

  override def fromConnectData(topic: String, schema: Schema, value: Any): Array[Byte] =
    throw new NotImplementedError("XML Serialization has not been implemented")

  override def toConnectData(topic: String, value: Array[Byte]): SchemaAndValue =
    value match {
      case null => SchemaAndValue.NULL
      case nonNullValue =>
        val node = Try(mapper.readValue(nonNullValue, typeReference))
          .fold(
            e => throw new DataException("Converting XML to Kafka Connect data failed due to serialization error: ", e),
            identity,
          )
        new SchemaAndValue(null, node)
    }

}
