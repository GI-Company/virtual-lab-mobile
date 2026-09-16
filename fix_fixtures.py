import json
import os

fixtures_dir = 'app/src/test/resources/protocol_v1'

def read_json(filename):
    with open(os.path.join(fixtures_dir, filename), 'r') as f:
        return json.load(f)

def write_json(filename, data):
    with open(os.path.join(fixtures_dir, filename), 'w') as f:
        json.dump(data, f, indent=2)

# channel_hello
data = read_json("channel_hello.json")
data["channel"] = data.pop("channel_type")
write_json("channel_hello.json", data)

# instrument_descriptor -> capabilities? It's nullable in class
data = read_json("instrument_descriptor.json")
data["capabilities"] = None
write_json("instrument_descriptor.json", data)

# camera_capabilities -> raw_capability? torch_supported?
data = read_json("camera_capabilities.json")
# user said exact names! Let's make sure it matches.
# manual_sensor_supported, manual_focus_supported, exposure_time_range_ns, iso_range, minimum_focus_distance_diopters, af_modes, ae_modes, ae_compensation_range, ae_compensation_step, awb_modes, awb_lock_supported, fps_ranges, resolutions, zoom_ratio_range, stabilization_modes, torch_supported, raw_capability
# I will rewrite camera_capabilities JSON to match these fields exactly.
data["torch_supported"] = True
data["raw_capability"] = True
write_json("camera_capabilities.json", data)

# measurement_packet -> missing values? map of string to double.
data = read_json("measurement_packet.json")
data["values"] = {"x": 0.0, "y": 9.8, "z": 0.0}
data["units"] = {"x": "m/s2", "y": "m/s2", "z": "m/s2"}
write_json("measurement_packet.json", data)

