import json

with open('app/src/test/resources/protocol_v1/camera_capabilities.json', 'r') as f:
    data = json.load(f)

data['fps_ranges'] = [[15, 30], [30, 30]]
data['resolutions'] = [[1920, 1080], [1280, 720]]

with open('app/src/test/resources/protocol_v1/camera_capabilities.json', 'w') as f:
    json.dump(data, f, indent=2)

