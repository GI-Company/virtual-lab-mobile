import re

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    content = f.read()

content = re.sub(r'\}\s*\}\s*\}\s*\}\s*// -------------------------------------------------------------\s*// LIVE CARD', 
                 '}\n            }\n        }\n    }\n}\n// -------------------------------------------------------------\n// LIVE CARD', content)

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'w') as f:
    f.write(content)
