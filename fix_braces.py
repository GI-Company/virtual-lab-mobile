import re

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    content = f.read()

# We need to find the exact end of VirtualLabCard and append `\n        }\n    }\n}`.
# Where did the replacement end?
# It ended with:
#                             color = MaterialTheme.colorScheme.onSurfaceVariant
#                         )
#                     }
#                 }
#             }

replacement_end = """                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}"""

content = content.replace("""                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }""", replacement_end, 1)

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'w') as f:
    f.write(content)
