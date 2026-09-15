import re

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    content = f.read()

# Replace exactly this:
target = """                }
        }
    }
}
// -------------------------------------------------------------
// LIVE CARD"""

replacement = """                }
            }
        }
    }
}
// -------------------------------------------------------------
// LIVE CARD"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("NOT FOUND")

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'w') as f:
    f.write(content)
