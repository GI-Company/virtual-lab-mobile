import json
import os

fixtures_dir = 'app/src/test/resources/protocol_v1'

def remove_nulls(d):
    if isinstance(d, dict):
        return {k: remove_nulls(v) for k, v in d.items() if v is not None}
    elif isinstance(d, list):
        return [remove_nulls(v) for v in d if v is not None]
    else:
        return d

for filename in os.listdir(fixtures_dir):
    if filename.endswith(".json"):
        filepath = os.path.join(fixtures_dir, filename)
        with open(filepath, 'r') as f:
            data = json.load(f)
        data = remove_nulls(data)
        with open(filepath, 'w') as f:
            json.dump(data, f, indent=2)

