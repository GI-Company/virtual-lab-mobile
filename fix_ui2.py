import re

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    content = f.read()

# 1. Fix the collection
content = content.replace("    val transportMode by viewModel.transportMode.collectAsStateWithLifecycle()",
"""    val transportMode by viewModel.transportMode.collectAsStateWithLifecycle()
    val usbBaseUrl by viewModel.usbBaseUrl.collectAsStateWithLifecycle()""")
content = content.replace("usbBaseUrl = viewModel.usbBaseUrl.collectAsStateWithLifecycle().value,",
"usbBaseUrl = usbBaseUrl,")

# 2. Find the bad block and replace it with nothing.
bad_block = """            } else {
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
            }"""
content = content.replace(bad_block, "")

# Now find where the VirtualLabCard ends, and correctly insert the USB Development offline block.
# Actually, the block was supposed to be inside VirtualLabCard where the discovery state is displayed.
# Let's find:
#             } else if (transportMode != com.example.ui.TransportMode.USB_ADB) {
#                 // Show discovery results
#                 when (discoveryState) {
# ...
#                     }
#                     is DiscoveryState.Error -> {
# ...
#                     }
#                 }
#             }
#         }
#     }
# }
# So we can just find "is DiscoveryState.Error -> {" block end.

replacement_end = """                    is DiscoveryState.Error -> {
                        Text(
                            "Discovery error: ${discoveryState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = onRetrySearch) {
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
            }"""

content = re.sub(r'                    is DiscoveryState\.Error -> \{\n.*?Text\("Retry Search"\)\n                        \}\n                    \}\n                \}\n            \}', replacement_end, content, flags=re.DOTALL)

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'w') as f:
    f.write(content)
