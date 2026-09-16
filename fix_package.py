with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace("import com.example.protocol.v1.BaseMessage\nimport com.example.protocol.v1.ProtocolSerializer\npackage com.example.ui\n", "package com.example.ui\n\nimport com.example.protocol.v1.BaseMessage\nimport com.example.protocol.v1.ProtocolSerializer\n")

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
