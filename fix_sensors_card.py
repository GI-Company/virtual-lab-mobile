import re

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    content = f.read()

target = """                }
        }
    }
}

// -------------------------------------------------------------
// LIVE CARD
// -------------------------------------------------------------"""

replacement = """                }
            }
        }
    }
}

// -------------------------------------------------------------
// LIVE CARD
// -------------------------------------------------------------"""

content = content.replace(target, replacement)

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'w') as f:
    f.write(content)
