import re

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    content = f.read()

# 1. Fix the top level collection
content = content.replace("    val transportMode by viewModel.transportMode.collectAsStateWithLifecycle()",
"""    val transportMode by viewModel.transportMode.collectAsStateWithLifecycle()
    val usbBaseUrl by viewModel.usbBaseUrl.collectAsStateWithLifecycle()""")

content = content.replace("usbBaseUrl = viewModel.usbBaseUrl.collectAsStateWithLifecycle().value,",
"usbBaseUrl = usbBaseUrl,")

# 2. Fix the broken block around 950
# We need to find the stray "} else { Surface... USB Development Session Offline ... }" and remove it.
# Then find the end of VirtualLabCard and insert it correctly.

# First, extract the block that was mistakenly inserted.
bad_block_regex = r'            \} else \{\n                Surface\(\n                    color = MaterialTheme\.colorScheme\.surfaceVariant\.copy\(alpha = 0\.3f\),\n                    shape = MaterialTheme\.shapes\.medium,\n                    modifier = Modifier\.fillMaxWidth\(\)\.padding\(top = 8\.dp\)\n                \) \{\n                    Column\(\n                        modifier = Modifier\.fillMaxWidth\(\)\.padding\(12\.dp\),\n                        horizontalAlignment = Alignment\.CenterHorizontally\n                    \) \{\n                        Text\(\n                            "USB Development Session Offline",\n                            style = MaterialTheme\.typography\.labelMedium\.copy\(fontWeight = FontWeight\.Bold\),\n                            color = MaterialTheme\.colorScheme\.onSurfaceVariant\n                        \)\n                        Spacer\(Modifier\.height\(8\.dp\)\)\n                        Text\(\n                            "Ensure `adb reverse tcp:8765 tcp:8765` is running and the Desktop is listening\.",\n                            style = MaterialTheme\.typography\.bodySmall,\n                            textAlign = TextAlign\.Center,\n                            color = MaterialTheme\.colorScheme\.onSurfaceVariant\n                        \)\n                    \}\n                \}\n            \}\n'

# Let's just restore the file using git checkout, then re-apply correctly.
