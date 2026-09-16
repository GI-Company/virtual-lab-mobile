import json

with open('app/src/test/resources/protocol_v1/instrument_descriptor.json', 'r') as f:
    data = json.load(f)

data['capabilities'] = ["CAMERA", "SENSORS"]

with open('app/src/test/resources/protocol_v1/instrument_descriptor.json', 'w') as f:
    json.dump(data, f, indent=2)

