package com.celonis.kafka.connect.ems.testcontainers

import com.celonis.kafka.connect.ems.testcontainers.connect.KafkaConnectClient
import com.celonis.kafka.connect.ems.testcontainers.connect.KafkaConnectContainer
import com.celonis.kafka.connect.ems.testcontainers.sr.SchemaRegistryContainer
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

import java.nio.file.Paths
import java.util.Properties

trait KafkaConnectEnvironment extends ToxiproxyEnvironment {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  val confluentPlatformVersion: String = sys.env.getOrElse("CONFLUENT_VERSION", "6.1.0")

  val kafkaContainer: KafkaContainer = {
    val kafkaImage: DockerImageName = DockerImageName.parse(s"confluentinc/cp-kafka:$confluentPlatformVersion")
    val k = new KafkaContainer(kafkaImage)
      .withNetwork(network)
      .withNetworkAliases("kafka")
      .withLogConsumer(new Slf4jLogConsumer(log))
    k.start()
    k
  }

  val schemaRegistryContainer: SchemaRegistryContainer = {
    val sr = new SchemaRegistryContainer(confluentPlatformVersion, kafkaContainer)
      .withLogConsumer(new Slf4jLogConsumer(log))
    sr.start()
    sr
  }

  val kafkaConnectContainer: KafkaConnectContainer = {
    val connectPath = Paths.get(sys.env.getOrElse("EMS_CONNECTOR_PATH",
                                                  throw new RuntimeException("EMS_CONNECTOR_PATH env variable not set"),
    ))
    val kc = new KafkaConnectContainer(confluentPlatformVersion, kafkaContainer, schemaRegistryContainer, connectPath)
      .withLogConsumer(new Slf4jLogConsumer(log))
    kc.start()
    kc
  }

  val kafkaConnectClient: KafkaConnectClient = new KafkaConnectClient(kafkaConnectContainer)

  def createProducer[K, V](
    keySer:            Class[_],
    valSer:            Class[_],
    brokerUrl:         String,
    schemaRegistryUrl: String,
  ): KafkaProducer[K, V] = {
    val props = new Properties
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySer)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valSer)
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl)
    props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl)
    new KafkaProducer[K, V](props)
  }

  val stringAvroProducer: KafkaProducer[String, Any] =
    createProducer(
      classOf[StringSerializer],
      classOf[KafkaAvroSerializer],
      kafkaContainer.getBootstrapServers,
      schemaRegistryContainer.getSchemaRegistryUrl,
    )
}
