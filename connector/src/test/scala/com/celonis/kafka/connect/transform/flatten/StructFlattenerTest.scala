package com.celonis.kafka.connect.transform.flatten

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.connect.data.Schema
import org.apache.kafka.connect.data.SchemaBuilder
import org.apache.kafka.connect.data.Struct
import org.apache.kafka.connect.errors.DataException
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class StructFlattenerTest extends AnyFunSuite {

  test("do nothing on a primitive") {
    val primitives = Map[Any, Schema](
      123   -> SchemaBuilder.int16().build(),
      "abc" -> SchemaBuilder.string().build(),
      456L  -> SchemaBuilder.int64().build(),
    )

    primitives.foreach {
      case (primitive, schema) =>
        val result = flatten(primitive, schema)
        assertResult(result)(primitive)
    }
  }

  test("flattens a nested field") {

    val nestedSchema = SchemaBuilder.struct().name("AStruct")
      .field("a_bool", SchemaBuilder.bool().build())
      .build()
    val nested = new Struct(nestedSchema)
    nested.put("a_bool", true)

    val schema = SchemaBuilder.struct()
      .field("a_string", SchemaBuilder.string().schema())
      .field("x", nestedSchema)
      .build()

    val struct = new Struct(schema)
    struct.put("a_string", "hello")
    struct.put("x", nested)

    val flatSchema = SchemaBuilder
      .struct()
      .field("a_string", SchemaBuilder.string().optional().schema())
      .field("x_a_bool", SchemaBuilder.bool().optional().schema())
      .build()

    val result = flatten(struct, schema).asInstanceOf[Struct]

    assertResult(flatSchema)(result.schema())
    assertResult("hello")(result.get("a_string"))
    assertResult(true)(result.get("x_a_bool"))

    assertThrows[DataException](result.get("x"))
  }

  test("transforms arrays and maps of primitives into strings") {
    val nestedSchema = SchemaBuilder.struct().name("AStruct")
      .field("an_array", SchemaBuilder.array(Schema.INT32_SCHEMA).build())
      .field("a_map", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.STRING_SCHEMA).build())
      .build()

    val nested = new Struct(nestedSchema)
    nested.put("an_array", List(1, 2, 3).asJava)
    nested.put("a_map", Map("1" -> "a", "2" -> "b").asJava)

    val schema = SchemaBuilder.struct()
      .field("nested", nestedSchema)
      .build()

    val struct = new Struct(schema)
    struct.put("nested", nested)

    val flatSchema = SchemaBuilder
      .struct()
      .field("nested_an_array", Schema.OPTIONAL_STRING_SCHEMA)
      .field("nested_a_map", Schema.OPTIONAL_STRING_SCHEMA)
      .build()

    val mapper = new ObjectMapper()

    val result = flatten(struct, schema).asInstanceOf[Struct]

    assertResult(flatSchema)(result.schema())

    assertResult(mutable.Map("1" -> "a", "2" -> "b")) {
      mapper.readValue(result.getString("nested_a_map"), classOf[java.util.Map[String, String]]).asScala
    }
    assertResult(mutable.Buffer(1, 2, 3)) {
      mapper.readValue(result.getString("nested_an_array"), classOf[java.util.LinkedList[String]]).asScala
    }
  }

  test("JSON encodes collection of AVRO records") {
    val nestedSchema = SchemaBuilder.struct()
      .field("a_bool", Schema.OPTIONAL_BOOLEAN_SCHEMA)
      .field("a_long", Schema.OPTIONAL_INT64_SCHEMA)
      .build()

    val nested = new Struct(nestedSchema)
    nested.put("a_bool", true)
    nested.put("a_long", 33L)

    val schema = SchemaBuilder.struct()
      .field("an_array", SchemaBuilder.array(nestedSchema))
      .field("a_map", SchemaBuilder.map(Schema.STRING_SCHEMA, nestedSchema))
      .build()

    val struct = new Struct(schema)
    struct.put("an_array", List(nested).asJava)
    struct.put("a_map", Map("key" -> nested).asJava)

    val flatSchema = SchemaBuilder
      .struct()
      .field("an_array", Schema.OPTIONAL_STRING_SCHEMA)
      .field("a_map", Schema.OPTIONAL_STRING_SCHEMA)
      .build()

    val result = flatten(struct, schema).asInstanceOf[Struct]
    assertResult("""[{"a_bool":true,"a_long":33}]""")(result.get("an_array"))
    assertResult(flatSchema)(result.schema())
    assertResult("""{"key":{"a_bool":true,"a_long":33}}""")(result.get("a_map"))
  }

  test("JSON encodes collection of JSON records") {

    val nestedSchema = SchemaBuilder.struct()
      .field("a_bool", Schema.BOOLEAN_SCHEMA)
      .field("a_long", Schema.INT64_SCHEMA)
      .build()

    val schema = SchemaBuilder
      .struct()
      .field("an_array", SchemaBuilder.array(nestedSchema).build())
      .field("a_map", SchemaBuilder.map(Schema.STRING_SCHEMA, nestedSchema).build())
      .build()

    val flatSchema = SchemaBuilder
      .struct()
      .field("an_array", Schema.OPTIONAL_STRING_SCHEMA)
      .field("a_map", Schema.OPTIONAL_STRING_SCHEMA)
      .build()

    val nested = Map[String, Any](
      "a_bool" -> true,
      "a_long" -> 33,
    ).asJava

    val jsonRecord = Map[String, Any](
      "an_array" -> List(nested).asJava,
      "a_map"    -> Map("key" -> nested).asJava,
    ).asJava

    val result = flatten(jsonRecord, schema).asInstanceOf[Struct]

    assertResult(flatSchema)(result.schema())
    assertResult("""[{"a_bool":true,"a_long":33}]""")(result.get("an_array"))
    assertResult("""{"key":{"a_bool":true,"a_long":33}}""")(result.get("a_map"))
  }

  test("drops arrays/maps when 'discardCollections' is set") {
    val nestedSchema = SchemaBuilder.struct().name("AStruct")
      .field("a_nested_map", SchemaBuilder.map(SchemaBuilder.string(), SchemaBuilder.string()).build())
      .field("a_nested_array", SchemaBuilder.array(SchemaBuilder.string()).build())
      .field("a_bool", SchemaBuilder.bool().build())
      .build()
    val nested = new Struct(nestedSchema)
    nested.put("a_nested_map", mutable.HashMap("x" -> "y").asJava)
    nested.put("a_nested_array", List("blah").asJava)
    nested.put("a_bool", true)

    val schema = SchemaBuilder.struct()
      .field("a_string", SchemaBuilder.string().schema())
      .field("a_map", SchemaBuilder.map(SchemaBuilder.string(), SchemaBuilder.string()).schema())
      .field("an_array", SchemaBuilder.array(SchemaBuilder.string()).schema())
      .field("a_struct", nestedSchema)
      .build()

    val struct = new Struct(schema)
    struct.put("a_string", "hello")
    struct.put("a_map", mutable.HashMap("hello" -> "hi-there...").asJava)
    struct.put("an_array", List("discard", "me", "please").asJava)
    struct.put("a_struct", nested)

    val flatSchema = SchemaBuilder
      .struct()
      .field("a_string", SchemaBuilder.string().optional().build())
      .field("a_struct_a_bool", SchemaBuilder.bool().optional().build())
      .build()

    val result = flatten(struct, schema, true).asInstanceOf[Struct]

    assertResult(flatSchema)(result.schema())
    assertResult("hello")(result.get("a_string"))
    assertResult(true)(result.get("a_struct_a_bool"))

    assertThrows[DataException](result.get("a_struct"))
    assertThrows[DataException](result.get("a_map"))
    assertThrows[DataException](result.get("an_array"))
  }

  test("leaves top level collections untouched when 'discardCollections' is set") {
    case class TestData(label: String, value: AnyRef, flattenedSchema: Schema)

    val mapValue:   java.util.Map[String, Int] = mutable.HashMap("x" -> 22).asJava
    val arrayValue: java.util.List[String]     = List("a", "b", "c").asJava

    val testData = List(
      TestData(
        "an map in top-level position",
        mapValue,
        SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT32_SCHEMA).build(),
      ),
      TestData("an array in top-level position", arrayValue, SchemaBuilder.array(Schema.STRING_SCHEMA).build()),
    )
    testData.foreach {
      case TestData(label, value, schema) =>
        withClue(s"$label : $value") {
          assertResult(value) {
            flatten(value, schema, true)
          }
        }
    }
  }

  test("when the schema is inferred, flattens nested maps instead than json-encoding them") {
    val nestedMap = Map(
      "some" -> Map(
        "nested-string" -> "a-string",
        "nested-array"  -> List("a", "b", "c").asJava,
        "nested-map"    -> Map[String, Any]("one-more-level" -> true).asJava,
      ).asJava,
    ).asJava

    val schema = SchemaBuilder.struct()
      .field(
        "some",
        SchemaBuilder.struct()
          .field("nested-string", Schema.OPTIONAL_STRING_SCHEMA)
          .field("nested-array", Schema.OPTIONAL_STRING_SCHEMA)
          .field("nested-map",
                 SchemaBuilder.struct()
                   .field("one-more-level", Schema.OPTIONAL_BOOLEAN_SCHEMA).build(),
          )
          .build(),
      ).build()

    val flattenedSchema = SchemaBuilder.struct()
      .field("some_nested_string", Schema.OPTIONAL_STRING_SCHEMA)
      .field("some_nested_array", Schema.OPTIONAL_STRING_SCHEMA)
      .field("some_nested_map_one_more_level", Schema.OPTIONAL_BOOLEAN_SCHEMA)
      .build()

    val expected = new Struct(flattenedSchema)

    expected.put("some_nested_string", "a-string")
    expected.put("some_nested_array", """["a","b","c"]""")
    expected.put("some_nested_map_one_more_level", true)

    assertResult(expected)(flatten(nestedMap, schema))
  }

  private def flatten(value: Any, schema: Schema, discardCollections: Boolean = false): Any =
    StructFlattener.flatten(value, new SchemaFlattener(discardCollections).flatten(schema))

}
