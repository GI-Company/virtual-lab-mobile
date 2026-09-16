import re

content = """package com.example.protocol.v1

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ProtocolV1FixtureTest {

    private fun readFixture(filename: String): String {
        val classLoader = this.javaClass.classLoader
        val resource = classLoader?.getResource("protocol_v1/$filename")
        if (resource == null) {
            val file = File("src/test/resources/protocol_v1/$filename")
            return file.readText()
        }
        return File(resource.toURI()).readText()
    }

    private inline fun <reified T : BaseMessage> testRoundtrip(filename: String) {
        val jsonString = readFixture(filename)
        val deserialized = ProtocolSerializer.deserialize<T>(jsonString)
        val serialized = ProtocolSerializer.serialize(deserialized)
        
        // Assert deserialized fields logic can be handled via serialization back to json
        val expectedJson = ProtocolSerializer.json.parseToJsonElement(jsonString)
        val actualJson = ProtocolSerializer.json.parseToJsonElement(serialized)
        
        assertEquals("Mismatch for $filename", expectedJson, actualJson)
    }

    @Test
    fun testChannelHello() {
        testRoundtrip<ChannelHelloMessage>("channel_hello.json")
    }

    @Test
    fun testInstrumentDescriptor() {
        testRoundtrip<InstrumentDescriptorMessage>("instrument_descriptor.json")
    }

    @Test
    fun testMeasurementPacket() {
        testRoundtrip<MeasurementPacketMessage>("measurement_packet.json")
    }

    @Test
    fun testCameraControlRequest() {
        testRoundtrip<CameraControlRequestMessage>("camera_control_request.json")
    }

    @Test
    fun testCameraControlResult() {
        testRoundtrip<CameraControlResultMessage>("camera_control_result.json")
    }

    @Test
    fun testCameraCapabilities() {
        testRoundtrip<CameraCapabilitiesMessage>("camera_capabilities.json")
    }

    @Test
    fun testCameraState() {
        testRoundtrip<CameraStateMessage>("camera_state.json")
    }

    @Test
    fun testScientificCaptureRequest() {
        testRoundtrip<ScientificCaptureRequestMessage>("scientific_capture_request.json")
    }

    @Test
    fun testScientificCaptureResult() {
        testRoundtrip<ScientificCaptureResultMessage>("scientific_capture_result.json")
    }

    @Test
    fun testScientificFrameAck() {
        testRoundtrip<ScientificFrameAckMessage>("scientific_frame_ack.json")
    }

    @Test
    fun testCameraPreviewFrameMetadata() {
        testRoundtrip<CameraPreviewFrameMessage>("camera_preview_frame_metadata.json")
    }

    @Test
    fun testCameraScientificFrameMetadata() {
        testRoundtrip<CameraScientificFrameMessage>("camera_scientific_frame_metadata.json")
    }
}
"""

with open('app/src/test/java/com/example/protocol/v1/ProtocolV1Test.kt', 'w') as f:
    f.write(content)
