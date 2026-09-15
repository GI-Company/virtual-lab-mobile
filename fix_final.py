import re

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    content = f.read()

# 1. We will find exactly:
target = """                        Button(onClick = onRetrySearch) {
                            Text("Retry Search")
                        }
                    }
                }
        }
    }
}"""

replacement = """                        Button(onClick = onRetrySearch) {
                            Text("Retry Search")
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "USB Development Session Offline",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ensure `adb reverse tcp:8765 tcp:8765` is running and the Desktop is listening.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("TARGET NOT FOUND!")

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'w') as f:
    f.write(content)
