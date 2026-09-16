import json
import os

fixtures_dir = 'app/src/test/resources/protocol_v1'

def write_json(filename, data):
    with open(os.path.join(fixtures_dir, filename), 'w') as f:
        json.dump(data, f, indent=2)

write_json("channel_hello.json", {
  "message_type": "CHANNEL_HELLO",
  "schema_version": "1",
  "device_id": "device-123",
  "stream_id": "stream-456",
  "channel_type": "control"
})

write_json("instrument_descriptor.json", {
  "message_type": "INSTRUMENT_DESCRIPTOR",
  "schema_version": "1",
  "device_id": "device-123",
  "stream_id": "stream-456",
  "name": "Test Node",
  "description": "A node",
  "sensors": []
})

write_json("measurement_packet.json", {
  "message_type": "MEASUREMENT_PACKET",
  "schema_version": "1",
  "device_id": "device-123",
  "stream_id": "stream-456",
  "source_session_id": "session-1",
  "measurement_type": "acceleration",
  "sensor_id": "sensor-1",
  "sequence": 100,
  "device_timestamp_ns": 1000000,
  "device_timebase": "MONOTONIC",
  "device_utc_ns": None,
  "values": [0.0, 9.8, 0.0],
  "units": "m/s^2",
  "accuracy": 3
})

write_json("camera_control_request.json", {
  "message_type": "CAMERA_CONTROL_REQUEST",
  "schema_version": "1",
  "request_id": "req-1",
  "device_id": "device-123",
  "control_type": "SET_PARAMETERS",
  "camera_stream_key": {
    "camera_id": "0",
    "logical_camera_id": None,
    "physical_camera_id": None
  },
  "requested_parameters": {
    "af_mode": "AUTO"
  }
})

write_json("camera_control_result.json", {
  "message_type": "CAMERA_CONTROL_RESULT",
  "schema_version": "1",
  "request_id": "req-1",
  "device_id": "device-123",
  "overall_status": "APPLIED",
  "parameter_results": {
    "af_mode": {
      "requested": "AUTO",
      "applied": "AUTO",
      "status": "APPLIED"
    }
  },
  "error_message": None
})

write_json("camera_capabilities.json", {
  "message_type": "CAMERA_CAPABILITIES",
  "schema_version": "1",
  "device_id": "device-123",
  "camera_stream_key": {
    "camera_id": "0"
  },
  "manual_sensor_supported": True,
  "manual_focus_supported": True,
  "exposure_time_range_ns": [1000, 100000000],
  "iso_range": [100, 3200],
  "minimum_focus_distance_diopters": 10.0,
  "af_modes": ["OFF", "AUTO"],
  "ae_modes": ["OFF", "ON"],
  "ae_compensation_range": [-12, 12],
  "ae_compensation_step": 0.16666,
  "awb_modes": ["OFF", "AUTO"],
  "awb_lock_supported": True,
  "fps_ranges": ["15-30", "30-30"],
  "resolutions": ["1920x1080", "1280x720"],
  "zoom_ratio_range": [1.0, 10.0],
  "stabilization_modes": ["OFF", "OPTICAL_ON"],
  "torch_supported": True,
  "raw_capability": False
})

write_json("camera_state.json", {
  "message_type": "CAMERA_STATE",
  "schema_version": "1",
  "device_id": "device-123",
  "camera_stream_key": {
    "camera_id": "0"
  },
  "current_exposure_time_ns": 20000000,
  "current_iso": 200,
  "current_focus_distance_diopters": 5.0,
  "current_af_mode": "AUTO",
  "thermal_status": "NORMAL"
})

write_json("scientific_capture_request.json", {
  "message_type": "SCIENTIFIC_CAPTURE_REQUEST",
  "schema_version": "1",
  "request_id": "req-2",
  "device_id": "device-123",
  "camera_stream_key": {
    "camera_id": "0"
  },
  "parameters": {
    "exposure_time_ns": 30000000
  }
})

write_json("scientific_capture_result.json", {
  "message_type": "SCIENTIFIC_CAPTURE_RESULT",
  "schema_version": "1",
  "request_id": "req-2",
  "device_id": "device-123",
  "status": "COMPLETED",
  "device_timestamp_ns": 1000000,
  "error_message": None
})

write_json("scientific_frame_ack.json", {
  "message_type": "SCIENTIFIC_FRAME_ACK",
  "schema_version": "1",
  "request_id": "req-2",
  "device_id": "device-123",
  "status": "COMMITTED",
  "error_message": None,
  "artifact_sha256": "abc123hash"
})

