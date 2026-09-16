import re

with open('app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'r') as f:
    content = f.read()

# Replace SharedFlow with Channel
content = content.replace("import kotlinx.coroutines.flow.MutableSharedFlow", "import kotlinx.coroutines.channels.Channel\nimport kotlinx.coroutines.flow.receiveAsFlow")
content = content.replace("import kotlinx.coroutines.flow.SharedFlow", "")
content = content.replace("import kotlinx.coroutines.flow.asSharedFlow", "")

content = content.replace("private val _incomingMessages = MutableSharedFlow<BaseMessage>(extraBufferCapacity = 16)", "private val _incomingMessages = Channel<BaseMessage>(Channel.UNLIMITED)")
content = content.replace("val incomingMessages: SharedFlow<BaseMessage> = _incomingMessages.asSharedFlow()", "val incomingMessages: Flow<BaseMessage> = _incomingMessages.receiveAsFlow()")
content = content.replace("_incomingMessages.tryEmit(message)", "_incomingMessages.trySend(message)")

with open('app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'w') as f:
    f.write(content)
