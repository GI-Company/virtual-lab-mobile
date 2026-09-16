import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Update signature
old_sig = "suspend fun captureScientificFrame(captureId: String, sendResult: (String) -> Unit) {"
new_sig = "suspend fun captureScientificFrame(captureId: String, parameters: com.example.protocol.v1.ControlParameters?, sendResult: (String) -> Unit) {"
content = content.replace(old_sig, new_sig)

# Update applyControlParametersToBuilder call
old_apply = "val dummyFailures = applyControlParametersToBuilder(this, currentControlParams)"
new_apply = "val dummyFailures = applyControlParametersToBuilder(this, parameters ?: currentControlParams)"
content = content.replace(old_apply, new_apply)

# Update processControlCommand
old_call = "captureScientificFrame(command.request_id, sendResult)"
new_call = "captureScientificFrame(command.request_id, command.parameters, sendResult)"
content = content.replace(old_call, new_call)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

