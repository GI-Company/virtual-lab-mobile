import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# 1. In applyControlParametersToBuilder, remove REJECTED_PHYSICAL_CAMERA and infer manual modes.
# Wait, I'll use Python to do string replacements.

old_track = """        fun track(paramName: String, success: Boolean) {
            if (!success) failures[paramName] = "REJECTED_PHYSICAL_CAMERA"
        }"""
new_track = """        fun track(paramName: String, success: Boolean) {
            if (!success) failures[paramName] = "REJECTED"
        }"""
content = content.replace(old_track, new_track)

# Let's fix the manual mode inference.
# I will use a regex to replace the entire applyControlParametersToBuilder function up to the Focus section.
old_apply_top = """        // Base auto mode if no manual controls are specified
        setKey(builder, CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        
        // Focus
        when (params.af_mode) {"""

new_apply_top = """        // Base auto mode if no manual controls are specified
        setKey(builder, CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        
        var effectiveAfMode = params.af_mode
        if (params.focus_distance_diopters != null) {
             effectiveAfMode = "OFF"
        }

        var effectiveAeMode = params.ae_mode
        if (params.exposure_time_ns != null || params.iso != null) {
             effectiveAeMode = "OFF"
        }

        // Focus
        when (effectiveAfMode) {"""

content = content.replace(old_apply_top, new_apply_top)
content = content.replace("when (params.ae_mode)", "when (effectiveAeMode)")

# 2. In updateRepeatingRequest, change the status logic in onCaptureCompleted.
# I need to change:
# if (paramStatus == "APPLIED" && reqJson != appJson && appJson != null) {
#    paramStatus = "PARTIALLY_APPLIED"
# }
# to also handle appJson == null -> UNCONFIRMED

old_status_logic = """                                            var paramStatus = if (buildFail != null) buildFail else "APPLIED"
                                            if (paramStatus == "APPLIED" && reqJson != appJson && appJson != null) {
                                                paramStatus = "PARTIALLY_APPLIED"
                                            }"""

new_status_logic = """                                            var paramStatus = if (buildFail != null) buildFail else "APPLIED"
                                            if (paramStatus == "APPLIED") {
                                                if (appJson == null) {
                                                    paramStatus = "UNCONFIRMED"
                                                } else if (reqJson != appJson) {
                                                    paramStatus = "PARTIALLY_APPLIED"
                                                }
                                            }"""

content = content.replace(old_status_logic, new_status_logic)

# 3. Derive overall_status correctly:
# all APPLIED -> APPLIED
# mix of APPLIED/PARTIAL/etc -> PARTIALLY_APPLIED
# all unverifiable -> UNCONFIRMED
# all rejected -> REJECTED
# execution failure -> FAILED

old_overall = """                                    var overallStatus = "APPLIED"
                                    if (parameterResults.values.any { it.status == "FAILED" || it.status == "REJECTED" || it.status == "REJECTED_PHYSICAL_CAMERA" }) {
                                        overallStatus = "PARTIALLY_APPLIED" // Or FAILED? "Derive overall_status from the per-parameter results."
                                    }
                                    if (parameterResults.isNotEmpty() && parameterResults.values.all { it.status == "REJECTED_PHYSICAL_CAMERA" }) {
                                        overallStatus = "REJECTED"
                                    }"""
new_overall = """                                    var overallStatus = "APPLIED"
                                    if (parameterResults.isNotEmpty()) {
                                        val allApplied = parameterResults.values.all { it.status == "APPLIED" }
                                        val allUnconfirmed = parameterResults.values.all { it.status == "UNCONFIRMED" }
                                        val allRejected = parameterResults.values.all { it.status == "REJECTED" }
                                        
                                        if (allApplied) {
                                            overallStatus = "APPLIED"
                                        } else if (allUnconfirmed) {
                                            overallStatus = "UNCONFIRMED"
                                        } else if (allRejected) {
                                            overallStatus = "REJECTED"
                                        } else {
                                            overallStatus = "PARTIALLY_APPLIED"
                                        }
                                    }"""
content = content.replace(old_overall, new_overall)

# Update currentControlParams safely.
# Where is currentControlParams set right now? 
# Ah, SET_PARAMETERS in processControlCommand does NOT set currentControlParams! It passes `newParams` to `updateRepeatingRequest`.
# But wait, does updateRepeatingRequest save `newParams` to `currentControlParams`?

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

