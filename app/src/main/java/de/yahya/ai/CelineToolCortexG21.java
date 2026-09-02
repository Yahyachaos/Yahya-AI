package de.yahya.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * G2.1 typed, allowlisted Tool Cortex.
 *
 * The cortex is pure Java. Android/device access is injected through Backend so planning,
 * validation and failure semantics remain deterministic and independently testable.
 */
public final class CelineToolCortexG21 implements CelineToolRegistry {
    public enum ArgumentSlot { TARGET, PAYLOAD }
    public enum ParameterType { STRING, NUMBER }
    public enum Precondition { ACCESSIBILITY_ACTIVE, NOTIFICATION_LISTENER_ACTIVE }
    public enum ExecutionStatus {
        SUCCESS,
        TOOL_NOT_ALLOWLISTED,
        INVALID_ARGUMENT,
        PRECONDITION_FAILED,
        NOT_FOUND,
        ACTION_REJECTED,
        BACKEND_ERROR
    }

    public static final class ParameterSpec {
        public final String name;
        public final ArgumentSlot slot;
        public final ParameterType type;
        public final boolean required;
        public final int maxLength;
        public final String description;

        ParameterSpec(String name, ArgumentSlot slot, ParameterType type,
                      boolean required, int maxLength, String description) {
            this.name = clean(name);
            this.slot = slot;
            this.type = type;
            this.required = required;
            this.maxLength = Math.max(1, maxLength);
            this.description = clean(description);
        }
    }

    public static final class TypedToolDescriptor {
        public final String id;
        public final String description;
        public final CelineRiskClass riskClass;
        public final List<ParameterSpec> parameters;
        public final List<Precondition> preconditions;

        TypedToolDescriptor(String id, String description, CelineRiskClass riskClass,
                            List<ParameterSpec> parameters, List<Precondition> preconditions) {
            this.id = clean(id);
            this.description = clean(description);
            this.riskClass = riskClass;
            this.parameters = immutable(parameters);
            this.preconditions = immutable(preconditions);
        }

        CelineToolDescriptor brainDescriptor() {
            return new CelineToolDescriptor(id, description, riskClass);
        }
    }

    public static final class ExecutionResult {
        public final String toolId;
        public final ExecutionStatus status;
        public final String observedResult;
        public final String errorCode;

        ExecutionResult(String toolId, ExecutionStatus status, String observedResult, String errorCode) {
            this.toolId = clean(toolId);
            this.status = status;
            this.observedResult = observedResult == null ? "" : observedResult;
            this.errorCode = clean(errorCode);
        }

        public boolean success() { return status == ExecutionStatus.SUCCESS; }
    }

    /** Concrete device bridge. No reasoning/provider behavior belongs here. */
    public interface Backend {
        String deviceStatus();
        boolean accessibilityActive();
        boolean notificationListenerActive();
        List<String> recentNotifications();
        String screenSummary();
        boolean goHome();
        boolean goBack();
        boolean openRecents();
        boolean openApp(String query);
        boolean clickText(String text);
        boolean setText(String text);
        boolean tap(float x, float y);
        void openAccessibilitySettings();
        void openNotificationSettings();
        void openAllFilesSettings();
    }

    private final Backend backend;
    private final LinkedHashMap<String, TypedToolDescriptor> allowlist = new LinkedHashMap<>();

    public CelineToolCortexG21(Backend backend) {
        if (backend == null) throw new IllegalArgumentException("backend must not be null");
        this.backend = backend;
        register(descriptor("device.status", "Read current local device resource/access status.",
                CelineRiskClass.L0_READ_ONLY));
        register(descriptor("notifications.recent", "Read recent active notifications.",
                CelineRiskClass.L0_READ_ONLY,
                preconditions(Precondition.NOTIFICATION_LISTENER_ACTIVE)));
        register(descriptor("screen.read", "Read accessible text from the current screen.",
                CelineRiskClass.L0_READ_ONLY,
                preconditions(Precondition.ACCESSIBILITY_ACTIVE)));
        register(descriptor("navigation.home", "Navigate Android to Home.",
                CelineRiskClass.L1_REVERSIBLE_LOCAL,
                preconditions(Precondition.ACCESSIBILITY_ACTIVE)));
        register(descriptor("navigation.back", "Navigate one Android Back step.",
                CelineRiskClass.L1_REVERSIBLE_LOCAL,
                preconditions(Precondition.ACCESSIBILITY_ACTIVE)));
        register(descriptor("navigation.recents", "Open Android recent apps.",
                CelineRiskClass.L1_REVERSIBLE_LOCAL,
                preconditions(Precondition.ACCESSIBILITY_ACTIVE)));
        register(descriptor("app.open", "Open one installed app by visible app name.",
                CelineRiskClass.L1_REVERSIBLE_LOCAL,
                params(parameter("app_name", ArgumentSlot.TARGET, ParameterType.STRING, true, 120,
                        "Visible installed app name."))));
        register(descriptor("ui.click_text", "Click one accessible visible UI target by text.",
                CelineRiskClass.L2_EXTERNAL_STATE_CHANGE,
                params(parameter("visible_text", ArgumentSlot.TARGET, ParameterType.STRING, true, 180,
                        "Visible text/content description to click.")),
                preconditions(Precondition.ACCESSIBILITY_ACTIVE)));
        register(descriptor("ui.set_text", "Set text in the first accessible editable field.",
                CelineRiskClass.L1_REVERSIBLE_LOCAL,
                params(parameter("text", ArgumentSlot.PAYLOAD, ParameterType.STRING, true, 2000,
                        "Text to place into the editable field.")),
                preconditions(Precondition.ACCESSIBILITY_ACTIVE)));
        register(descriptor("ui.tap", "Tap one explicit screen coordinate.",
                CelineRiskClass.L2_EXTERNAL_STATE_CHANGE,
                params(
                        parameter("x", ArgumentSlot.TARGET, ParameterType.NUMBER, true, 32, "X coordinate."),
                        parameter("y", ArgumentSlot.PAYLOAD, ParameterType.NUMBER, true, 32, "Y coordinate.")),
                preconditions(Precondition.ACCESSIBILITY_ACTIVE)));
        register(descriptor("settings.accessibility", "Open Android accessibility settings.",
                CelineRiskClass.L1_REVERSIBLE_LOCAL));
        register(descriptor("settings.notifications", "Open Android notification-listener settings.",
                CelineRiskClass.L1_REVERSIBLE_LOCAL));
        register(descriptor("settings.all_files", "Open this app's all-files access settings.",
                CelineRiskClass.L1_REVERSIBLE_LOCAL));
    }

    @Override
    public List<CelineToolDescriptor> availableTools() {
        List<CelineToolDescriptor> out = new ArrayList<>();
        for (TypedToolDescriptor value : allowlist.values()) out.add(value.brainDescriptor());
        return Collections.unmodifiableList(out);
    }

    public List<TypedToolDescriptor> typedTools() {
        return Collections.unmodifiableList(new ArrayList<>(allowlist.values()));
    }

    public TypedToolDescriptor descriptor(String id) {
        return allowlist.get(clean(id));
    }

    @Override
    public CelineToolResult execute(CelineToolIntent intent) {
        ExecutionResult result = executeTyped(intent);
        return new CelineToolResult(result.success(), result.observedResult, result.errorCode);
    }

    public ExecutionResult executeTyped(CelineToolIntent intent) {
        if (intent == null) return failure("", ExecutionStatus.INVALID_ARGUMENT, "MISSING_INTENT", "");
        String toolId = clean(intent.toolId);
        TypedToolDescriptor descriptor = allowlist.get(toolId);
        if (descriptor == null) {
            return failure(toolId, ExecutionStatus.TOOL_NOT_ALLOWLISTED, "TOOL_NOT_ALLOWLISTED", "");
        }

        ExecutionResult argumentFailure = validateArguments(descriptor, intent);
        if (argumentFailure != null) return argumentFailure;
        ExecutionResult preconditionFailure = validatePreconditions(descriptor);
        if (preconditionFailure != null) return preconditionFailure;

        try {
            switch (toolId) {
                case "device.status": {
                    String status = clean(backend.deviceStatus());
                    return status.isEmpty()
                            ? failure(toolId, ExecutionStatus.BACKEND_ERROR, "EMPTY_DEVICE_STATUS", "")
                            : success(toolId, status);
                }
                case "notifications.recent": {
                    List<String> recent = backend.recentNotifications();
                    if (recent == null || recent.isEmpty()) return success(toolId, "Keine aktiven Benachrichtigungen.");
                    StringBuilder out = new StringBuilder();
                    int count = 0;
                    for (String row : recent) {
                        String cleanRow = clean(row);
                        if (cleanRow.isEmpty()) continue;
                        if (out.length() > 0) out.append('\n');
                        out.append(cleanRow);
                        if (++count >= 15) break;
                    }
                    return success(toolId, out.length() == 0 ? "Keine aktiven Benachrichtigungen." : out.toString());
                }
                case "screen.read": {
                    String screen = clean(backend.screenSummary());
                    return screen.isEmpty()
                            ? failure(toolId, ExecutionStatus.ACTION_REJECTED, "SCREEN_NOT_AVAILABLE", "")
                            : success(toolId, screen);
                }
                case "navigation.home":
                    return booleanAction(toolId, backend.goHome(), "HOME_ACTION_REJECTED", "HOME");
                case "navigation.back":
                    return booleanAction(toolId, backend.goBack(), "BACK_ACTION_REJECTED", "BACK");
                case "navigation.recents":
                    return booleanAction(toolId, backend.openRecents(), "RECENTS_ACTION_REJECTED", "RECENTS");
                case "app.open": {
                    String app = clean(intent.target);
                    return backend.openApp(app)
                            ? success(toolId, "APP_OPENED:" + app)
                            : failure(toolId, ExecutionStatus.NOT_FOUND, "APP_NOT_FOUND", app);
                }
                case "ui.click_text": {
                    String text = clean(intent.target);
                    return backend.clickText(text)
                            ? success(toolId, "CLICKED_TEXT:" + text)
                            : failure(toolId, ExecutionStatus.ACTION_REJECTED, "TARGET_NOT_FOUND_OR_NOT_CLICKABLE", text);
                }
                case "ui.set_text": {
                    String text = intent.payload == null ? "" : intent.payload;
                    return backend.setText(text)
                            ? success(toolId, "TEXT_SET")
                            : failure(toolId, ExecutionStatus.ACTION_REJECTED, "NO_EDITABLE_FIELD_OR_SET_REJECTED", "");
                }
                case "ui.tap": {
                    float x = Float.parseFloat(clean(intent.target));
                    float y = Float.parseFloat(clean(intent.payload));
                    if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0f || y < 0f) {
                        return failure(toolId, ExecutionStatus.INVALID_ARGUMENT, "INVALID_COORDINATES", "");
                    }
                    return backend.tap(x, y)
                            ? success(toolId, String.format(Locale.US, "TAPPED:%.1f,%.1f", x, y))
                            : failure(toolId, ExecutionStatus.ACTION_REJECTED, "TAP_REJECTED", "");
                }
                case "settings.accessibility":
                    backend.openAccessibilitySettings();
                    return success(toolId, "ACCESSIBILITY_SETTINGS_OPENED");
                case "settings.notifications":
                    backend.openNotificationSettings();
                    return success(toolId, "NOTIFICATION_SETTINGS_OPENED");
                case "settings.all_files":
                    backend.openAllFilesSettings();
                    return success(toolId, "ALL_FILES_SETTINGS_OPENED");
                default:
                    return failure(toolId, ExecutionStatus.TOOL_NOT_ALLOWLISTED, "TOOL_NOT_ALLOWLISTED", "");
            }
        } catch (NumberFormatException e) {
            return failure(toolId, ExecutionStatus.INVALID_ARGUMENT, "INVALID_NUMBER", "");
        } catch (RuntimeException e) {
            return failure(toolId, ExecutionStatus.BACKEND_ERROR, "BACKEND_EXCEPTION", clean(e.getClass().getSimpleName()));
        }
    }

    private ExecutionResult validateArguments(TypedToolDescriptor descriptor, CelineToolIntent intent) {
        for (ParameterSpec spec : descriptor.parameters) {
            String value = spec.slot == ArgumentSlot.TARGET ? intent.target : intent.payload;
            value = value == null ? "" : value.trim();
            if (spec.required && value.isEmpty()) {
                return failure(descriptor.id, ExecutionStatus.INVALID_ARGUMENT,
                        "MISSING_" + spec.name.toUpperCase(Locale.US), "");
            }
            if (value.length() > spec.maxLength) {
                return failure(descriptor.id, ExecutionStatus.INVALID_ARGUMENT,
                        "ARGUMENT_TOO_LONG_" + spec.name.toUpperCase(Locale.US), "");
            }
            if (!value.isEmpty() && spec.type == ParameterType.NUMBER) {
                try {
                    double number = Double.parseDouble(value);
                    if (!Double.isFinite(number)) throw new NumberFormatException("non-finite");
                } catch (NumberFormatException e) {
                    return failure(descriptor.id, ExecutionStatus.INVALID_ARGUMENT,
                            "INVALID_" + spec.name.toUpperCase(Locale.US), "");
                }
            }
        }
        return null;
    }

    private ExecutionResult validatePreconditions(TypedToolDescriptor descriptor) {
        for (Precondition precondition : descriptor.preconditions) {
            if (precondition == Precondition.ACCESSIBILITY_ACTIVE && !backend.accessibilityActive()) {
                return failure(descriptor.id, ExecutionStatus.PRECONDITION_FAILED,
                        "ACCESSIBILITY_INACTIVE", "");
            }
            if (precondition == Precondition.NOTIFICATION_LISTENER_ACTIVE && !backend.notificationListenerActive()) {
                return failure(descriptor.id, ExecutionStatus.PRECONDITION_FAILED,
                        "NOTIFICATION_LISTENER_INACTIVE", "");
            }
        }
        return null;
    }

    private ExecutionResult booleanAction(String toolId, boolean ok, String errorCode, String observed) {
        return ok ? success(toolId, observed)
                : failure(toolId, ExecutionStatus.ACTION_REJECTED, errorCode, "");
    }

    private void register(TypedToolDescriptor descriptor) {
        if (descriptor.id.isEmpty()) throw new IllegalArgumentException("tool id must not be empty");
        if (allowlist.containsKey(descriptor.id)) throw new IllegalArgumentException("duplicate tool id: " + descriptor.id);
        allowlist.put(descriptor.id, descriptor);
    }

    private static TypedToolDescriptor descriptor(String id, String description, CelineRiskClass riskClass) {
        return descriptor(id, description, riskClass, Collections.<ParameterSpec>emptyList(),
                Collections.<Precondition>emptyList());
    }

    private static TypedToolDescriptor descriptor(String id, String description, CelineRiskClass riskClass,
                                                  List<Precondition> preconditions) {
        return descriptor(id, description, riskClass, Collections.<ParameterSpec>emptyList(), preconditions);
    }

    private static TypedToolDescriptor descriptor(String id, String description, CelineRiskClass riskClass,
                                                  List<ParameterSpec> parameters, List<Precondition> preconditions) {
        return new TypedToolDescriptor(id, description, riskClass, parameters, preconditions);
    }

    private static ParameterSpec parameter(String name, ArgumentSlot slot, ParameterType type,
                                           boolean required, int maxLength, String description) {
        return new ParameterSpec(name, slot, type, required, maxLength, description);
    }

    private static List<ParameterSpec> params(ParameterSpec... specs) {
        List<ParameterSpec> out = new ArrayList<>();
        if (specs != null) Collections.addAll(out, specs);
        return out;
    }

    private static List<Precondition> preconditions(Precondition... values) {
        List<Precondition> out = new ArrayList<>();
        if (values != null) Collections.addAll(out, values);
        return out;
    }

    private static ExecutionResult success(String toolId, String observed) {
        return new ExecutionResult(toolId, ExecutionStatus.SUCCESS, observed, "");
    }

    private static ExecutionResult failure(String toolId, ExecutionStatus status, String errorCode, String observed) {
        return new ExecutionResult(toolId, status, observed, errorCode);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static <T> List<T> immutable(List<T> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
