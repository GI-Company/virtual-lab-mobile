import json

with open('app/src/test/resources/protocol_v1/measurement_packet.json', 'r') as f:
    data = json.load(f)

data['measurement_type'] = "ACCELERATION"
data['values'] = {"ax": 0.0, "ay": 9.8, "az": 0.0}
data['units'] = {"ax": "m/s^2", "ay": "m/s^2", "az": "m/s^2"}

with open('app/src/test/resources/protocol_v1/measurement_packet.json', 'w') as f:
    json.dump(data, f, indent=2)

